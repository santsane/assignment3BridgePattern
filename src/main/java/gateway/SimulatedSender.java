package gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class SimulatedSender implements MessageSender {

    private final String channelName;
    private final List<String> deliveries = new ArrayList<>();
    private boolean available = true;

    protected SimulatedSender(String channelName) {
        this.channelName = Objects.requireNonNull(channelName, "channelName must not be null");
    }

    @Override
    public final boolean sendMessage(String recipient, String title, String body) {
        if (!available) {
            return false;
        }
        if (recipient == null || recipient.isBlank() || body == null) {
            return false;
        }
        String safeTitle = title == null ? "" : title;
        String payload = buildPayload(recipient, safeTitle, body);
        if (payload == null) {
            return false;
        }
        deliveries.add(payload);
        return true;
    }

    protected abstract String buildPayload(String recipient, String title, String body);

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    public List<String> getDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    protected int deliveryCount() {
        return deliveries.size();
    }
}