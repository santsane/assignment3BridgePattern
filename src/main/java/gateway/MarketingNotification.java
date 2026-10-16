package gateway;

import java.util.HashSet;
import java.util.Set;

public class MarketingNotification extends Notification {

    public static final String TAG = "[PROMO]";
    public static final String TITLE = "Special offer";
    public static final String FOOTER = "Reply STOP to unsubscribe.";

    private final Set<String> optedOut = new HashSet<>();

    public MarketingNotification(MessageSender sender) {
        super(sender);
    }

    public void optOut(String recipient) {
        if (recipient != null) {
            optedOut.add(recipient);
        }
    }

    public boolean isOptedOut(String recipient) {
        return recipient != null && optedOut.contains(recipient);
    }

    @Override
    public void send(String recipient, String message) {
        if (isOptedOut(recipient)) {
            recordResult(false);
            return;
        }
        String body = TAG + " " + safe(message) + "\n\n" + FOOTER;
        boolean delivered = sender.isAvailable()
                && sender.sendMessage(recipient, TITLE, body);
        recordResult(delivered);
    }
}