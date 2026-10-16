package gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationGatewayTest {

    private TelegramMessageSender telegram;
    private EmailMessageSender email;
    private SmsMessageSender sms;
    private KafkaEventSender kafka;
    private PushNotificationSender push;

    @BeforeEach
    void setUp() {
        telegram = new TelegramMessageSender();
        email = new EmailMessageSender();
        sms = new SmsMessageSender();
        kafka = new KafkaEventSender();
        push = new PushNotificationSender();
    }

    /** Custom sender that is not one of the built-in providers; fails for recipients starting with "bad". */
    private static class FlakySender implements MessageSender {
        private final boolean available;
        private int accepted;

        FlakySender(boolean available) {
            this.available = available;
        }

        @Override
        public boolean sendMessage(String recipient, String title, String body) {
            if (recipient == null || recipient.startsWith("bad")) {
                return false;
            }
            accepted++;
            return true;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getChannelName() {
            return "Flaky";
        }
    }

    @Test
    void urgentNotificationIsDeliveredThroughTelegramWithUrgentPrefix() {
        UrgentNotification notification = new UrgentNotification(telegram);

        notification.send("alice", "Server down");

        assertTrue(notification.wasLastSendSuccessful());
        assertEquals(1, telegram.getDeliveries().size());
        String delivered = telegram.getDeliveries().get(0);
        assertTrue(delivered.contains("[URGENT] Server down"));
        assertTrue(delivered.contains("*" + UrgentNotification.TITLE + "*"));
        assertEquals("Telegram", notification.getLastUsedChannel());
    }

    @Test
    void emailSenderAddsSubjectAndSignature() {
        UrgentNotification notification = new UrgentNotification(email);

        notification.send("bob@example.com", "Disk almost full");

        String delivered = email.getDeliveries().get(0);
        assertTrue(delivered.contains("To: bob@example.com"));
        assertTrue(delivered.contains("Subject: " + UrgentNotification.TITLE));
        assertTrue(delivered.contains(EmailMessageSender.SIGNATURE));
    }

    @Test
    void unavailableProviderWithoutFallbackFailsAndDeliversNothing() {
        telegram.setAvailable(false);
        UrgentNotification notification = new UrgentNotification(telegram);

        notification.send("alice", "Anyone there?");

        assertFalse(notification.wasLastSendSuccessful());
        assertNull(notification.getLastUsedChannel());
        assertTrue(telegram.getDeliveries().isEmpty());
    }

    @Test
    void urgentNotificationFallsBackToSmsWhenTelegramIsUnavailable() {
        telegram.setAvailable(false);
        UrgentNotification notification = new UrgentNotification(telegram, sms);

        notification.send("+77001234567", "Database failover");

        assertTrue(notification.wasLastSendSuccessful());
        assertEquals("SMS", notification.getLastUsedChannel());
        assertTrue(telegram.getDeliveries().isEmpty());
        assertEquals(1, sms.getDeliveries().size());
        assertTrue(sms.getDeliveries().get(0).contains("[URGENT] Database failover"));
    }

    @Test
    void fallbackIsNotUsedWhenPrimaryIsAvailable() {
        UrgentNotification notification = new UrgentNotification(telegram, sms);

        notification.send("alice", "All good");

        assertEquals(1, telegram.getDeliveries().size());
        assertTrue(sms.getDeliveries().isEmpty());
    }

    @Test
    void fallbackChainSkipsEveryUnavailableProviderAndWorksWithCustomSender() {
        telegram.setAvailable(false);
        sms.setAvailable(false);
        FlakySender custom = new FlakySender(true);
        UrgentNotification notification = new UrgentNotification(telegram, sms);
        notification.addFallback(custom);

        notification.send("alice", "Chain test");

        assertTrue(notification.wasLastSendSuccessful());
        assertEquals("Flaky", notification.getLastUsedChannel());
        assertEquals(1, custom.accepted);
        assertEquals(2, notification.getFallbacks().size());
    }

    @Test
    void allProvidersUnavailableMakesUrgentNotificationFail() {
        telegram.setAvailable(false);
        sms.setAvailable(false);
        UrgentNotification notification = new UrgentNotification(telegram, sms);

        notification.send("alice", "Nobody can hear this");

        assertFalse(notification.wasLastSendSuccessful());
        assertNull(notification.getLastUsedChannel());
    }

    @Test
    void smsRejectsMessagesLongerThan160Characters() {
        assertTrue(sms.sendMessage("+77001234567", "", "x".repeat(160)));
        assertFalse(sms.sendMessage("+77001234567", "", "x".repeat(161)));
        assertEquals(1, sms.getDeliveries().size());
        assertEquals(160, sms.getMaxLength());
    }

    @Test
    void smsLimitIsConfigurable() {
        SmsMessageSender shortSms = new SmsMessageSender(120);

        assertTrue(shortSms.sendMessage("+77001234567", "", "y".repeat(120)));
        assertFalse(shortSms.sendMessage("+77001234567", "", "y".repeat(121)));
        assertThrows(IllegalArgumentException.class, () -> new SmsMessageSender(0));
    }

    @Test
    void urgentNotificationOverSmsFailsWhenPrefixPushesMessageOverLimit() {
        UrgentNotification notification = new UrgentNotification(sms);

        notification.send("+77001234567", "z".repeat(160));

        assertFalse(notification.wasLastSendSuccessful());
        assertTrue(sms.getDeliveries().isEmpty());
    }

    @Test
    void urgentNotificationFallsBackWhenPrimarySmsRejectsLongMessage() {
        UrgentNotification notification = new UrgentNotification(sms, email);

        notification.send("user@example.com", "z".repeat(200));

        assertTrue(notification.wasLastSendSuccessful());
        assertEquals("Email", notification.getLastUsedChannel());
        assertTrue(sms.getDeliveries().isEmpty());
    }

    @Test
    void encryptedNotificationTransformsMessageBeforeSending() {
        EncryptedNotification notification = new EncryptedNotification(email);

        notification.send("bob@example.com", "secret plan");

        assertTrue(notification.wasLastSendSuccessful());
        String delivered = email.getDeliveries().get(0);
        assertFalse(delivered.contains("secret plan"));
        assertTrue(delivered.contains(notification.encrypt("secret plan")));
        assertTrue(delivered.contains(EmailMessageSender.SIGNATURE));
    }

    @Test
    void encryptionIsReversibleAndKeyDependent() {
        EncryptedNotification first = new EncryptedNotification(email, "key-one");
        EncryptedNotification second = new EncryptedNotification(email, "key-two");
        String original = "Привет, world! 123";

        String cipher = first.encrypt(original);

        assertNotEquals(original, cipher);
        assertEquals(original, first.decrypt(cipher));
        assertNotEquals(cipher, second.encrypt(original));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedNotification(email, ""));
    }

    @Test
    void encryptedNotificationFailsWhenProviderIsUnavailable() {
        email.setAvailable(false);
        EncryptedNotification notification = new EncryptedNotification(email);

        notification.send("bob@example.com", "secret plan");

        assertFalse(notification.wasLastSendSuccessful());
        assertTrue(email.getDeliveries().isEmpty());
    }

    @Test
    void batchNotificationReportsTotalSuccessfulAndFailed() {
        FlakySender flaky = new FlakySender(true);
        ScheduledBatchNotification batch = new ScheduledBatchNotification(
                flaky, List.of("ok1", "ok2", "bad1", "ok3"));

        ScheduledBatchNotification.BatchReport report = batch.sendBatch("Hello everyone");

        assertEquals(4, report.total());
        assertEquals(3, report.successful());
        assertEquals(1, report.failed());
        assertEquals("Total: 4\nSuccessful: 3\nFailed: 1", report.summary());
        assertFalse(batch.wasLastSendSuccessful());
        assertSame(report, batch.getLastReport());
    }

    @Test
    void batchNotificationDeliversToEveryRecipientOverSms() {
        ScheduledBatchNotification batch = new ScheduledBatchNotification(sms);
        batch.addRecipient("+77000000001");
        batch.addRecipient("+77000000002");
        batch.addRecipient("+77000000003");

        ScheduledBatchNotification.BatchReport report = batch.sendBatch("Meeting at 10:00");

        assertEquals(3, report.successful());
        assertEquals(0, report.failed());
        assertEquals(3, sms.getDeliveries().size());
        assertTrue(batch.wasLastSendSuccessful());
    }

    @Test
    void batchNotificationFailsAllRecipientsWhenSmsTextIsTooLong() {
        ScheduledBatchNotification batch = new ScheduledBatchNotification(
                sms, List.of("+77000000001", "+77000000002"));

        ScheduledBatchNotification.BatchReport report = batch.sendBatch("m".repeat(300));

        assertEquals(2, report.total());
        assertEquals(0, report.successful());
        assertEquals(2, report.failed());
    }

    @Test
    void batchNotificationFailsEverythingWhenProviderIsUnavailable() {
        sms.setAvailable(false);
        ScheduledBatchNotification batch = new ScheduledBatchNotification(
                sms, List.of("+77000000001", "+77000000002"));

        ScheduledBatchNotification.BatchReport report = batch.sendBatch("Ping");

        assertEquals(0, report.successful());
        assertEquals(2, report.failed());
        assertFalse(batch.wasLastSendSuccessful());
    }

    @Test
    void batchSendIncludesTheExtraRecipientAndRejectsBlankRecipients() {
        ScheduledBatchNotification batch = new ScheduledBatchNotification(
                sms, List.of("+77000000001"));

        batch.send("+77000000009", "Hi");

        assertEquals(2, batch.getLastReport().total());
        assertEquals(2, sms.getDeliveries().size());
        assertThrows(IllegalArgumentException.class, () -> batch.addRecipient(" "));
        assertThrows(IllegalArgumentException.class, () -> batch.addRecipient(null));
    }

    @Test
    void emptyBatchIsNotConsideredSuccessful() {
        ScheduledBatchNotification batch = new ScheduledBatchNotification(sms);

        ScheduledBatchNotification.BatchReport report = batch.sendBatch("Nobody to notify");

        assertEquals(0, report.total());
        assertFalse(batch.wasLastSendSuccessful());
    }

    @Test
    void sameNotificationObjectCanSwitchProviderAtRuntime() {
        Notification notification = new UrgentNotification(telegram);

        notification.send("carol", "First via Telegram");
        notification.setSender(email);
        notification.send("carol", "Second via Email");

        assertEquals(1, telegram.getDeliveries().size());
        assertEquals(1, email.getDeliveries().size());
        assertTrue(telegram.getDeliveries().get(0).contains("First via Telegram"));
        assertTrue(email.getDeliveries().get(0).contains("Second via Email"));
        assertSame(email, notification.getSender());
    }

    @Test
    void settingNullSenderIsRejectedAndKeepsPreviousSender() {
        Notification notification = new EncryptedNotification(telegram);

        assertThrows(NullPointerException.class, () -> notification.setSender(null));
        assertSame(telegram, notification.getSender());
        assertThrows(NullPointerException.class, () -> new UrgentNotification(null));
    }

    @Test
    void newPushSenderWorksWithAllExistingNotificationTypes() {
        new UrgentNotification(push).send("device-1", "Urgent push");
        new EncryptedNotification(push).send("device-2", "Encrypted push");
        ScheduledBatchNotification batch = new ScheduledBatchNotification(
                push, List.of("device-3", "device-4"));
        batch.sendBatch("Batch push");

        assertEquals(5, push.getDeliveries().size());
        assertTrue(push.getDeliveries().get(0).contains("[URGENT] Urgent push"));
        assertEquals("Push", push.getChannelName());
        assertEquals(2, batch.getLastReport().successful());
    }

    @Test
    void pushSenderRejectsOversizedBodyAndBlankRecipient() {
        assertFalse(push.sendMessage("device-1", "t", "b".repeat(PushNotificationSender.MAX_BODY_LENGTH + 1)));
        assertFalse(push.sendMessage("  ", "t", "body"));
        assertTrue(push.sendMessage("device-1", "t", "b".repeat(PushNotificationSender.MAX_BODY_LENGTH)));
    }

    @Test
    void newMarketingNotificationWorksWithEveryExistingSender() {
        List<SimulatedSender> senders = List.of(telegram, email, sms, kafka, push);

        for (SimulatedSender s : senders) {
            MarketingNotification marketing = new MarketingNotification(s);
            marketing.send("user-1", "Sale");
            assertTrue(marketing.wasLastSendSuccessful(), s.getChannelName());
        }

        assertTrue(email.getDeliveries().get(0).contains(MarketingNotification.TAG + " Sale"));
        assertTrue(email.getDeliveries().get(0).contains(MarketingNotification.FOOTER));
        assertTrue(push.getDeliveries().get(0).contains(MarketingNotification.TAG));
    }

    @Test
    void marketingNotificationCanSwitchToPushAtRuntime() {
        MarketingNotification marketing = new MarketingNotification(email);

        marketing.send("user@example.com", "Promo A");
        marketing.setSender(push);
        marketing.send("device-7", "Promo B");

        assertEquals(1, email.getDeliveries().size());
        assertEquals(1, push.getDeliveries().size());
    }

    @Test
    void marketingNotificationSkipsOptedOutRecipients() {
        MarketingNotification marketing = new MarketingNotification(push);
        marketing.optOut("device-9");

        marketing.send("device-9", "Do not disturb");

        assertTrue(marketing.isOptedOut("device-9"));
        assertFalse(marketing.wasLastSendSuccessful());
        assertTrue(push.getDeliveries().isEmpty());
    }

    @Test
    void marketingNotificationFailsWhenKafkaIsUnavailable() {
        kafka.setAvailable(false);
        MarketingNotification marketing = new MarketingNotification(kafka);

        marketing.send("user-1", "Promo");

        assertFalse(marketing.wasLastSendSuccessful());
        assertTrue(kafka.getDeliveries().isEmpty());
    }

    @Test
    void kafkaSenderPublishesEventsWithIncreasingOffsets() {
        UrgentNotification notification = new UrgentNotification(kafka);

        notification.send("order-service", "First \"event\"");
        notification.send("order-service", "Second event");

        List<String> events = kafka.getDeliveries();
        assertEquals(2, events.size());
        assertTrue(events.get(0).contains("topic=" + KafkaEventSender.TOPIC));
        assertTrue(events.get(0).contains("offset=0"));
        assertTrue(events.get(1).contains("offset=1"));
        assertTrue(events.get(0).contains("\\\"event\\\""));
        assertEquals("Kafka", kafka.getChannelName());
    }

    @Test
    void sendersRejectInvalidRecipientsAndBodies() {
        List<SimulatedSender> senders = List.of(telegram, email, sms, kafka, push);

        for (SimulatedSender s : senders) {
            assertFalse(s.sendMessage(null, "t", "body"), s.getChannelName());
            assertFalse(s.sendMessage("", "t", "body"), s.getChannelName());
            assertFalse(s.sendMessage("user", "t", null), s.getChannelName());
            assertTrue(s.getDeliveries().isEmpty(), s.getChannelName());
        }
    }

    @Test
    void channelNamesAreDistinctAndAvailabilityCanBeToggled() {
        List<MessageSender> senders = List.of(telegram, email, sms, kafka, push);

        long distinct = senders.stream().map(MessageSender::getChannelName).distinct().count();
        assertEquals(senders.size(), distinct);

        telegram.setAvailable(false);
        assertFalse(telegram.isAvailable());
        assertFalse(telegram.sendMessage("alice", "t", "body"));
        telegram.setAvailable(true);
        assertTrue(telegram.sendMessage("alice", "t", "body"));
    }
}