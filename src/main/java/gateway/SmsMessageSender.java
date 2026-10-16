package gateway;

public class SmsMessageSender extends SimulatedSender {

    public static final int DEFAULT_MAX_LENGTH = 160;

    private final int maxLength;

    public SmsMessageSender() {
        this(DEFAULT_MAX_LENGTH);
    }

    public SmsMessageSender(int maxLength) {
        super("SMS");
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    protected String buildPayload(String recipient, String title, String body) {
        String text = title.isBlank() ? body : title + ": " + body;
        if (text.length() > maxLength) {
            return null;
        }
        return "SMS to " + recipient + ": " + text;
    }
}