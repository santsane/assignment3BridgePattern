package gateway;

import java.util.Objects;

public abstract class Notification {

    protected MessageSender sender;
    private boolean lastSendSuccessful;

    protected Notification(MessageSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
    }

    public void setSender(MessageSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
    }

    public MessageSender getSender() {
        return sender;
    }

    public abstract void send(
            String recipient,
            String message
    );

    public boolean wasLastSendSuccessful() {
        return lastSendSuccessful;
    }

    protected void recordResult(boolean successful) {
        this.lastSendSuccessful = successful;
    }

    protected static String safe(String text) {
        return text == null ? "" : text;
    }
}