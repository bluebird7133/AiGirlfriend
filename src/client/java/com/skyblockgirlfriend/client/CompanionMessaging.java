package com.skyblockgirlfriend.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CompanionMessaging {
	private static final long VISIBLE_NANOS = 5_000_000_000L;
	private static final long FADE_NANOS = 800_000_000L;

	private final Map<ChatType, List<Message>> histories = new EnumMap<>(ChatType.class);
	private final ArrayDeque<Message> notificationQueue = new ArrayDeque<>();
	private Message activeNotification;
	private long activeNotificationStarted;
	private boolean notificationsSuppressed;

	public CompanionMessaging() {
		for (ChatType chatType : ChatType.values()) {
			histories.put(chatType, new ArrayList<>());
		}
	}

	public void addMessage(Sender sender, String text) {
		addMessage(ChatType.AI, sender, text, true);
	}

	public void addMessage(ChatType chatType, Sender sender, String text) {
		addMessage(chatType, sender, text, true);
	}

	public void addPlayerMessage(String text) {
		addPlayerMessage(ChatType.AI, text);
	}

	public void addPlayerMessage(ChatType chatType, String text) {
		addMessage(chatType, Sender.PLAYER, text, false);
	}

	private void addMessage(ChatType chatType, Sender sender, String text, boolean notify) {
		if (text == null || text.isBlank()) {
			return;
		}

		Message message = new Message(Objects.requireNonNull(sender), text);
		histories.get(Objects.requireNonNull(chatType)).add(message);
		if (!notify || notificationsSuppressed) {
			return;
		}
		if (activeNotification == null) {
			activate(message, System.nanoTime());
		} else {
			notificationQueue.addLast(message);
		}
	}

	public List<Message> history() {
		return history(ChatType.AI);
	}

	public List<Message> history(ChatType chatType) {
		return List.copyOf(histories.get(Objects.requireNonNull(chatType)));
	}

	public int messageCount() {
		return messageCount(ChatType.AI);
	}

	public int messageCount(ChatType chatType) {
		return histories.get(Objects.requireNonNull(chatType)).size();
	}

	public Message messageAt(int index) {
		return messageAt(ChatType.AI, index);
	}

	public Message messageAt(ChatType chatType, int index) {
		return histories.get(Objects.requireNonNull(chatType)).get(index);
	}

	public void setNotificationsSuppressed(boolean suppressed) {
		if (notificationsSuppressed == suppressed) {
			return;
		}
		notificationsSuppressed = suppressed;
		if (suppressed) {
			activeNotification = null;
			notificationQueue.clear();
		}
	}

	public ActiveNotification activeNotification(long now) {
		if (notificationsSuppressed) {
			return null;
		}
		while (activeNotification != null
				&& now - activeNotificationStarted >= VISIBLE_NANOS + FADE_NANOS) {
			activeNotification = notificationQueue.pollFirst();
			if (activeNotification != null) {
				activeNotificationStarted = now;
			}
		}
		if (activeNotification == null) {
			return null;
		}

		long elapsed = now - activeNotificationStarted;
		float opacity = elapsed <= VISIBLE_NANOS
				? 1.0F
				: 1.0F - (float) (elapsed - VISIBLE_NANOS) / FADE_NANOS;
		return new ActiveNotification(activeNotification, Math.max(0.0F, opacity));
	}

	private void activate(Message message, long now) {
		activeNotification = message;
		activeNotificationStarted = now;
	}

	public enum Sender {
		COMPANION,
		PLAYER
	}

	public enum ChatType {
		AI,
		MATH
	}

	public record Message(Sender sender, String text) {
	}

	public record ActiveNotification(Message message, float opacity) {
	}
}
