package com.skyblockgirlfriend.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class MessageNotifications {
	private final CompanionMessaging messaging;
	private final NotificationRenderer renderer;

	public MessageNotifications(CompanionMessaging messaging, NotificationRenderer renderer) {
		this.messaging = messaging;
		this.renderer = renderer;
	}

	public void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		render(graphics, client);
	}

	public void extractScreen(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || screen instanceof NotificationPositionScreen) {
			return;
		}
		render(graphics, client);
	}

	private void render(GuiGraphicsExtractor graphics, Minecraft client) {
		CompanionMessaging.ActiveNotification active = messaging.activeNotification(System.nanoTime());
		if (active != null && active.opacity() > 0.0F) {
			renderer.render(graphics, client, active.message(), active.opacity());
		}
	}
}
