package com.skyblockgirlfriend.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompanionMessaging {
	private static final long VISIBLE_NANOS = 5_000_000_000L;
	private static final long FADE_NANOS = 800_000_000L;

	private final List<Message> history = new ArrayList<>();
	private final ArrayDeque<Message> notificationQueue = new ArrayDeque<>();
	private Message activeNotification;
	private long activeNotificationStarted;
	private boolean notificationsSuppressed;

	public void addMessage(Sender sender, String text) {
		addMessage(sender, text, true);
	}

	public void addPlayerMessage(String text) {
		addMessage(Sender.PLAYER, text, false);
	}

	private void addMessage(Sender sender, String text, boolean notify) {
		if (text == null || text.isBlank()) {
			return;
		}

		Message message = new Message(Objects.requireNonNull(sender), text);
		history.add(message);
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
		return List.copyOf(history);
	}

	public int messageCount() {
		return history.size();
	}

	public Message messageAt(int index) {
		return history.get(index);
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

	public record Message(Sender sender, String text) {
	}

	public record ActiveNotification(Message message, float opacity) {
	}
}
