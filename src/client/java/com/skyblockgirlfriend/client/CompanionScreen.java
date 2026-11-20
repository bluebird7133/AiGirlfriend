package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

public final class CompanionScreen extends Screen {
	private static final float MIN_SCALE = 0.3F;
	private static final float MAX_SCALE = 2.0F;

	private final CompanionSettings settings;
	private final NotificationRenderer notificationRenderer;
	private Button invisibleButton;
	private EditBox companionNameBox;
	private int companionNameX;

	public CompanionScreen(CompanionSettings settings, NotificationRenderer notificationRenderer) {
		super(Component.literal("Companion Settings"));
		this.settings = settings;
		this.notificationRenderer = notificationRenderer;
	}

	@Override
	protected void init() {
		settings.refreshSkins();
		int left = width / 2 - 150;
		int buttonY = 58;
		companionNameX = width / 2 + 34;
		addRenderableWidget(Button.builder(Component.literal("GUI"), button ->
				minecraft.gui.setScreen(new NotificationPositionScreen(settings, notificationRenderer, this)))
				.bounds(width / 2 + 95, 30, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Select skin"), button ->
				minecraft.gui.setScreen(new SkinPickerScreen(settings, this)))
				.bounds(left, buttonY, 300, 20).build());

		PlayerSkinWidget preview = new PlayerSkinWidget(
				48,
				64,
				minecraft.getEntityModels(),
				() -> minecraft.player == null
						? DefaultPlayerSkin.getDefaultSkin()
						: settings.skinForRender(minecraft, minecraft.player.getSkin())
		);
		preview.setX(width / 2 - 24);
		preview.setY(buttonY + 27);
		addRenderableWidget(preview);

		companionNameBox = new EditBox(font, companionNameX, buttonY + 43,
				left + 300 - companionNameX, 20, Component.literal("Companion name"));
		companionNameBox.setMaxLength(32);
		companionNameBox.setValue(settings.companionName());
		companionNameBox.setHint(Component.literal("Companion name"));
		companionNameBox.setResponder(settings::setCompanionName);
		addRenderableWidget(companionNameBox);

		addRenderableWidget(new SizeSlider(left, buttonY + 116, 300, 20));
		invisibleButton = addRenderableWidget(Button.builder(invisibleButtonText(), button -> {
			settings.toggleInvisible();
			invisibleButton.setMessage(invisibleButtonText());
		}).bounds(left, buttonY + 145, 300, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 100, buttonY + 176, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("Appearance"), width / 2, 36, 0xFFFFFF);
		graphics.text(font, "Companion name", companionNameX, 86, 0xFFFFFF);
		graphics.centeredText(font, Component.literal(settings.selectedSkinName()), width / 2, 151, 0xFFFFFF);
		if (!settings.hasSkinsDirectory()) {
			graphics.centeredText(font, Component.literal("Cannot locate skins directory."), width / 2,
					height - 36, 0xFFFF55);
			graphics.centeredText(font, Component.literal("Create a skins folder in your Minecraft instance."),
					width / 2, height - 23, 0xFFFF55);
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
		minecraft.gui.setScreen(null);
	}

	@Override
	public void removed() {
		settings.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component invisibleButtonText() {
		return Component.literal("Invisible: " + (settings.isInvisible() ? "On" : "Off"));
	}

	private final class SizeSlider extends AbstractSliderButton {
		private SizeSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(),
					(settings.scale() - MIN_SCALE) / (MAX_SCALE - MIN_SCALE));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			int percent = Math.round((MIN_SCALE + (float) value * (MAX_SCALE - MIN_SCALE)) * 100.0F);
			setMessage(Component.literal("Size: " + percent + "%"));
		}

		@Override
		protected void applyValue() {
			settings.setScale(MIN_SCALE + (float) value * (MAX_SCALE - MIN_SCALE));
			updateMessage();
		}
	}
}
