package com.skyblockgirlfriend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class SkyblockGirlfriendClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CompanionSettings settings = new CompanionSettings();
		MiniatureCompanion companion = new MiniatureCompanion(settings);
		CompanionMessaging messaging = new CompanionMessaging();
		NotificationRenderer notificationRenderer = new NotificationRenderer(settings);
		MessageNotifications notifications = new MessageNotifications(messaging, notificationRenderer);
		ConversationPanel conversationPanel = new ConversationPanel(settings, messaging);
		HypixelDeathDetector deathDetector = new HypixelDeathDetector(messaging);
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("skyblock-girlfriend", "companion")
		);
		KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.skyblock-girlfriend.companion_menu",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_G,
				category
		));
		ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				deathDetector.onSystemMessage(Minecraft.getInstance(), message));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			companion.tick(client);
			deathDetector.tick(client);
			while (menuKey.consumeClick()) {
				if (client.gui.screen() instanceof CompanionScreen
						|| client.gui.screen() instanceof SkinPickerScreen
						|| client.gui.screen() instanceof NotificationPositionScreen) {
					client.gui.setScreen(null);
				} else if (client.gui.screen() == null && client.player != null) {
					client.gui.setScreen(new CompanionScreen(settings, notificationRenderer));
				}
			}
		});
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath("skyblock-girlfriend", "companion_notifications"),
				notifications::extractHud
		);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inventoryScreen) {
				conversationPanel.attach(inventoryScreen);
			}
			ScreenEvents.afterExtract(screen).register(notifications::extractScreen);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(companion::addRenderState);
	}
}