package gateway;

public class Main {

    public static void main(String[] args) {
        scenario1UrgentWithTelegram();
        scenario2EncryptedWithEmail();
        scenario3ScheduledBatchWithSms();
        scenario4RuntimeSwitching();
        scenario5FallbackToSms();
        scenario6MarketingWithPush();
    }

    private static void scenario1UrgentWithTelegram() {
        header("1. UrgentNotification + Telegram");
        TelegramMessageSender telegram = new TelegramMessageSender();
        UrgentNotification notification = new UrgentNotification(telegram);

        notification.send("alice", "Server CPU at 99%");

        printResult(notification);
        printDeliveries(telegram);
    }

    private static void scenario2EncryptedWithEmail() {
        header("2. EncryptedNotification + Email");
        EmailMessageSender email = new EmailMessageSender();
        EncryptedNotification notification = new EncryptedNotification(email);
        String plain = "Your one-time code is 482913";

        notification.send("bob@example.com", plain);

        printResult(notification);
        printDeliveries(email);
        String cipher = notification.encrypt(plain);
        System.out.println("Decrypted check: " + notification.decrypt(cipher));
    }

    private static void scenario3ScheduledBatchWithSms() {
        header("3. ScheduledBatchNotification + SMS");
        SmsMessageSender sms = new SmsMessageSender();
        ScheduledBatchNotification notification = new ScheduledBatchNotification(sms);
        for (int i = 1; i <= 10; i++) {
            notification.addRecipient(String.format("+7700000%04d", i));
        }

        ScheduledBatchNotification.BatchReport report =
                notification.sendBatch("Maintenance tonight at 23:00");

        System.out.println(report.summary());

        System.out.println("\n-- Batch with an over-limit SMS (all recipients fail) --");
        ScheduledBatchNotification tooLong = new ScheduledBatchNotification(
                new SmsMessageSender(), java.util.List.of("+77000000001", "+77000000002"));
        System.out.println(tooLong.sendBatch("x".repeat(200)).summary());
    }

    private static void scenario4RuntimeSwitching() {
        header("4. Runtime switching: Telegram -> Email");
        TelegramMessageSender telegram = new TelegramMessageSender();
        EmailMessageSender email = new EmailMessageSender();
        Notification notification = new UrgentNotification(telegram);

        notification.send("carol@example.com", "Deployment finished");
        notification.setSender(email);
        notification.send("carol@example.com", "Deployment finished");

        printDeliveries(telegram);
        printDeliveries(email);
    }

    private static void scenario5FallbackToSms() {
        header("5. Telegram unavailable -> fallback to SMS");
        TelegramMessageSender telegram = new TelegramMessageSender();
        SmsMessageSender sms = new SmsMessageSender();
        telegram.setAvailable(false);
        UrgentNotification notification = new UrgentNotification(telegram, sms);

        notification.send("+77001234567", "Database failover triggered");

        printResult(notification);
        System.out.println("Delivered via: " + notification.getLastUsedChannel());
        printDeliveries(telegram);
        printDeliveries(sms);
    }

    private static void scenario6MarketingWithPush() {
        header("6. MarketingNotification + PushNotificationSender");
        PushNotificationSender push = new PushNotificationSender();
        MarketingNotification notification = new MarketingNotification(push);

        notification.send("device-42", "20% off all plans this week");

        printResult(notification);
        printDeliveries(push);

        System.out.println("\n-- Opted-out recipient is skipped --");
        notification.optOut("device-43");
        notification.send("device-43", "20% off all plans this week");
        printResult(notification);
        System.out.println("Total push deliveries: " + push.getDeliveries().size());
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(title);
        System.out.println("==================================================");
    }

    private static void printResult(Notification notification) {
        System.out.println("Last send successful: " + notification.wasLastSendSuccessful());
    }

    private static void printDeliveries(SimulatedSender sender) {
        System.out.println("-- " + sender.getChannelName() + " deliveries ("
                + sender.getDeliveries().size() + ") --");
        sender.getDeliveries().forEach(System.out::println);
    }
}