package gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UrgentNotification extends Notification {

    public static final String PREFIX = "[URGENT]";
    public static final String TITLE = "Urgent notification";

    private final List<MessageSender> fallbacks = new ArrayList<>();
    private String lastUsedChannel;

    public UrgentNotification(MessageSender primary, MessageSender... fallbackSenders) {
        super(primary);
        if (fallbackSenders != null) {
            for (MessageSender fallback : fallbackSenders) {
                addFallback(fallback);
            }
        }
    }

    public void addFallback(MessageSender fallback) {
        fallbacks.add(Objects.requireNonNull(fallback, "fallback must not be null"));
    }

    public List<MessageSender> getFallbacks() {
        return Collections.unmodifiableList(fallbacks);
    }

    public String getLastUsedChannel() {
        return lastUsedChannel;
    }

    @Override
    public void send(String recipient, String message) {
        String body = PREFIX + " " + safe(message);
        lastUsedChannel = null;
        boolean delivered = false;

        List<MessageSender> candidates = new ArrayList<>();
        candidates.add(sender);
        candidates.addAll(fallbacks);

        for (MessageSender candidate : candidates) {
            if (candidate.isAvailable() && candidate.sendMessage(recipient, TITLE, body)) {
                lastUsedChannel = candidate.getChannelName();
                delivered = true;
                break;
            }
        }
        recordResult(delivered);
    }
}