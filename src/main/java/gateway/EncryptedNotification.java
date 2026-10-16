package gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptedNotification extends Notification {

    public static final String TITLE = "Encrypted notification";
    private static final String DEFAULT_KEY = "bridge-key";

    private final byte[] key;

    public EncryptedNotification(MessageSender sender) {
        this(sender, DEFAULT_KEY);
    }

    public EncryptedNotification(MessageSender sender, String key) {
        super(sender);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void send(String recipient, String message) {
        String encrypted = encrypt(safe(message));
        boolean delivered = sender.isAvailable()
                && sender.sendMessage(recipient, TITLE, encrypted);
        recordResult(delivered);
    }

    public String encrypt(String plainText) {
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(xor(data));
    }

    public String decrypt(String cipherText) {
        byte[] data = Base64.getDecoder().decode(cipherText);
        return new String(xor(data), StandardCharsets.UTF_8);
    }

    private byte[] xor(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
}