package gateway;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ScheduledBatchNotification extends Notification {

    public static final String TITLE = "Scheduled notification";

    private final Set<String> recipients = new LinkedHashSet<>();
    private BatchReport lastReport = new BatchReport(0, 0, 0);

    public ScheduledBatchNotification(MessageSender sender) {
        super(sender);
    }

    public ScheduledBatchNotification(MessageSender sender, Collection<String> recipients) {
        super(sender);
        if (recipients != null) {
            recipients.forEach(this::addRecipient);
        }
    }

    public void addRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        recipients.add(recipient);
    }

    public Set<String> getRecipients() {
        return Collections.unmodifiableSet(recipients);
    }

    public BatchReport getLastReport() {
        return lastReport;
    }

    @Override
    public void send(String recipient, String message) {
        Set<String> targets = new LinkedHashSet<>(recipients);
        if (recipient != null && !recipient.isBlank()) {
            targets.add(recipient);
        }
        deliverToAll(targets, message);
    }

    public BatchReport sendBatch(String message) {
        return deliverToAll(recipients, message);
    }

    private BatchReport deliverToAll(Collection<String> targets, String message) {
        int successful = 0;
        int failed = 0;
        for (String target : targets) {
            boolean ok = sender.isAvailable()
                    && sender.sendMessage(target, TITLE, safe(message));
            if (ok) {
                successful++;
            } else {
                failed++;
            }
        }
        lastReport = new BatchReport(targets.size(), successful, failed);
        recordResult(lastReport.total() > 0 && lastReport.failed() == 0);
        return lastReport;
    }

    public record BatchReport(int total, int successful, int failed) {

        public String summary() {
            return "Total: " + total + "\n"
                    + "Successful: " + successful + "\n"
                    + "Failed: " + failed;
        }

        @Override
        public String toString() {
            return summary();
        }
    }
}