package gateway;

public class EmailMessageSender extends SimulatedSender {

    public static final String SIGNATURE = "-- \nNotification Gateway Team";

    public EmailMessageSender() {
        super("Email");
    }

    @Override
    protected String buildPayload(String recipient, String title, String body) {
        String subject = title.isBlank() ? "(no subject)" : title;
        return "To: " + recipient + "\n"
                + "Subject: " + subject + "\n\n"
                + body + "\n\n"
                + SIGNATURE;
    }
}