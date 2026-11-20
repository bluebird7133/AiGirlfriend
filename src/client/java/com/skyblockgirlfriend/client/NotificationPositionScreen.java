package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class NotificationPositionScreen extends Screen {
	private static final int RESIZE_HANDLE_SIZE = 10;

	private final CompanionSettings settings;
	private final NotificationRenderer renderer;
	private final Screen parent;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean dragging;
	private ResizeCorner resizeCorner;
	private NotificationRenderer.Bounds resizeStartBounds;
	private int resizeStartMouseX;
	private int resizeStartMouseY;

	public NotificationPositionScreen(
			CompanionSettings settings,
			NotificationRenderer renderer,
			Screen parent
	) {
		super(Component.literal("Notification Position"));
		this.settings = settings;
		this.renderer = renderer;
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableOnly((graphics, mouseX, mouseY, delta) ->
				renderer.renderPreview(graphics, minecraft));
		addRenderableWidget(Button.builder(Component.literal("Reset Position"), button -> {
			settings.setNotificationPosition(0.02F, 0.08F);
			settings.save();
		}).bounds(width / 2 - 105, height - 42, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 + 5, height - 42, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("Notification Position"), width / 2, 28, 0xFFFFFF);
		graphics.centeredText(font, Component.literal("Drag the preview to choose where messages appear."),
				width / 2, 44, 0xAAAAAA);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}

		NotificationRenderer.Bounds bounds = renderer.previewBounds(width, height, font);
		resizeCorner = findResizeCorner((int) event.x(), (int) event.y(), bounds);
		if (resizeCorner != null) {
			resizeStartBounds = bounds;
			resizeStartMouseX = (int) event.x();
			resizeStartMouseY = (int) event.y();
			return true;
		}
		if (event.x() < bounds.x() || event.x() > bounds.x() + bounds.width()
				|| event.y() < bounds.y() || event.y() > bounds.y() + bounds.height()) {
			return false;
		}
		dragOffsetX = (int) event.x() - bounds.x();
		dragOffsetY = (int) event.y() - bounds.y();
		dragging = true;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (resizeCorner != null && event.button() == 0) {
			renderer.resizeFromCorner(resizeStartBounds, resizeCorner.left(), resizeCorner.top(),
					(int) event.x() - resizeStartMouseX, (int) event.y() - resizeStartMouseY,
					width, height, font);
			return true;
		}
		if (dragging && event.button() == 0) {
			renderer.setPositionFromTopLeft((int) event.x() - dragOffsetX,
					(int) event.y() - dragOffsetY, width, height, font);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && (dragging || resizeCorner != null)) {
			dragging = false;
			resizeCorner = null;
			resizeStartBounds = null;
			settings.save();
			return true;
		}
		return super.mouseReleased(event);
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

	private static ResizeCorner findResizeCorner(int mouseX, int mouseY, NotificationRenderer.Bounds bounds) {
		for (ResizeCorner corner : ResizeCorner.values()) {
			int left = corner.left() ? bounds.x() : bounds.x() + bounds.width() - RESIZE_HANDLE_SIZE;
			int top = corner.top() ? bounds.y() : bounds.y() + bounds.height() - RESIZE_HANDLE_SIZE;
			if (mouseX >= left && mouseX <= left + RESIZE_HANDLE_SIZE
					&& mouseY >= top && mouseY <= top + RESIZE_HANDLE_SIZE) {
				return corner;
			}
		}
		return null;
	}

	private enum ResizeCorner {
		TOP_LEFT(true, true),
		TOP_RIGHT(false, true),
		BOTTOM_LEFT(true, false),
		BOTTOM_RIGHT(false, false);

		private final boolean left;
		private final boolean top;

		ResizeCorner(boolean left, boolean top) {
			this.left = left;
			this.top = top;
		}

		private boolean left() {
			return left;
		}

		private boolean top() {
			return top;
		}
	}
}
