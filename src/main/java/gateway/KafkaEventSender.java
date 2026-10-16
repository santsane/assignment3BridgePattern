package gateway;

public class KafkaEventSender extends SimulatedSender {

    public static final String TOPIC = "notifications";

    public KafkaEventSender() {
        super("Kafka");
    }

    @Override
    protected String buildPayload(String recipient, String title, String body) {
        long offset = deliveryCount();
        return "topic=" + TOPIC
                + " partition=0"
                + " offset=" + offset
                + " key=" + recipient
                + " value={\"title\":\"" + escape(title) + "\",\"body\":\"" + escape(body) + "\"}";
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}