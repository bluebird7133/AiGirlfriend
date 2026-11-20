package com.skyblockgirlfriend.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;
import java.util.regex.Pattern;

public final class HypixelDeathDetector {
	private static final Pattern DEATH_MESSAGE_PATTERN = Pattern.compile(
			"^☠\\s+you\\s+\\S.*$",
			Pattern.CASE_INSENSITIVE
	);
	private static final long DEATH_COOLDOWN_NANOS = 5_000_000_000L;
	private static final long SKYBLOCK_CONTEXT_NANOS = 2_000_000_000L;

	private final CompanionMessaging messaging;
	private long lastSkyblockContext;
	private long lastDeath;
	private boolean hasSkyblockContext;
	private boolean hasDeath;
	private boolean wasDeathScreen;

	public HypixelDeathDetector(CompanionMessaging messaging) {
		this.messaging = messaging;
	}

	public void tick(Minecraft client) {
		if (isHypixelSkyBlock(client)) {
			lastSkyblockContext = System.nanoTime();
			hasSkyblockContext = true;
		}
		boolean deathScreen = client.gui.screen() instanceof DeathScreen;
		if (deathScreen && !wasDeathScreen && isHypixelServer(client)
				&& hasRecentSkyblockContext(client, System.nanoTime())) {
			recordDeath(System.nanoTime());
		}
		wasDeathScreen = deathScreen;
	}

	public void onSystemMessage(Minecraft client, Component message) {
		String text = stripFormatting(message.getString()).trim();
		if (!DEATH_MESSAGE_PATTERN.matcher(text).matches()) {
			return;
		}

		if (!isHypixelServer(client)) {
			return;
		}
		long now = System.nanoTime();
		if (hasRecentSkyblockContext(client, now)) {
			recordDeath(now);
		}
	}

	private void recordDeath(long now) {
		if (hasDeath && now - lastDeath < DEATH_COOLDOWN_NANOS) {
			return;
		}
		lastDeath = now;
		hasDeath = true;
		messaging.addMessage(CompanionMessaging.Sender.COMPANION, "HOW DID YOU DIE?!");
	}

	private boolean hasRecentSkyblockContext(Minecraft client, long now) {
		return isHypixelSkyBlock(client)
				|| (hasSkyblockContext && now - lastSkyblockContext <= SKYBLOCK_CONTEXT_NANOS);
	}

	private static boolean isHypixelSkyBlock(Minecraft client) {
		if (!isHypixelServer(client) || client.level == null) {
			return false;
		}
		Objective sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		return sidebar != null
				&& stripFormatting(sidebar.getDisplayName().getString()).toLowerCase(Locale.ROOT).contains("skyblock");
	}

	private static boolean isHypixelServer(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server == null || server.ip == null) {
			return false;
		}
		String host = server.ip.trim().toLowerCase(Locale.ROOT);
		int portSeparator = host.lastIndexOf(':');
		if (portSeparator >= 0 && host.indexOf(':') == portSeparator) {
			host = host.substring(0, portSeparator);
		}
		return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
	}

	private static String stripFormatting(String text) {
		return text.replaceAll("(?i)§[0-9A-FK-OR]", "");
	}
}
