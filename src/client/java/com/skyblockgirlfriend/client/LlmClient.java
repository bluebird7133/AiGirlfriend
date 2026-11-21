package com.skyblockgirlfriend.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class LlmClient {
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	private static final int MAX_REPLY_TOKENS = 1024;

	public CompletableFuture<String> reply(
			LlmSettings settings,
			List<CompanionMessaging.Message> history,
			String companionName
	) {
		try {
			LlmSettings.Provider provider = settings.provider();
			URI endpoint = endpoint(provider, settings.endpoint());
			JsonObject payload = provider == LlmSettings.Provider.OLLAMA
					? ollamaPayload(settings.model(), history, companionName)
					: openAiPayload(provider, settings.model(), history, companionName);
			HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
					.timeout(Duration.ofSeconds(90))
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
			if (provider.requiresApiKey()) {
				request.header("Authorization", "Bearer " + settings.apiKey());
			}

			return HTTP_CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
					.thenApply(response -> parseResponse(provider, response));
		} catch (IllegalArgumentException exception) {
			return CompletableFuture.failedFuture(exception);
		}
	}

	private static URI endpoint(LlmSettings.Provider provider, String endpoint) {
		String base = endpoint.trim().replaceAll("/+$", "");
		URI baseUri = URI.create(base);
		String scheme = baseUri.getScheme();
		String host = baseUri.getHost();
		if (host == null || baseUri.getUserInfo() != null || baseUri.getQuery() != null
				|| baseUri.getFragment() != null) {
			throw new IllegalArgumentException("The LLM service URL is invalid.");
		}

		boolean https = "https".equalsIgnoreCase(scheme);
		boolean localOllama = provider == LlmSettings.Provider.OLLAMA
				&& "http".equalsIgnoreCase(scheme)
				&& ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
		if (!https && !localOllama) {
			throw new IllegalArgumentException("Use HTTPS for hosted services, or localhost for Ollama.");
		}

		String route = provider == LlmSettings.Provider.OLLAMA ? "/api/chat" : "/chat/completions";
		if (!base.endsWith(route)) {
			base += route;
		}
		return URI.create(base);
	}

	private static JsonObject openAiPayload(
			LlmSettings.Provider provider,
			String model,
			List<CompanionMessaging.Message> history,
			String companionName
	) {
		JsonObject payload = new JsonObject();
		payload.addProperty("model", model);
		payload.add("messages", messages(history, companionName));
		payload.addProperty("stream", false);
		payload.addProperty("max_completion_tokens", MAX_REPLY_TOKENS);
		if (provider == LlmSettings.Provider.GROQ
				&& model.toLowerCase(Locale.ROOT).startsWith("openai/gpt-oss-")) {
			payload.addProperty("reasoning_effort", "low");
			payload.addProperty("include_reasoning", false);
		}
		return payload;
	}

	private static JsonObject ollamaPayload(
			String model,
			List<CompanionMessaging.Message> history,
			String companionName
	) {
		JsonObject payload = new JsonObject();
		payload.addProperty("model", model);
		payload.add("messages", messages(history, companionName));
		payload.addProperty("stream", false);
		JsonObject options = new JsonObject();
		options.addProperty("num_predict", MAX_REPLY_TOKENS);
		payload.add("options", options);
		return payload;
	}

	private static JsonArray messages(List<CompanionMessaging.Message> history, String companionName) {
		JsonArray messages = new JsonArray();
		addMessage(messages, "system", "You are " + companionName
				+ ", a friendly Minecraft companion. Reply naturally and keep responses concise.");
		for (CompanionMessaging.Message message : history) {
			String role = message.sender() == CompanionMessaging.Sender.PLAYER ? "user" : "assistant";
			addMessage(messages, role, message.text());
		}
		return messages;
	}

	private static void addMessage(JsonArray messages, String role, String content) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		messages.add(message);
	}

	private static String parseResponse(
			LlmSettings.Provider provider,
			HttpResponse<String> response
	) {
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new LlmRequestException(httpError(provider, response.statusCode(), response.body()));
		}

		try {
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (provider == LlmSettings.Provider.OLLAMA) {
				String content = stringField(root.getAsJsonObject("message"), "content");
				if (content.isBlank()) {
					throw new LlmRequestException("Ollama returned an empty reply. Check that the selected model is available.");
				}
				return content.trim();
			}

			JsonArray choices = root.getAsJsonArray("choices");
			if (choices == null || choices.isEmpty()) {
				throw new LlmRequestException(provider.displayName() + " returned no response choices.");
			}
			JsonObject choice = choices.get(0).getAsJsonObject();
			JsonObject message = choice.getAsJsonObject("message");
			String content = stringField(message, "content");
			if (content.isBlank()) {
				String refusal = stringField(message, "refusal");
				if (!refusal.isBlank()) {
					return refusal.trim();
				}
				String finishReason = stringField(choice, "finish_reason");
				String usage = usageSummary(root);
				if ("length".equals(finishReason)) {
					throw new LlmRequestException(provider.displayName()
							+ " used its completion-token budget before producing a visible reply."
							+ usage + " Try a shorter prompt or a less reasoning-intensive model.");
				}
				if ("content_filter".equals(finishReason)) {
					throw new LlmRequestException(provider.displayName()
							+ " filtered the response and returned no visible reply." + usage);
				}
				throw new LlmRequestException(provider.displayName()
						+ " returned HTTP 200 but no visible reply (finish_reason="
						+ (finishReason.isBlank() ? "not provided" : finishReason) + ")." + usage
						+ " Retry, or try a different model.");
			}
			if ("length".equals(stringField(choice, "finish_reason"))) {
				return content.trim() + " [The provider stopped at its completion-token limit; this reply may be incomplete.]";
			}
			return content.trim();
		} catch (JsonParseException | IllegalStateException | IndexOutOfBoundsException
				| NullPointerException exception) {
			throw new LlmRequestException(provider.displayName()
					+ " returned an unreadable success response. Check the configured model and try again.", exception);
		}
	}

	public static String failureMessage(Throwable failure) {
		Throwable cause = failure;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException)
				&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof LlmRequestException) {
			return cause.getMessage();
		}
		if (cause instanceof HttpTimeoutException) {
			return "The LLM request timed out. Check your connection and try again.";
		}
		if (cause instanceof UnknownHostException) {
			return "Could not find the LLM service host. Check the endpoint and your connection.";
		}
		if (cause instanceof ConnectException) {
			return "Could not connect to the LLM service. Check the endpoint and internet connection, or make sure Ollama is running.";
		}
		if (cause instanceof SSLException) {
			return "The secure connection to the LLM service failed. Check the endpoint and system clock.";
		}
		if (cause instanceof IOException) {
			return "The LLM network request failed: " + safeDetail(cause.getMessage());
		}
		return cause.getMessage() == null || cause.getMessage().isBlank()
				? "The LLM request failed unexpectedly. Check the game log."
				: safeDetail(cause.getMessage());
	}

	private static String httpError(LlmSettings.Provider provider, int status, String body) {
		String hint = switch (status) {
			case 400 -> "The request or model parameters were rejected.";
			case 401 -> "Authentication failed. Check the API key saved in LLM Setup.";
			case 403 -> "The account or API key does not have permission for this request.";
			case 404 -> "The endpoint or selected model was not found.";
			case 413 -> "The request is too large. Shorten the conversation.";
			case 422 -> "The service could not process this request.";
			case 429 -> "The rate limit or account quota was reached. Wait and retry.";
			case 498 -> "Groq's Flex capacity is full. Retry later or change service tier.";
			case 499 -> "The request was cancelled by the service.";
			case 500, 502, 503, 504 -> "The service is temporarily unavailable. Retry later.";
			default -> "The service rejected the request.";
		};
		String details = providerErrorDetails(body);
		return provider.displayName() + " request failed (HTTP " + status + "): " + hint
				+ (details.isBlank() ? "" : " Provider detail: " + details);
	}

	private static String providerErrorDetails(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			JsonElement error = root.get("error");
			if (error == null || error.isJsonNull()) {
				return "";
			}
			if (error.isJsonPrimitive()) {
				return safeDetail(error.getAsString());
			}
			if (!error.isJsonObject()) {
				return "";
			}
			JsonObject errorObject = error.getAsJsonObject();
			String message = stringField(errorObject, "message");
			String code = stringField(errorObject, "code");
			String type = stringField(errorObject, "type");
			StringBuilder details = new StringBuilder();
			if (!message.isBlank()) {
				details.append(message);
			}
			if (!code.isBlank()) {
				details.append(details.isEmpty() ? "" : " ");
				details.append("(code: ").append(code).append(')');
			}
			if (!type.isBlank()) {
				details.append(details.isEmpty() ? "" : " ");
				details.append("(type: ").append(type).append(')');
			}
			return safeDetail(details.toString());
		} catch (JsonParseException | IllegalStateException exception) {
			return "";
		}
	}

	private static String usageSummary(JsonObject root) {
		JsonObject usage = root.getAsJsonObject("usage");
		if (usage == null) {
			return "";
		}
		String promptTokens = stringField(usage, "prompt_tokens");
		String completionTokens = stringField(usage, "completion_tokens");
		if (promptTokens.isBlank() && completionTokens.isBlank()) {
			return "";
		}
		return " Usage: " + (promptTokens.isBlank() ? "?" : promptTokens) + " prompt, "
				+ (completionTokens.isBlank() ? "?" : completionTokens) + " completion tokens.";
	}

	private static String stringField(JsonObject object, String field) {
		if (object == null) {
			return "";
		}
		JsonElement value = object.get(field);
		return value == null || value.isJsonNull() || !value.isJsonPrimitive()
				? ""
				: value.getAsString();
	}

	private static String safeDetail(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String cleaned = value.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
		cleaned = cleaned.replaceAll("(?i)bearer\\s+\\S+", "Bearer [redacted]")
				.replaceAll("(?i)(?:gsk_|sk-)[a-z0-9_-]{12,}", "[redacted]");
		return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 237) + "...";
	}

	private static final class LlmRequestException extends RuntimeException {
		private LlmRequestException(String message) {
			super(message);
		}

		private LlmRequestException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
