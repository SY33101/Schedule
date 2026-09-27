package com.sherry.schedule.privatearea;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PrivatePasswordManager {
    private static final String PREFS = "private_area_security";
    private static final String SALT = "salt";
    private static final String VERIFIER = "verifier";
    private static final String FAILURES = "failures";
    private static final String LOCKED_UNTIL = "locked_until";
    private static final int ITERATIONS = 180_000;
    private static final int DERIVED_BITS = 512;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PrivatePasswordManager() { }

    public static boolean isConfigured(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.contains(SALT) && prefs.contains(VERIFIER);
    }

    public static void setPassword(Context context, char[] password)
            throws GeneralSecurityException {
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt);
        byte[] key = Arrays.copyOfRange(derived, 0, 32);
        byte[] verifier = Arrays.copyOfRange(derived, 32, 64);
        prefs(context).edit()
                .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
                .remove(FAILURES).remove(LOCKED_UNTIL).apply();
        PrivateSession.unlock(key);
        Arrays.fill(key, (byte) 0);
        Arrays.fill(verifier, (byte) 0);
        Arrays.fill(derived, (byte) 0);
    }

    public static boolean verify(Context context, char[] password)
            throws GeneralSecurityException {
        SharedPreferences prefs = prefs(context);
        if (!isConfigured(context)) return false;
        long now = System.currentTimeMillis();
        if (prefs.getLong(LOCKED_UNTIL, 0L) > now) return false;
        byte[] salt = Base64.decode(prefs.getString(SALT, ""), Base64.NO_WRAP);
        byte[] expected = Base64.decode(prefs.getString(VERIFIER, ""), Base64.NO_WRAP);
        byte[] derived = derive(password, salt);
        byte[] actual = Arrays.copyOfRange(derived, 32, 64);
        boolean matches = MessageDigest.isEqual(expected, actual);
        Arrays.fill(actual, (byte) 0);
        if (matches) {
            byte[] key = Arrays.copyOfRange(derived, 0, 32);
            PrivateSession.unlock(key);
            Arrays.fill(key, (byte) 0);
            prefs.edit().remove(FAILURES).remove(LOCKED_UNTIL).apply();
        } else {
            int failures = prefs.getInt(FAILURES, 0) + 1;
            SharedPreferences.Editor editor = prefs.edit().putInt(FAILURES, failures);
            if (failures >= 5) {
                int exponent = Math.min(4, failures - 5);
                long delay = 30_000L * (1L << exponent);
                editor.putLong(LOCKED_UNTIL, now + delay);
            }
            editor.apply();
        }
        Arrays.fill(derived, (byte) 0);
        return matches;
    }

    public static long remainingLockMillis(Context context) {
        return Math.max(0L, prefs(context).getLong(LOCKED_UNTIL, 0L)
                - System.currentTimeMillis());
    }

    private static byte[] derive(char[] password, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, DERIVED_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
