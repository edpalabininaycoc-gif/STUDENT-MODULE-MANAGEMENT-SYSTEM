package moduledistributionsystem.data;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing (built into Java, no extra library needed).
 *
 * Why: passwords must never be stored as readable text. A hash is a one-way scramble: from the stored
 * value nobody can read the password back, but the same password always produces a value that can be
 * checked. A random "salt" is added so that two users with the same password get different stored
 * values.
 *
 * A stored value looks like  pbkdf2$120000$<salt>$<hash>  so the number of iterations can be raised
 * later without breaking older accounts. Values that do not start with "pbkdf2$" are treated as plain
 * text, which only happens for the in-memory demo accounts used when no database is connected.
 *
 * How to customize: raise ITERATIONS to make guessing slower (old accounts keep working, because their
 * own iteration count is stored inside their value).
 */
public final class PasswordHasher {
    /** Marks a stored value as a PBKDF2 hash. */
    private static final String PREFIX = "pbkdf2$";
    /** How many times the scramble is repeated. Higher = safer but slower to log in. */
    private static final int ITERATIONS = 120_000;
    /** Length of the random salt in bytes (16 bytes = 128 bits). */
    private static final int SALT_BYTES = 16;
    /** Length of the resulting hash in bits. */
    private static final int HASH_BITS = 256;
    /** Cryptographically strong random generator used to make salts. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Utility class: it only has static methods, so nobody should create an instance. */
    private PasswordHasher() { }

    /** True if the stored text is already a PBKDF2 hash (it starts with "pbkdf2$"). */
    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** Returns the hashed form of a plain password. Already-hashed values are returned untouched. */
    public static String hash(String plain) {
        if (isHashed(plain)) return plain;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] h = derive(plain, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(h);
    }

    /** True if the plain password matches the stored value (hashed or, for demo accounts, plain). */
    public static boolean matches(String plain, String stored) {
        if (plain == null || stored == null) return false;
        if (!isHashed(stored)) return stored.equals(plain);
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(plain, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Runs PBKDF2 (HMAC-SHA256) on a password with the given salt and iteration count and returns the
     * raw hash bytes.
     */
    private static byte[] derive(String plain, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plain.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this Java runtime", e);
        }
    }
}
