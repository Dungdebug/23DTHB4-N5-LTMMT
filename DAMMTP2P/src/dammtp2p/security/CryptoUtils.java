package dammtp2p.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Production-ready cryptographic utilities for DAMMTP2P:
 * <ul>
 *   <li>PBKDF2-HMAC-SHA256 password hashing with unique per-user salts.</li>
 *   <li>AES-128-GCM (128-bit key, 12-byte IV, 128-bit auth tag) authenticated encryption.</li>
 *   <li>Deterministic chunk-level nonce derivation for resumable file transfers.</li>
 * </ul>
 */
public final class CryptoUtils {

    public static final int AES_KEY_SIZE_BITS = 128;
    public static final int AES_KEY_SIZE_BYTES = 16;
    public static final int GCM_IV_SIZE_BYTES = 12;
    public static final int GCM_TAG_SIZE_BITS = 128;

    public static final int PBKDF2_ITERATIONS = 10_000;
    public static final int PBKDF2_KEY_LENGTH = 256;
    public static final int SALT_SIZE_BYTES = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        // Prevent instantiation
    }

    // =========================================================================
    //  PASSWORD HASHING (PBKDF2WithHmacSHA256)
    // =========================================================================

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] hashPassword(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 algorithm unavailable in JRE", e);
        }
    }

    public static boolean verifyPassword(char[] password, byte[] salt, byte[] expectedHash) {
        byte[] actualHash = hashPassword(password, salt);
        return MessageDigest.isEqual(actualHash, expectedHash);
    }

    // =========================================================================
    //  AES-128 KEY GENERATION & ENCODING
    // =========================================================================

    public static SecretKey generateAes128Key() {
        byte[] keyBytes = new byte[AES_KEY_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static SecretKey deriveRoomKey(String roomId) {
        String id = (roomId != null && !roomId.trim().isEmpty()) ? roomId.trim().toLowerCase() : "general";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(("DAMMTP2P_ROOM_SECRET_SALT_" + id).getBytes(StandardCharsets.UTF_8));
            byte[] key16 = Arrays.copyOf(hash, AES_KEY_SIZE_BYTES);
            return new SecretKeySpec(key16, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static SecretKey keyFromBytes(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != AES_KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("AES key must be exactly 16 bytes (128 bits)");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static SecretKey keyFromBase64(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key);
        return keyFromBytes(bytes);
    }

    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static byte[] generateGcmIv() {
        byte[] iv = new byte[GCM_IV_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    // =========================================================================
    //  AES-128-GCM TEXT & BYTE ENCRYPTION
    // =========================================================================

    public static byte[] encryptGcm(byte[] plaintext, SecretKey key, byte[] iv) throws GeneralSecurityException {
        if (iv.length != GCM_IV_SIZE_BYTES) {
            throw new IllegalArgumentException("GCM IV must be exactly 12 bytes");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptGcm(byte[] ciphertextWithTag, SecretKey key, byte[] iv) throws GeneralSecurityException {
        if (iv.length != GCM_IV_SIZE_BYTES) {
            throw new IllegalArgumentException("GCM IV must be exactly 12 bytes");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertextWithTag);
    }

    public static EncryptedPayload encryptString(String plaintext, SecretKey key) throws GeneralSecurityException {
        byte[] iv = generateGcmIv();
        byte[] cipherBytes = encryptGcm(plaintext.getBytes(StandardCharsets.UTF_8), key, iv);
        return new EncryptedPayload(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(cipherBytes)
        );
    }

    public static String decryptString(String base64Iv, String base64Cipher, SecretKey key) throws GeneralSecurityException {
        byte[] iv = Base64.getDecoder().decode(base64Iv);
        byte[] cipherBytes = Base64.getDecoder().decode(base64Cipher);
        byte[] plainBytes = decryptGcm(cipherBytes, key, iv);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // =========================================================================
    //  CHUNK-LEVEL DETERMINISTIC IV DERIVATION (FOR LARGE RESUMABLE FILES)
    // =========================================================================

    /**
     * Derives a unique 12-byte IV for a specific chunk index from the base transfer IV.
     * Guarantees NIST SP 800-38D compliance by preventing nonce reuse across chunks.
     *
     * @param baseIv     12-byte base IV negotiated at transfer initiation
     * @param chunkIndex 64-bit zero-based sequential chunk index
     * @return 12-byte unique chunk IV
     */
    public static byte[] deriveChunkIv(byte[] baseIv, long chunkIndex) {
        if (baseIv == null || baseIv.length != GCM_IV_SIZE_BYTES) {
            throw new IllegalArgumentException("Base IV must be 12 bytes");
        }
        byte[] chunkIv = Arrays.copyOf(baseIv, GCM_IV_SIZE_BYTES);
        long idx = chunkIndex;
        // XOR the last 8 bytes with chunkIndex
        for (int i = GCM_IV_SIZE_BYTES - 1; i >= 4; i--) {
            chunkIv[i] ^= (byte) (idx & 0xFF);
            idx >>= 8;
        }
        return chunkIv;
    }

    public static byte[] encryptChunk(byte[] plaintext, int offset, int length, SecretKey key, byte[] baseIv, long chunkIndex)
            throws GeneralSecurityException {
        byte[] chunkIv = deriveChunkIv(baseIv, chunkIndex);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, chunkIv));
        return cipher.doFinal(plaintext, offset, length);
    }

    public static byte[] decryptChunk(byte[] ciphertext, SecretKey key, byte[] baseIv, long chunkIndex)
            throws GeneralSecurityException {
        byte[] chunkIv = deriveChunkIv(baseIv, chunkIndex);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, chunkIv));
        return cipher.doFinal(ciphertext);
    }

    // =========================================================================
    //  HEX UTILITIES
    // =========================================================================

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * DTO containing Base64 IV and Ciphertext.
     */
    public static class EncryptedPayload {
        private final String iv;
        private final String cipher;

        public EncryptedPayload(String iv, String cipher) {
            this.iv = iv;
            this.cipher = cipher;
        }

        public String getIv() {
            return iv;
        }

        public String getCipher() {
            return cipher;
        }
    }
}