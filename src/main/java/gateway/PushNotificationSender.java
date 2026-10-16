package gateway;

public class PushNotificationSender extends SimulatedSender implements MessageSender {

    public static final int MAX_BODY_LENGTH = 256;

    public PushNotificationSender() {
        super("Push");
    }

    @Override
    protected String buildPayload(String recipient, String title, String body) {
        if (body.length() > MAX_BODY_LENGTH) {
            return null;
        }
        return "PUSH device=" + recipient + " title=" + title + " body=" + body;
    }
}