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
	private final ConversationPanel chatPanel;
	private final Screen parent;
	private Button chatToggle;
	private Button notificationToggle;
	private boolean showChat = true;
	private boolean showNotification = true;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean draggingChat;
	private boolean draggingNotification;
	private ResizeCorner notificationResizeCorner;
	private ChatResizeCorner chatResizeCorner;
	private NotificationRenderer.Bounds notificationResizeStart;
	private ConversationPanel.PanelBounds chatResizeStart;
	private int resizeStartMouseX;
	private int resizeStartMouseY;

	public NotificationPositionScreen(
			CompanionSettings settings,
			NotificationRenderer renderer,
			ConversationPanel chatPanel,
			Screen parent
	) {
		super(Component.literal("GUI Layout"));
		this.settings = settings;
		this.renderer = renderer;
		this.chatPanel = chatPanel;
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
			if (showChat) {
				chatPanel.renderEditorPreview(graphics, minecraft, width, height);
			}
			if (showNotification) {
				renderer.renderPreview(graphics, minecraft, true);
			}
		});
		chatToggle = addRenderableWidget(Button.builder(chatToggleText(), button -> {
			showChat = !showChat;
			chatToggle.setMessage(chatToggleText());
		}).bounds(width / 2 - 105, height - 72, 100, 20).build());
		notificationToggle = addRenderableWidget(Button.builder(notificationToggleText(), button -> {
			showNotification = !showNotification;
			notificationToggle.setMessage(notificationToggleText());
		}).bounds(width / 2 + 5, height - 72, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset Position"), button -> {
			if (showChat) {
				settings.setConversationPosition(0.0F, 0.5F);
			}
			if (showNotification) {
				settings.setNotificationPosition(0.02F, 0.08F);
			}
			settings.save();
		}).bounds(width / 2 - 105, height - 42, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 + 5, height - 42, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, Component.literal("GUI Layout"), width / 2, 28, 0xFFFFFF);
		graphics.centeredText(font, Component.literal("Toggle previews to edit overlapping Chat and Notification panels separately."),
				width / 2, 44, 0xAAAAAA);
		graphics.centeredText(font, Component.literal("Drag a panel to move it; drag a corner to resize it."),
				width / 2, 58, 0xAAAAAA);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}

		if (showChat) {
			ConversationPanel.PanelBounds chatBounds = chatPanel.previewBounds(width, height);
			if (chatBounds != null && contains(chatBounds, event.x(), event.y())) {
				chatResizeCorner = findChatResizeCorner((int) event.x(), (int) event.y(), chatBounds);
				if (chatResizeCorner != null) {
					chatResizeStart = chatBounds;
					resizeStartMouseX = (int) event.x();
					resizeStartMouseY = (int) event.y();
					return true;
				}
				dragOffsetX = (int) event.x() - chatBounds.x();
				dragOffsetY = (int) event.y() - chatBounds.y();
				draggingChat = true;
				return true;
			}
		}
		if (showNotification) {
			NotificationRenderer.Bounds bounds = renderer.previewBounds(width, height, font);
			notificationResizeCorner = findResizeCorner((int) event.x(), (int) event.y(), bounds);
			if (notificationResizeCorner != null) {
				notificationResizeStart = bounds;
				resizeStartMouseX = (int) event.x();
				resizeStartMouseY = (int) event.y();
				return true;
			}
			if (contains(bounds, event.x(), event.y())) {
				dragOffsetX = (int) event.x() - bounds.x();
				dragOffsetY = (int) event.y() - bounds.y();
				draggingNotification = true;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (chatResizeCorner != null && event.button() == 0) {
			chatPanel.resizePreviewFromCorner(chatResizeStart, chatResizeCorner.left(), chatResizeCorner.top(),
					(int) event.x() - resizeStartMouseX, (int) event.y() - resizeStartMouseY, width, height);
			return true;
		}
		if (draggingChat && event.button() == 0) {
			chatPanel.setPositionFromTopLeft((int) event.x() - dragOffsetX,
					(int) event.y() - dragOffsetY, width, height);
			return true;
		}
		if (notificationResizeCorner != null && event.button() == 0) {
			renderer.resizeFromCorner(notificationResizeStart,
					notificationResizeCorner.left(), notificationResizeCorner.top(),
					(int) event.x() - resizeStartMouseX, (int) event.y() - resizeStartMouseY,
					width, height, font);
			return true;
		}
		if (draggingNotification && event.button() == 0) {
			renderer.setPositionFromTopLeft((int) event.x() - dragOffsetX,
					(int) event.y() - dragOffsetY, width, height, font);
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && (draggingChat || draggingNotification
				|| chatResizeCorner != null || notificationResizeCorner != null)) {
			draggingChat = false;
			draggingNotification = false;
			chatResizeCorner = null;
			notificationResizeCorner = null;
			chatResizeStart = null;
			notificationResizeStart = null;
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

	private static ChatResizeCorner findChatResizeCorner(
			int mouseX,
			int mouseY,
			ConversationPanel.PanelBounds bounds
	) {
		for (ChatResizeCorner corner : ChatResizeCorner.values()) {
			int left = corner.left() ? bounds.x() : bounds.x() + bounds.width() - RESIZE_HANDLE_SIZE;
			int top = corner.top() ? bounds.y() : bounds.y() + bounds.height() - RESIZE_HANDLE_SIZE;
			if (mouseX >= left && mouseX <= left + RESIZE_HANDLE_SIZE
					&& mouseY >= top && mouseY <= top + RESIZE_HANDLE_SIZE) {
				return corner;
			}
		}
		return null;
	}

	private static boolean contains(NotificationRenderer.Bounds bounds, double x, double y) {
		return x >= bounds.x() && x <= bounds.x() + bounds.width()
				&& y >= bounds.y() && y <= bounds.y() + bounds.height();
	}

	private static boolean contains(ConversationPanel.PanelBounds bounds, double x, double y) {
		return x >= bounds.x() && x <= bounds.x() + bounds.width()
				&& y >= bounds.y() && y <= bounds.y() + bounds.height();
	}

	private Component chatToggleText() {
		return Component.literal("Chat: " + (showChat ? "On" : "Off"));
	}

	private Component notificationToggleText() {
		return Component.literal("Notification: " + (showNotification ? "On" : "Off"));
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

	private enum ChatResizeCorner {
		TOP_LEFT(true, true),
		TOP_RIGHT(false, true),
		BOTTOM_LEFT(true, false),
		BOTTOM_RIGHT(false, false);

		private final boolean left;
		private final boolean top;

		ChatResizeCorner(boolean left, boolean top) {
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
