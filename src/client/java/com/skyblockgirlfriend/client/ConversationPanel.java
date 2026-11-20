package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ConversationPanel {
	private static final int MIN_WIDTH = 170;
	private static final int MIN_HEIGHT = 180;
	private static final int AVATAR_SIZE = 20;
	private static final int HEADER_HEIGHT = 24;
	private static final int INNER_PADDING = 6;
	private static final int SCREEN_MARGIN = 8;
	private static final int RESIZE_HANDLE_SIZE = 10;
	private static final int COMPOSER_HEIGHT = 22;
	private static final int SEND_BUTTON_WIDTH = 34;
	private static final int MIN_COMPOSER_PANEL_WIDTH = 80;

	private final CompanionSettings settings;
	private final CompanionMessaging messaging;
	private final List<MessageRow> cachedRows = new ArrayList<>();
	private EditBox messageInput;
	private int cachedViewportWidth = -1;
	private int scrollOffset;
	private int dragGrabOffset;
	private int dragPanelOffsetX;
	private int dragPanelOffsetY;
	private int lastMessageCount;
	private boolean draggingScrollbar;
	private boolean draggingPanel;
	private ResizeCorner resizeCorner;
	private PanelLayout resizeStartLayout;
	private int resizeStartMouseX;
	private int resizeStartMouseY;

	public ConversationPanel(CompanionSettings settings, CompanionMessaging messaging) {
		this.settings = settings;
		this.messaging = messaging;
	}

	public void attach(InventoryScreen screen) {
		messageInput = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 18, Component.literal("Message"));
		messageInput.setMaxLength(256);
		messageInput.setBordered(false);
		messageInput.setHint(Component.literal("Message..."));
		setInputFocused(false);
		ScreenEvents.afterExtract(screen).register(this::extract);
		ScreenMouseEvents.allowMouseClick(screen).register(this::allowMouseClick);
		ScreenMouseEvents.allowMouseDrag(screen).register(this::allowMouseDrag);
		ScreenMouseEvents.allowMouseRelease(screen).register(this::allowMouseRelease);
		ScreenMouseEvents.allowMouseScroll(screen).register(this::allowMouseScroll);
		ScreenKeyboardEvents.allowKeyPress(screen).register(this::allowKeyPress);
		ScreenKeyboardEvents.allowCharType(screen).register(this::allowCharType);
		ScreenEvents.remove(screen).register(removed -> {
			draggingScrollbar = false;
			setInputFocused(false);
			if (draggingPanel || resizeCorner != null) {
				settings.save();
				draggingPanel = false;
				resizeCorner = null;
				resizeStartLayout = null;
			}
		});
	}

	private void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		PanelLayout layout = layout(screen.width, screen.height);
		if (layout == null) {
			setInputFocused(false);
			return;
		}
		List<MessageRow> rows = messageRows(client.font, layout);
		if (rows.size() > lastMessageCount && scrollOffset > 0) {
			for (int index = lastMessageCount; index < rows.size(); index++) {
				scrollOffset += rows.get(index).rowHeight();
			}
		}
		lastMessageCount = rows.size();
		int totalHeight = rows.stream().mapToInt(MessageRow::rowHeight).sum();
		int maxScroll = Math.max(0, totalHeight - layout.viewportHeight());
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

		graphics.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(),
				0xCC141414);
		graphics.outline(layout.x(), layout.y(), layout.width(), layout.height(), 0xFF555555);
		int titleWidth = Math.max(1, layout.width() - (layout.width() >= 100 ? 34 : 12));
		String title = client.font.plainSubstrByWidth(settings.companionDisplayName(), titleWidth);
		graphics.enableScissor(layout.x() + INNER_PADDING, layout.y(),
				layout.x() + layout.width() - INNER_PADDING, layout.y() + HEADER_HEIGHT);
		graphics.text(client.font, title, layout.x() + INNER_PADDING, layout.y() + 7, 0xFFFFFFFF);
		graphics.disableScissor();
		if (layout.width() >= 100) {
			drawMoveHandle(graphics, layout.x() + layout.width() - 25, layout.y() + 5);
		}
		graphics.horizontalLine(layout.x() + 4, layout.x() + layout.width() - 4,
				layout.y() + HEADER_HEIGHT - 1, 0x88555555);

		if (rows.isEmpty()) {
			graphics.centeredText(client.font, "No messages yet", layout.x() + layout.width() / 2,
					layout.viewportY() + 8, 0xFFAAAAAA);
		} else {
			int scrollFromTop = maxScroll - scrollOffset;
			int rowY = layout.viewportY() - scrollFromTop;
			PlayerSkin playerSkin = client.player.getSkin();
			PlayerSkin companionSkin = settings.skinForRender(client, playerSkin);
			graphics.enableScissor(layout.viewportX(), layout.viewportY(),
					layout.viewportX() + layout.viewportWidth(), layout.viewportY() + layout.viewportHeight());
			for (MessageRow row : rows) {
				drawMessage(graphics, client.font, row, layout, rowY,
						row.message().sender() == CompanionMessaging.Sender.COMPANION ? companionSkin : playerSkin);
				rowY += row.rowHeight();
			}
			graphics.disableScissor();
			drawScrollbar(graphics, layout, totalHeight, maxScroll);
		}
		drawComposer(graphics, client.font, layout, mouseX, mouseY, delta);
		drawResizeHandles(graphics, layout);
	}

	private void drawMessage(
			GuiGraphicsExtractor graphics,
			Font font,
			MessageRow row,
			PanelLayout panel,
			int rowY,
			PlayerSkin skin
	) {
		boolean companion = row.message().sender() == CompanionMessaging.Sender.COMPANION;
		int bubbleY = rowY + (row.rowHeight() - row.bubbleHeight()) / 2;
		int bubbleX = companion
				? panel.viewportX() + AVATAR_SIZE + 4
				: panel.viewportX() + panel.viewportWidth() - AVATAR_SIZE - 4 - row.bubbleWidth();
		int avatarX = companion
				? panel.viewportX()
				: panel.viewportX() + panel.viewportWidth() - AVATAR_SIZE;
		int avatarY = rowY + (row.rowHeight() - AVATAR_SIZE) / 2;

		int bubbleColor = companion ? 0xB8141414 : 0xB8204F70;
		int outlineColor = companion ? 0x88555555 : 0xAA70B8E8;
		graphics.fill(bubbleX, bubbleY, bubbleX + row.bubbleWidth(), bubbleY + row.bubbleHeight(), bubbleColor);
		graphics.outline(bubbleX, bubbleY, row.bubbleWidth(), row.bubbleHeight(), outlineColor);
		SkinHeadRenderer.draw(graphics, skin, avatarX, avatarY, AVATAR_SIZE);
		int textY = bubbleY + 4;
		for (FormattedCharSequence line : row.lines()) {
			graphics.text(font, line, bubbleX + 4, textY, 0xFFFFFFFF);
			textY += font.lineHeight;
		}
	}

	private void drawComposer(
			GuiGraphicsExtractor graphics,
			Font font,
			PanelLayout layout,
			int mouseX,
			int mouseY,
			float delta
	) {
		ComposerLayout composer = composerLayout(layout);
		graphics.horizontalLine(layout.x() + 4, layout.x() + layout.width() - 4,
				composer.y() - 4, 0x88555555);
		graphics.fill(composer.inputX(), composer.y(), composer.sendX() - 4,
				composer.y() + COMPOSER_HEIGHT, 0xCC0D0D0D);
		graphics.outline(composer.inputX(), composer.y(), composer.sendX() - 4 - composer.inputX(),
				COMPOSER_HEIGHT, messageInput.isFocused() ? 0xFF70B8E8 : 0xFF555555);

		messageInput.setX(composer.inputX() + 4);
		messageInput.setY(composer.y() + 2);
		messageInput.setWidth(Math.max(1, composer.sendX() - composer.inputX() - 12));
		messageInput.setHeight(COMPOSER_HEIGHT - 4);
		messageInput.extractRenderState(graphics, mouseX, mouseY, delta);

		boolean hasMessage = !messageInput.getValue().isBlank();
		graphics.fill(composer.sendX(), composer.y(), composer.sendX() + SEND_BUTTON_WIDTH,
				composer.y() + COMPOSER_HEIGHT, hasMessage ? 0xFF315F7F : 0xFF292929);
		graphics.outline(composer.sendX(), composer.y(), SEND_BUTTON_WIDTH, COMPOSER_HEIGHT, 0xFF555555);
		graphics.centeredText(font, "Send", composer.sendX() + SEND_BUTTON_WIDTH / 2,
				composer.y() + 7, hasMessage ? 0xFFFFFFFF : 0xFF888888);
	}

	private void drawScrollbar(
			GuiGraphicsExtractor graphics,
			PanelLayout layout,
			int contentHeight,
			int maxScroll
	) {
		if (maxScroll == 0) {
			return;
		}
		int trackX = layout.x() + layout.width() - 7;
		int trackY = layout.viewportY();
		int trackHeight = layout.viewportHeight();
		int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
		thumbHeight = Math.min(trackHeight, thumbHeight);
		int thumbTravel = trackHeight - thumbHeight;
		int thumbY = trackY + thumbTravel
				- (int) Math.round((double) scrollOffset / maxScroll * thumbTravel);
		graphics.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0x66555555);
		graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFFAAAAAA);
	}

	private List<MessageRow> messageRows(Font font, PanelLayout layout) {
		int messageCount = messaging.messageCount();
		if (cachedViewportWidth != layout.viewportWidth() || messageCount < cachedRows.size()) {
			cachedRows.clear();
			cachedViewportWidth = layout.viewportWidth();
		}

		int availableWidth = Math.max(16, layout.viewportWidth() - AVATAR_SIZE - 8);
		int maxTextWidth = Math.max(8, availableWidth - 8);
		while (cachedRows.size() < messageCount) {
			CompanionMessaging.Message message = messaging.messageAt(cachedRows.size());
			List<FormattedCharSequence> lines = font.split(Component.literal(message.text()), maxTextWidth);
			if (lines.isEmpty()) {
				lines = List.of(FormattedCharSequence.EMPTY);
			}
			int widestLine = 0;
			for (FormattedCharSequence line : lines) {
				widestLine = Math.max(widestLine, font.width(line));
			}
			int bubbleWidth = Math.min(availableWidth, Math.max(24, widestLine + 8));
			int bubbleHeight = lines.size() * font.lineHeight + 8;
			int rowHeight = Math.max(AVATAR_SIZE, bubbleHeight) + 4;
			cachedRows.add(new MessageRow(message, List.copyOf(lines), bubbleWidth, bubbleHeight, rowHeight));
		}
		return cachedRows;
	}

	private boolean allowMouseClick(Screen screen, MouseButtonEvent event) {
		PanelLayout layout = layout(screen.width, screen.height);
		if (layout == null || !contains(layout, event.x(), event.y())) {
			setInputFocused(false);
			return true;
		}
		if (event.button() == 0) {
			ComposerLayout composer = composerLayout(layout);
			if (containsInput(composer, event.x(), event.y())) {
				setInputFocused(true);
				messageInput.mouseClicked(event, false);
				return false;
			}
			if (containsSendButton(composer, event.x(), event.y())) {
				setInputFocused(true);
				sendMessage();
				return false;
			}
			setInputFocused(false);
			resizeCorner = findResizeCorner((int) event.x(), (int) event.y(), layout);
			if (resizeCorner != null) {
				resizeStartLayout = layout;
				resizeStartMouseX = (int) event.x();
				resizeStartMouseY = (int) event.y();
				return false;
			}
			if (event.y() < layout.y() + HEADER_HEIGHT) {
				draggingPanel = true;
				dragPanelOffsetX = (int) event.x() - layout.x();
				dragPanelOffsetY = (int) event.y() - layout.y();
				return false;
			}
			ScrollGeometry geometry = scrollGeometry(layout);
			if (geometry.maxScroll() > 0 && event.x() >= geometry.trackX() - 3
					&& event.x() <= geometry.trackX() + 6) {
				draggingScrollbar = true;
				if (event.y() >= geometry.thumbY() && event.y() <= geometry.thumbY() + geometry.thumbHeight()) {
					dragGrabOffset = (int) event.y() - geometry.thumbY();
				} else {
					dragGrabOffset = geometry.thumbHeight() / 2;
					updateScrollFromThumb(layout, (int) event.y() - dragGrabOffset);
				}
			}
		}
		return false;
	}

	private boolean allowKeyPress(Screen screen, KeyEvent event) {
		if (!messageInput.isFocused()) {
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			setInputFocused(false);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			sendMessage();
			return false;
		}
		messageInput.keyPressed(event);
		return false;
	}

	private boolean allowCharType(Screen screen, CharacterEvent event) {
		if (!messageInput.isFocused()) {
			return true;
		}
		messageInput.charTyped(event);
		return false;
	}

	private void setInputFocused(boolean focused) {
		if (messageInput == null) {
			return;
		}
		messageInput.setFocused(focused);
		messaging.setNotificationsSuppressed(focused);
	}

	private void sendMessage() {
		String text = messageInput.getValue().trim();
		if (text.isEmpty()) {
			return;
		}

		messaging.addPlayerMessage(text);
		messaging.addMessage(CompanionMessaging.Sender.COMPANION, BasicMathResponder.respond(text));
		messageInput.setValue("");
		scrollOffset = 0;
	}

	private boolean allowMouseDrag(Screen screen, MouseButtonEvent event, double deltaX, double deltaY) {
		if (resizeCorner != null && event.button() == 0) {
			resizePanel(screen, (int) event.x() - resizeStartMouseX,
					(int) event.y() - resizeStartMouseY);
			return false;
		}
		if (draggingPanel && event.button() == 0) {
			movePanel(screen, (int) event.x() - dragPanelOffsetX, (int) event.y() - dragPanelOffsetY);
			return false;
		}
		if (!draggingScrollbar || event.button() != 0) {
			return true;
		}
		PanelLayout layout = layout(screen.width, screen.height);
		if (layout != null) {
			updateScrollFromThumb(layout, (int) event.y() - dragGrabOffset);
		}
		return false;
	}

	private boolean allowMouseRelease(Screen screen, MouseButtonEvent event) {
		boolean wasDragging = draggingScrollbar || draggingPanel || resizeCorner != null;
		if (event.button() == 0) {
			if (draggingPanel || resizeCorner != null) {
				settings.save();
			}
			draggingScrollbar = false;
			draggingPanel = false;
			resizeCorner = null;
			resizeStartLayout = null;
		}
		PanelLayout layout = layout(screen.width, screen.height);
		return !wasDragging && (layout == null || !contains(layout, event.x(), event.y()));
	}

	private boolean allowMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount,
			double verticalAmount) {
		PanelLayout layout = layout(screen.width, screen.height);
		if (layout == null || !contains(layout, mouseX, mouseY)) {
			return true;
		}
		int maxScroll = maxScroll(layout);
		scrollOffset = Math.max(0, Math.min(maxScroll,
				scrollOffset + (int) Math.round(verticalAmount * 18.0)));
		return false;
	}

	private static ComposerLayout composerLayout(PanelLayout layout) {
		int x = layout.x() + INNER_PADDING;
		int y = layout.y() + layout.height() - INNER_PADDING - COMPOSER_HEIGHT;
		int sendX = layout.x() + layout.width() - INNER_PADDING - SEND_BUTTON_WIDTH;
		return new ComposerLayout(x, y, sendX);
	}

	private static boolean containsInput(ComposerLayout composer, double x, double y) {
		return x >= composer.inputX() && x < composer.sendX() - 4
				&& y >= composer.y() && y < composer.y() + COMPOSER_HEIGHT;
	}

	private static boolean containsSendButton(ComposerLayout composer, double x, double y) {
		return x >= composer.sendX() && x < composer.sendX() + SEND_BUTTON_WIDTH
				&& y >= composer.y() && y < composer.y() + COMPOSER_HEIGHT;
	}

	private void updateScrollFromThumb(PanelLayout layout, int thumbY) {
		ScrollGeometry geometry = scrollGeometry(layout);
		int thumbTravel = geometry.trackHeight() - geometry.thumbHeight();
		if (geometry.maxScroll() == 0 || thumbTravel == 0) {
			scrollOffset = 0;
			return;
		}
		int clampedThumbY = Math.max(geometry.trackY(),
				Math.min(geometry.trackY() + thumbTravel, thumbY));
		double fromBottom = (double) (geometry.trackY() + thumbTravel - clampedThumbY) / thumbTravel;
		scrollOffset = (int) Math.round(fromBottom * geometry.maxScroll());
	}

	private void movePanel(Screen screen, int x, int y) {
		PanelLayout current = layout(screen.width, screen.height);
		if (current == null) {
			return;
		}
		int clampedX = Math.max(SCREEN_MARGIN, Math.min(SCREEN_MARGIN + current.maxX(), x));
		int clampedY = Math.max(SCREEN_MARGIN, Math.min(SCREEN_MARGIN + current.maxY(), y));
		settings.setConversationPosition(
				current.maxX() == 0 ? 0.0F : (float) (clampedX - SCREEN_MARGIN) / current.maxX(),
				current.maxY() == 0 ? 0.0F : (float) (clampedY - SCREEN_MARGIN) / current.maxY()
		);
	}

	private void resizePanel(Screen screen, int deltaX, int deltaY) {
		PanelLayout start = resizeStartLayout;
		if (start == null || resizeCorner == null) {
			return;
		}

		int inventoryLeft = screen.width / 2 - 88;
		int maximumWidth = resizeCorner.left()
				? start.x() + start.width() - SCREEN_MARGIN
				: inventoryLeft - SCREEN_MARGIN - start.x();
		int minimumWidth = Math.min(MIN_WIDTH, Math.max(1, maximumWidth));
		int widthDelta = resizeCorner.left() ? -deltaX : deltaX;
		int newWidth = clamp(start.width() + widthDelta, minimumWidth, Math.max(minimumWidth, maximumWidth));
		int newX = resizeCorner.left() ? start.x() + start.width() - newWidth : start.x();

		int maximumHeight = resizeCorner.top()
				? start.y() + start.height() - SCREEN_MARGIN
				: screen.height - SCREEN_MARGIN - start.y();
		int minimumHeight = Math.min(MIN_HEIGHT, Math.max(1, maximumHeight));
		int heightDelta = resizeCorner.top() ? -deltaY : deltaY;
		int newHeight = clamp(start.height() + heightDelta, minimumHeight,
				Math.max(minimumHeight, maximumHeight));
		int newY = resizeCorner.top() ? start.y() + start.height() - newHeight : start.y();

		newX = clamp(newX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, inventoryLeft - newWidth - SCREEN_MARGIN));
		newY = clamp(newY, SCREEN_MARGIN,
				Math.max(SCREEN_MARGIN, screen.height - newHeight - SCREEN_MARGIN));
		settings.setConversationSize(newWidth, newHeight);

		PanelLayout resized = layout(screen.width, screen.height);
		if (resized != null) {
			settings.setConversationPosition(
					resized.maxX() == 0 ? 0.0F : (float) (newX - SCREEN_MARGIN) / resized.maxX(),
					resized.maxY() == 0 ? 0.0F : (float) (newY - SCREEN_MARGIN) / resized.maxY()
			);
		}
	}

	private int maxScroll(PanelLayout layout) {
		int contentHeight = messageRows(Minecraft.getInstance().font, layout)
				.stream().mapToInt(MessageRow::rowHeight).sum();
		return Math.max(0, contentHeight - layout.viewportHeight());
	}

	private ScrollGeometry scrollGeometry(PanelLayout layout) {
		int contentHeight = messageRows(Minecraft.getInstance().font, layout)
				.stream().mapToInt(MessageRow::rowHeight).sum();
		int maxScroll = Math.max(0, contentHeight - layout.viewportHeight());
		int trackHeight = layout.viewportHeight();
		int thumbHeight = maxScroll == 0 ? trackHeight
				: Math.min(trackHeight, Math.max(16, trackHeight * trackHeight / contentHeight));
		int thumbTravel = trackHeight - thumbHeight;
		int thumbY = layout.viewportY() + thumbTravel
				- (maxScroll == 0 ? 0 : (int) Math.round((double) scrollOffset / maxScroll * thumbTravel));
		return new ScrollGeometry(layout.x() + layout.width() - 7, layout.viewportY(), trackHeight,
				thumbY, thumbHeight, maxScroll);
	}

	private static void drawMoveHandle(GuiGraphicsExtractor graphics, int x, int y) {
		int color = 0xFFAAAAAA;
		graphics.fill(x, y, x + 14, y + 14, 0xCC101010);
		graphics.outline(x, y, 14, 14, 0xFF666666);
		int centerX = x + 7;
		int centerY = y + 7;
		graphics.fill(centerX - 1, centerY - 4, centerX + 1, centerY + 4, color);
		graphics.fill(centerX - 4, centerY - 1, centerX + 4, centerY + 1, color);
		graphics.fill(centerX - 1, centerY - 6, centerX + 1, centerY - 3, color);
		graphics.fill(centerX - 1, centerY + 3, centerX + 1, centerY + 6, color);
		graphics.fill(centerX - 6, centerY - 1, centerX - 3, centerY + 1, color);
		graphics.fill(centerX + 3, centerY - 1, centerX + 6, centerY + 1, color);
		graphics.fill(centerX - 3, centerY - 4, centerX - 1, centerY - 2, color);
		graphics.fill(centerX + 1, centerY - 4, centerX + 3, centerY - 2, color);
		graphics.fill(centerX - 3, centerY + 2, centerX - 1, centerY + 4, color);
		graphics.fill(centerX + 1, centerY + 2, centerX + 3, centerY + 4, color);
		graphics.fill(centerX - 4, centerY - 3, centerX - 2, centerY - 1, color);
		graphics.fill(centerX - 4, centerY + 1, centerX - 2, centerY + 3, color);
		graphics.fill(centerX + 2, centerY - 3, centerX + 4, centerY - 1, color);
		graphics.fill(centerX + 2, centerY + 1, centerX + 4, centerY + 3, color);
	}

	private static void drawResizeHandles(GuiGraphicsExtractor graphics, PanelLayout layout) {
		int size = 6;
		int color = 0xFFAAAAAA;
		graphics.fill(layout.x(), layout.y(), layout.x() + size, layout.y() + size, color);
		graphics.fill(layout.x() + layout.width() - size, layout.y(),
				layout.x() + layout.width(), layout.y() + size, color);
		graphics.fill(layout.x(), layout.y() + layout.height() - size,
				layout.x() + size, layout.y() + layout.height(), color);
		graphics.fill(layout.x() + layout.width() - size, layout.y() + layout.height() - size,
				layout.x() + layout.width(), layout.y() + layout.height(), color);
	}

	private static ResizeCorner findResizeCorner(int mouseX, int mouseY, PanelLayout layout) {
		for (ResizeCorner corner : ResizeCorner.values()) {
			int left = corner.left() ? layout.x() : layout.x() + layout.width() - RESIZE_HANDLE_SIZE;
			int top = corner.top() ? layout.y() : layout.y() + layout.height() - RESIZE_HANDLE_SIZE;
			if (mouseX >= left && mouseX <= left + RESIZE_HANDLE_SIZE
					&& mouseY >= top && mouseY <= top + RESIZE_HANDLE_SIZE) {
				return corner;
			}
		}
		return null;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static boolean contains(PanelLayout layout, double x, double y) {
		return x >= layout.x() && x < layout.x() + layout.width()
				&& y >= layout.y() && y < layout.y() + layout.height();
	}

	private PanelLayout layout(int screenWidth, int screenHeight) {
		int inventoryLeft = screenWidth / 2 - 88;
		int availableWidth = inventoryLeft - SCREEN_MARGIN * 2;
		if (availableWidth < MIN_COMPOSER_PANEL_WIDTH || screenHeight < 104) {
			return null;
		}
		int width = Math.min(settings.conversationWidth(), availableWidth);
		int height = Math.min(settings.conversationHeight(), screenHeight - SCREEN_MARGIN * 2);
		int maxX = Math.max(0, inventoryLeft - width - SCREEN_MARGIN * 2);
		int maxY = Math.max(0, screenHeight - height - SCREEN_MARGIN * 2);
		int x = SCREEN_MARGIN + Math.round(settings.conversationPositionX() * maxX);
		int y = SCREEN_MARGIN + Math.round(settings.conversationPositionY() * maxY);
		int viewportX = x + INNER_PADDING;
		int viewportY = y + HEADER_HEIGHT;
		int viewportWidth = Math.max(8, width - INNER_PADDING * 2 - 8);
		int viewportHeight = Math.max(1, height - HEADER_HEIGHT - INNER_PADDING - COMPOSER_HEIGHT - INNER_PADDING);
		return new PanelLayout(x, y, width, height, viewportX, viewportY, viewportWidth, viewportHeight,
				maxX, maxY);
	}

	private record PanelLayout(
			int x,
			int y,
			int width,
			int height,
			int viewportX,
			int viewportY,
			int viewportWidth,
			int viewportHeight,
			int maxX,
			int maxY
	) {
	}

	private record MessageRow(
			CompanionMessaging.Message message,
			List<FormattedCharSequence> lines,
			int bubbleWidth,
			int bubbleHeight,
			int rowHeight
	) {
	}

	private record ComposerLayout(int inputX, int y, int sendX) {
	}

	private record ScrollGeometry(
			int trackX,
			int trackY,
			int trackHeight,
			int thumbY,
			int thumbHeight,
			int maxScroll
	) {
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
