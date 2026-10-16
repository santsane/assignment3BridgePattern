package gateway;

public class TelegramMessageSender extends SimulatedSender {

    public TelegramMessageSender() {
        super("Telegram");
    }

    @Override
    protected String buildPayload(String recipient, String title, String body) {
        String formatted = title.isBlank() ? body : "*" + title + "*\n" + body;
        return "[Telegram -> " + recipient + "] " + formatted;
    }
}