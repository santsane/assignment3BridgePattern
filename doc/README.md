# Multi-Channel Notification Gateway - Bridge Pattern

A Java 17 notification gateway built with the Bridge design pattern. Notification type* (what is sent and how it is prepared) are separated from message *providers* (how and where it is delivered). All providers are simulated.

## Project Structure

- `MessageSender` is the Implementor interface
- `SimulatedSender` is a helper base class for the simulated providers. It handles availability, validation and the delivery log, and leaves the channel-specific formatting to subclasses.
- `TelegramMessageSender`, `EmailMessageSender`, `SmsMessageSender`, `KafkaEventSender` and `PushNotificationSender` are the Concrete Implementors
- `Notification` is the Abstraction
- `UrgentNotification`, `EncryptedNotification`, `ScheduledBatchNotification` and `MarketingNotification` are the Refined Abstractions
- `Main` runs the 6 mandatory demo scenarios
- `NotificationGatewayTest` holds the JUnit 5 tests

## 1. What are the two independent dimensions

1. **Notification type (Abstraction side):** what is sent and how the message is prepared before sending. The types are Urgent (`[URGENT]` prefix, availability check, fallback), Encrypted (XOR + Base64), ScheduledBatch (many recipients plus a summary) and Marketing (promo tag, unsubscribe footer, opt-out list).
2. **Delivery provider (Implementation side):** how and where the message is delivered. The providers are Telegram (formatted text), Email (subject and signature), SMS (160-character limit), Kafka (simulated event publishing) and Push (device payload).

Either dimension can grow or change without touching the other.

## 2. Where exactly is the Bridge

The Bridge is the field in the abstract class `Notification`:

`protected MessageSender sender;`

This is the has-a link between the Abstraction hierarchy (`Notification` and its subclasses) and the Implementor hierarchy (`MessageSender` and its implementations). Notification classes never name a concrete provider. They only call `sendMessage(...)`, `isAvailable()` and `getChannelName()` through the interface. Because the reference is held in a field, it can be replaced at runtime with `setSender(...)`.

## 3. Why is inheritance problematic here

With inheritance, every combination of type and provider needs its own class:

`UrgentTelegramNotification`, `UrgentEmailNotification`, `UrgentSmsNotification`, `EncryptedTelegramNotification`, and so on.

- N notification types * M providers gives **N * M classes**. With 4 types and 5 providers that is 20 classes, against 4 + 5 = 9 with Bridge.
- The logic is duplicated. The `[URGENT]` handling or the encryption would be copied into every provider-specific subclass.
- A change in one place, such as the SMS limit, has to be repeated in every SMS combination.
- The provider is fixed at compile time. `UrgentTelegramNotification` cannot switch to Email at runtime without creating a new object of a different class.
- A fallback to another provider would need type checks such as `instanceof TelegramMessageSender`, which breaks the abstraction.

## 4. What happens when a new provider is added

`PushNotificationSender` was added as an extension. One new class that implements `MessageSender` (through `SimulatedSender`).

- `Notification`, `UrgentNotification`, `EncryptedNotification`, `ScheduledBatchNotification` and `MarketingNotification` are not modified
- Every existing notification type works with it immediately. This is demonstrated by the Marketing + Push demo scenario and by the Push tests with Urgent, Encrypted and Batch notifications.
- Adding something like `WhatsAppMessageSender` follows same path
- Changing a provider rule such as the SMS limit only touches that provider: `new SmsMessageSender(120)`

## 5. What happens when a new Notification type is added

`MarketingNotification` was added as an extension. It is one new class that extends `Notification`.

- `MessageSender` and all concrete senders (Telegram, Email, SMS, Kafka, Push) are not modified.
- Works with every existing provider - This is demonstrated by the Marketing + Push demo scenario and by the test that runs Marketing over all five senders
- Adding something like `SilentNotification` follows the path of one subclass and no changes to any provider.

The abstraction and the implementation change independently. A new provider costs 1 class and a new notification type costs 1 class, instead of N or M new combination classes each time

## 6. Why is this Bridge rather than Adapter

| Aspect | Bridge (this project) | Adapter |
|---|---|---|
| Intent | Designed up front so two hierarchies can vary independently | Makes an existing, incompatible interface fit the one a client expects |
| When applied | At design time, before the parts exist | After the fact, when the parts already exist and don't match |
| Structure | Two hierarchies: Abstraction -> Refined Abstractions, and Implementor -> Concrete Implementors | Usually one wrapper around one adaptee |
| Here | `Notification` hierarchy and `MessageSender` hierarchy, both ours and both extensible | No third-party API with an incompatible interface is being wrapped |

The `MessageSender` interface was designed together with the notification classes, and both sides are expected to grow. The goal is to avoid the N × M class explosion and to allow runtime provider switching.

## Pattern Roles

| Role | Class |
|---|---|
| Abstraction | `Notification` |
| Refined Abstractions | `UrgentNotification`, `EncryptedNotification`, `ScheduledBatchNotification`, `MarketingNotification` |
| Implementor | `MessageSender` |
| Concrete Implementors | `TelegramMessageSender`, `EmailMessageSender`, `SmsMessageSender`, `KafkaEventSender`, `PushNotificationSender` |
| Bridge | `Notification.sender` (`Notification` has a `MessageSender`) |

## Mandatory Demo Scenarios (`Main`)

1. UrgentNotification + Telegram
2. EncryptedNotification + Email
3. ScheduledBatchNotification + SMS
4. Runtime switching: Telegram -> Email
5. Telegram unavailable -> fallback to SMS
6. MarketingNotification + PushNotificationSender

## Build and Run

Requires Java 17+ and JUnit 5 (Jupiter)

- Run the demo: `gateway.Main`
- Run the tests: `gateway.NotificationGatewayTest`

## UML

The class diagram is in `doc/document.pdf`