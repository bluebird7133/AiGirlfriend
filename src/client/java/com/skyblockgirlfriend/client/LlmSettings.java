package com.skyblockgirlfriend.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class LlmSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("skyblock-girlfriend");
	private final Path settingsFile = FabricLoader.getInstance().getConfigDir()
			.resolve("skyblock-girlfriend")
			.resolve("llm.properties");
	private final Map<Provider, String> endpoints = new EnumMap<>(Provider.class);
	private final Map<Provider, String> models = new EnumMap<>(Provider.class);
	private final Map<Provider, String> apiKeys = new EnumMap<>(Provider.class);
	private Provider provider = Provider.GROQ;
	private boolean enabled;

	public LlmSettings() {
		for (Provider service : Provider.values()) {
			endpoints.put(service, service.defaultEndpoint());
			models.put(service, service.defaultModel());
			apiKeys.put(service, "");
		}
		try {
			load();
		} catch (IOException exception) {
			LOGGER.error("Could not load LLM settings from {}", settingsFile, exception);
		}
	}

	public Provider provider() {
		return provider;
	}

	public void setProvider(Provider provider) {
		this.provider = provider == null ? Provider.GROQ : provider;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String endpoint() {
		return endpoints.get(provider);
	}

	public String endpoint(Provider service) {
		return endpoints.get(service);
	}

	public void setEndpoint(String endpoint) {
		setEndpoint(provider, endpoint);
	}

	public void setEndpoint(Provider service, String endpoint) {
		if (service != null && endpoint != null) {
			endpoints.put(service, endpoint);
		}
	}

	public String model() {
		return models.get(provider);
	}

	public String model(Provider service) {
		return models.get(service);
	}

	public void setModel(String model) {
		setModel(provider, model);
	}

	public void setModel(Provider service, String model) {
		if (service != null && model != null) {
			models.put(service, model);
		}
	}

	public String apiKey() {
		return apiKey(provider);
	}

	public String apiKey(Provider service) {
		return apiKeys.get(service);
	}

	public void setApiKey(String apiKey) {
		setApiKey(provider, apiKey);
	}

	public void setApiKey(Provider service, String apiKey) {
		if (service != null && service.requiresApiKey() && apiKey != null) {
			apiKeys.put(service, apiKey.trim());
		}
	}

	public boolean isConfigured() {
		return !endpoint().isBlank()
				&& !model().isBlank()
				&& (!provider.requiresApiKey() || !apiKey().isBlank());
	}

	public boolean save() {
		Properties properties = new Properties();
		properties.setProperty("enabled", Boolean.toString(enabled));
		properties.setProperty("provider", provider.name().toLowerCase(Locale.ROOT));
		for (Provider service : Provider.values()) {
			String prefix = service.name().toLowerCase(Locale.ROOT);
			properties.setProperty(prefix + "_endpoint", endpoints.get(service));
			properties.setProperty(prefix + "_model", models.get(service));
			if (service.requiresApiKey()) {
				properties.setProperty(prefix + "_api_key", apiKeys.get(service));
			}
		}

		try {
			Files.createDirectories(settingsFile.getParent());
			try (OutputStream output = Files.newOutputStream(settingsFile)) {
				properties.store(output, "SkyBlock Girlfriend LLM settings");
			}
			return true;
		} catch (IOException exception) {
			LOGGER.error("Could not save LLM settings to {}", settingsFile, exception);
			return false;
		}
	}

	private void load() throws IOException {
		if (!Files.exists(settingsFile)) {
			return;
		}

		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(settingsFile)) {
			properties.load(input);
		}
		String savedProvider = properties.getProperty("provider");
		if (savedProvider != null) {
			try {
				provider = Provider.valueOf(savedProvider.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException exception) {
				LOGGER.warn("Ignoring invalid LLM provider setting");
			}
		}
		String savedEnabled = properties.getProperty("enabled");
		if ("true".equalsIgnoreCase(savedEnabled) || "false".equalsIgnoreCase(savedEnabled)) {
			enabled = Boolean.parseBoolean(savedEnabled);
		} else if (savedEnabled != null) {
			LOGGER.warn("Ignoring invalid LLM enabled setting");
		}

		for (Provider service : Provider.values()) {
			String prefix = service.name().toLowerCase(Locale.ROOT);
			endpoints.put(service, properties.getProperty(prefix + "_endpoint", service.defaultEndpoint()));
			models.put(service, properties.getProperty(prefix + "_model", service.defaultModel()));
			if (service.requiresApiKey()) {
				apiKeys.put(service, properties.getProperty(prefix + "_api_key", ""));
			}
		}
	}

	public enum Provider {
		GROQ("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", true),
		OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini", true),
		OLLAMA("Ollama", "http://localhost:11434", "llama3.2:3b", false);

		private final String displayName;
		private final String defaultEndpoint;
		private final String defaultModel;
		private final boolean requiresApiKey;

		Provider(String displayName, String defaultEndpoint, String defaultModel, boolean requiresApiKey) {
			this.displayName = displayName;
			this.defaultEndpoint = defaultEndpoint;
			this.defaultModel = defaultModel;
			this.requiresApiKey = requiresApiKey;
		}

		public String displayName() {
			return displayName;
		}

		public String defaultEndpoint() {
			return defaultEndpoint;
		}

		public String defaultModel() {
			return defaultModel;
		}

		public boolean requiresApiKey() {
			return requiresApiKey;
		}
	}
}
