package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class CompanionAppearanceScreen extends Screen {
	private static final float MIN_BUST_SIZE = 0.25F;
	private static final float MAX_BUST_SIZE = 1.5F;
	private static final float MIN_SPACING = 0.6F;
	private static final float MAX_SPACING = 1.6F;
	private static final float MIN_OFFSET = -0.18F;
	private static final float MAX_OFFSET = 0.18F;
	private static final float MIN_DEPTH = -0.08F;
	private static final float MAX_DEPTH = 0.15F;
	private static final float MAX_ANGLE = 25.0F;

	private final CompanionSettings settings;
	private final Screen parent;
	private Button appearanceButton;
	private Button physicsButton;
	private Slider bounceSlider;
	private Slider floppinessSlider;
	private Slider[] appearanceSliders;

	public CompanionAppearanceScreen(CompanionSettings settings, Screen parent) {
		super(Component.literal("Super Secret Settings"));
		this.settings = settings;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - 150;
		int right = width / 2 + 2;
		int top = 58;
		appearanceButton = addRenderableWidget(Button.builder(appearanceText(), button -> {
			settings.toggleAppearance();
			refreshControls();
		}).bounds(left, top, 300, 20).build());

		Slider sizeSlider = addSlider(left, top + 28, 148, settings.bustSize(),
				MIN_BUST_SIZE, MAX_BUST_SIZE, "Shape size", settings::setBustSize, ValueFormat.PERCENT);
		Slider spacingSlider = addSlider(right, top + 28, 148, settings.bustSpacing(),
				MIN_SPACING, MAX_SPACING, "Spacing", settings::setBustSpacing, ValueFormat.PERCENT);
		Slider heightSlider = addSlider(left, top + 54, 148, settings.bustHeight(),
				MIN_OFFSET, MAX_OFFSET, "Height", settings::setBustHeight, ValueFormat.PIXELS);
		Slider depthSlider = addSlider(right, top + 54, 148, settings.bustDepth(),
				MIN_DEPTH, MAX_DEPTH, "Depth", settings::setBustDepth, ValueFormat.PIXELS);
		Slider angleSlider = addSlider(left, top + 80, 148, settings.bustAngle(),
				0.0F, MAX_ANGLE, "Angle", settings::setBustAngle, ValueFormat.DEGREES);
		physicsButton = addRenderableWidget(Button.builder(physicsText(), button -> {
			settings.toggleAppearancePhysics();
			refreshControls();
		}).bounds(right, top + 80, 148, 20).build());
		bounceSlider = addSlider(left, top + 106, 148, settings.bustBounce(),
				0.0F, 1.0F, "Bounce", settings::setBustBounce, ValueFormat.PERCENT);
		floppinessSlider = addSlider(right, top + 106, 148, settings.bustFloppiness(),
				0.0F, 1.0F, "Floppiness", settings::setBustFloppiness, ValueFormat.PERCENT);

		appearanceSliders = new Slider[]{sizeSlider, spacingSlider, heightSlider, depthSlider, angleSlider};
		refreshControls();
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 100, height - 34, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 26, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal("Companion-only appearance and movement settings"),
				width / 2, 42, 0xFFAAAAAA);
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
	public void removed() {
		settings.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Slider addSlider(
			int x,
			int y,
			int width,
			float initial,
			float minimum,
			float maximum,
			String label,
			java.util.function.Consumer<Float> setter,
			ValueFormat format
	) {
		return addRenderableWidget(new Slider(x, y, width, initial, minimum, maximum, label, setter, format));
	}

	private void refreshControls() {
		appearanceButton.setMessage(appearanceText());
		physicsButton.setMessage(physicsText());
		boolean appearanceEnabled = settings.isAppearanceEnabled();
		for (Slider slider : appearanceSliders) {
			slider.active = appearanceEnabled;
		}
		physicsButton.active = appearanceEnabled;
		boolean physicsEnabled = appearanceEnabled && settings.isAppearancePhysicsEnabled();
		bounceSlider.active = physicsEnabled;
		floppinessSlider.active = physicsEnabled;
	}

	private Component appearanceText() {
		return Component.literal("Companion appearance: " + (settings.isAppearanceEnabled() ? "On" : "Off"));
	}

	private Component physicsText() {
		return Component.literal("Movement physics: " + (settings.isAppearancePhysicsEnabled() ? "On" : "Off"));
	}

	private enum ValueFormat {
		PERCENT,
		PIXELS,
		DEGREES
	}

	private final class Slider extends AbstractSliderButton {
		private final float minimum;
		private final float maximum;
		private final String label;
		private final java.util.function.Consumer<Float> setter;
		private final ValueFormat format;

		private Slider(
				int x,
				int y,
				int width,
				float initial,
				float minimum,
				float maximum,
				String label,
				java.util.function.Consumer<Float> setter,
				ValueFormat format
		) {
			super(x, y, width, 20, Component.empty(), (initial - minimum) / (maximum - minimum));
			this.minimum = minimum;
			this.maximum = maximum;
			this.label = label;
			this.setter = setter;
			this.format = format;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			float value = minimum + (float) this.value * (maximum - minimum);
			String displayValue = switch (format) {
				case PERCENT -> String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
				case PIXELS -> String.format(Locale.ROOT, "%+.1f px", value * 16.0F);
				case DEGREES -> String.format(Locale.ROOT, "%.0f deg", value);
			};
			setMessage(Component.literal(label + ": " + displayValue));
		}

		@Override
		protected void applyValue() {
			setter.accept(minimum + (float) this.value * (maximum - minimum));
			updateMessage();
		}
	}
}
