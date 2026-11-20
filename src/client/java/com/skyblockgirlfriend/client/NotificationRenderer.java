package com.skyblockgirlfriend.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;

public final class NotificationRenderer {
	private static final int MIN_WIDTH = 280;
	private static final int MIN_HEIGHT = 52;
	private static final int HEIGHT_PADDING = 16;
	private static final int AVATAR_SIZE = 32;

	private final CompanionSettings settings;

	public NotificationRenderer(CompanionSettings settings) {
		this.settings = settings;
	}

	public void render(GuiGraphicsExtractor graphics, Minecraft client, CompanionMessaging.Message message,
			float opacity) {
		Layout layout = layout(graphics.guiWidth(), graphics.guiHeight(), client.font, message.text());
		draw(graphics, client, message.text(), layout, opacity);
	}

	public void renderPreview(GuiGraphicsExtractor graphics, Minecraft client) {
		String text = "HOW DID YOU DIE?!";
		Layout layout = layout(graphics.guiWidth(), graphics.guiHeight(), client.font, text);
		draw(graphics, client, text, layout, 1.0F);
		drawResizeHandles(graphics, layout);
	}

	public Bounds previewBounds(int screenWidth, int screenHeight, Font font) {
		Layout layout = layout(screenWidth, screenHeight, font, "HOW DID YOU DIE?!");
		return new Bounds(layout.x(), layout.y(), layout.width(), layout.height());
	}

	public void setPositionFromTopLeft(int x, int y, int screenWidth, int screenHeight, Font font) {
		Bounds bounds = previewBounds(screenWidth, screenHeight, font);
		int maxX = Math.max(0, screenWidth - bounds.width());
		int maxY = Math.max(0, screenHeight - bounds.height());
		settings.setNotificationPosition(
				maxX == 0 ? 0.0F : (float) Math.max(0, Math.min(maxX, x)) / maxX,
				maxY == 0 ? 0.0F : (float) Math.max(0, Math.min(maxY, y)) / maxY
		);
	}

	public void resizeFromCorner(
			Bounds startingBounds,
			boolean resizeLeft,
			boolean resizeTop,
			int deltaX,
			int deltaY,
			int screenWidth,
			int screenHeight,
			Font font
	) {
		int maximumWidth = Math.max(1, screenWidth - 16);
		int minimumWidth = Math.min(MIN_WIDTH, maximumWidth);
		int maximumHeight = Math.max(1, screenHeight - 16);
		int minimumHeight = Math.min(MIN_HEIGHT, maximumHeight);
		int width = clamp(startingBounds.width() + (resizeLeft ? -deltaX : deltaX),
				minimumWidth, maximumWidth);
		int height = clamp(startingBounds.height() + (resizeTop ? -deltaY : deltaY),
				minimumHeight, maximumHeight);
		int x = resizeLeft ? startingBounds.x() + startingBounds.width() - width : startingBounds.x();
		int y = resizeTop ? startingBounds.y() + startingBounds.height() - height : startingBounds.y();

		settings.setNotificationSize(width, height);
		setPositionFromTopLeft(x, y, screenWidth, screenHeight, font);
	}

	private void draw(GuiGraphicsExtractor graphics, Minecraft client, String text, Layout layout, float opacity) {
		int x = layout.x();
		int y = layout.y();
		int width = layout.width();
		int height = layout.height();
		graphics.fill(x, y, x + width, y + height, withOpacity(0xCC141414, opacity));
		graphics.outline(x, y, width, height, withOpacity(0xFF555555, opacity));

		PlayerSkin baseSkin = client.player == null
				? DefaultPlayerSkin.getDefaultSkin()
				: client.player.getSkin();
		PlayerSkin skin = settings.skinForRender(client, baseSkin);
		int avatarX = x + 8;
		int avatarY = y + (height - AVATAR_SIZE) / 2;
		SkinHeadRenderer.draw(graphics, skin, avatarX, avatarY, AVATAR_SIZE);
		if (opacity < 1.0F) {
			graphics.fill(avatarX, avatarY, avatarX + AVATAR_SIZE, avatarY + AVATAR_SIZE,
					withOpacity(0xFF000000, 1.0F - opacity));
		}

		List<FormattedCharSequence> lines = client.font.split(Component.literal(text), layout.textWidth());
		int textY = y + (height - lines.size() * client.font.lineHeight) / 2;
		graphics.enableScissor(x + 8 + AVATAR_SIZE + 12, y + 4,
				x + width - 4, y + height - 4);
		for (FormattedCharSequence line : lines) {
			graphics.text(client.font, line, x + 8 + AVATAR_SIZE + 12, textY, withOpacity(0xFFFFFFFF, opacity));
			textY += client.font.lineHeight;
		}
		graphics.disableScissor();
	}

	private Layout layout(int screenWidth, int screenHeight, Font font, String text) {
		int width = Math.max(1, Math.min(settings.notificationWidth(), screenWidth - 16));
		int textWidth = Math.max(8, width - 8 - AVATAR_SIZE - 12 - 8);
		int lineCount = Math.max(1, font.split(Component.literal(text), textWidth).size());
		int maximumHeight = Math.max(1, screenHeight - 16);
		int height = Math.min(maximumHeight,
				Math.max(settings.notificationHeight(), HEIGHT_PADDING + lineCount * font.lineHeight));
		int maxX = Math.max(0, screenWidth - width);
		int maxY = Math.max(0, screenHeight - height);
		int x = Math.round(settings.notificationPositionX() * maxX);
		int y = Math.round(settings.notificationPositionY() * maxY);
		return new Layout(x, y, width, height, textWidth);
	}

	private static void drawResizeHandles(GuiGraphicsExtractor graphics, Layout layout) {
		int size = 6;
		int color = 0xFF55DD66;
		graphics.fill(layout.x(), layout.y(), layout.x() + size, layout.y() + size, color);
		graphics.fill(layout.x() + layout.width() - size, layout.y(),
				layout.x() + layout.width(), layout.y() + size, color);
		graphics.fill(layout.x(), layout.y() + layout.height() - size,
				layout.x() + size, layout.y() + layout.height(), color);
		graphics.fill(layout.x() + layout.width() - size, layout.y() + layout.height() - size,
				layout.x() + layout.width(), layout.y() + layout.height(), color);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int withOpacity(int color, float opacity) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, opacity)));
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	private record Layout(int x, int y, int width, int height, int textWidth) {
	}

	public record Bounds(int x, int y, int width, int height) {
	}
}
