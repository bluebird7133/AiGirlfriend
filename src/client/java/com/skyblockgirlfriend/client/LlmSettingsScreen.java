package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.EnumMap;
import java.util.Map;

public final class LlmSettingsScreen extends Screen {
	private final LlmSettings settings;
	private final Screen parent;
	private final Map<LlmSettings.Provider, String> endpointDrafts = new EnumMap<>(LlmSettings.Provider.class);
	private final Map<LlmSettings.Provider, String> modelDrafts = new EnumMap<>(LlmSettings.Provider.class);
	private final Map<LlmSettings.Provider, String> apiKeyDrafts = new EnumMap<>(LlmSettings.Provider.class);
	private LlmSettings.Provider provider;
	private boolean enabled;
	private Button providerButton;
	private Button enabledButton;
	private EditBox endpointBox;
	private EditBox modelBox;
	private EditBox apiKeyBox;
	private int formX;
	private int formWidth;
	private int top;
	private boolean saveFailed;

	public LlmSettingsScreen(LlmSettings settings, Screen parent) {
		super(Component.literal("LLM Setup"));
		this.settings = settings;
		this.parent = parent;
		provider = settings.provider();
		enabled = settings.isEnabled();
		for (LlmSettings.Provider service : LlmSettings.Provider.values()) {
			endpointDrafts.put(service, settings.endpoint(service));
			modelDrafts.put(service, settings.model(service));
			apiKeyDrafts.put(service, settings.apiKey(service));
		}
	}

	@Override
	protected void init() {
		formWidth = Math.max(180, Math.min(360, width - 20));
		formX = (width - formWidth) / 2;
		top = Math.max(58, Math.min(112, height / 2 - 96));
		int providerWidth = Math.max(70, formWidth - 106);
		providerButton = addRenderableWidget(Button.builder(providerText(), button -> cycleProvider())
				.bounds(formX, top, providerWidth, 20).build());
		enabledButton = addRenderableWidget(Button.builder(enabledText(), button -> {
			enabled = !enabled;
			enabledButton.setMessage(enabledText());
		}).bounds(formX + providerWidth + 6, top, formWidth - providerWidth - 6, 20).build());

		endpointBox = addRenderableWidget(new EditBox(font, formX, top + 42, formWidth, 20,
				Component.literal("Service base URL")));
		endpointBox.setMaxLength(512);
		endpointBox.setHint(Component.literal("Service base URL"));

		modelBox = addRenderableWidget(new EditBox(font, formX, top + 84, formWidth, 20,
				Component.literal("Model")));
		modelBox.setMaxLength(128);
		modelBox.setHint(Component.literal("Model"));

		apiKeyBox = addRenderableWidget(new EditBox(font, formX, top + 126, formWidth, 20,
				Component.literal("API key")));
		apiKeyBox.setMaxLength(1024);
		apiKeyBox.setHint(Component.literal("API key"));
		apiKeyBox.addFormatter((value, cursorPosition) ->
				FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
		updateProviderFields();

		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(width / 2 - 105, height - 34, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
				.bounds(width / 2 + 5, height - 34, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("LLM Setup"), width / 2, 26, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal("Choose a service, model, and local API key."),
				width / 2, 42, 0xFFAAAAAA);
		graphics.text(font, "Service base URL", formX, top + 29, 0xFFFFFFFF);
		graphics.text(font, "Model", formX, top + 71, 0xFFFFFFFF);
		graphics.text(font, provider.requiresApiKey() ? "API key (masked)" : "API key",
				formX, top + 113, 0xFFFFFFFF);
		if (!provider.requiresApiKey()) {
			graphics.centeredText(font, Component.literal("Ollama does not require an API key."),
					width / 2, top + 155, 0xFFAAAAAA);
		}
		if (height >= 320) {
			String privacyNote = provider.requiresApiKey()
					? "Key saved locally in plain text; sent to selected service."
					: "Ollama runs locally and does not require an API key.";
			graphics.centeredText(font, Component.literal(privacyNote), width / 2, top + 157, 0xFFAAAAAA);
			graphics.centeredText(font, Component.literal("Install the selected model in local Ollama."),
					width / 2, top + 170, 0xFFAAAAAA);
		}
		if (saveFailed) {
			graphics.centeredText(font, Component.literal("Could not save. Check log and file permissions."),
					width / 2, height - 49, 0xFFFF5555);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void cycleProvider() {
		saveCurrentDraft();
		endpointBox.setFocused(false);
		modelBox.setFocused(false);
		apiKeyBox.setFocused(false);
		LlmSettings.Provider[] providers = LlmSettings.Provider.values();
		provider = providers[(provider.ordinal() + 1) % providers.length];
		updateProviderFields();
	}

	private void saveCurrentDraft() {
		if (endpointBox != null) {
			endpointDrafts.put(provider, endpointBox.getValue());
			modelDrafts.put(provider, modelBox.getValue());
			apiKeyDrafts.put(provider, apiKeyBox.getValue());
		}
	}

	private void updateProviderFields() {
		providerButton.setMessage(providerText());
		endpointBox.setValue(endpointDrafts.get(provider));
		modelBox.setValue(modelDrafts.get(provider));
		apiKeyBox.setValue(apiKeyDrafts.get(provider));
		apiKeyBox.setVisible(provider.requiresApiKey());
	}

	private void saveAndClose() {
		saveCurrentDraft();
		settings.setProvider(provider);
		settings.setEnabled(enabled);
		for (LlmSettings.Provider service : LlmSettings.Provider.values()) {
			settings.setEndpoint(service, endpointDrafts.get(service));
			settings.setModel(service, modelDrafts.get(service));
			settings.setApiKey(service, apiKeyDrafts.get(service));
		}
		saveFailed = !settings.save();
		if (!saveFailed) {
			onClose();
		}
	}

	private Component providerText() {
		return Component.literal("Service: " + provider.displayName());
	}

	private Component enabledText() {
		return Component.literal("AI: " + (enabled ? "Enabled" : "Disabled"));
	}
}
