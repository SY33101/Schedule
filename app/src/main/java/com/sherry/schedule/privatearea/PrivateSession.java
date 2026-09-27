package com.sherry.schedule.privatearea;

import android.os.SystemClock;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class PrivateSession {
    private static final long SESSION_MILLIS = 10L * 60L * 1000L;
    private static byte[] keyBytes;
    private static long expiresAt;

    private PrivateSession() { }

    public static synchronized void unlock(byte[] key) {
        lock();
        keyBytes = Arrays.copyOf(key, key.length);
        expiresAt = SystemClock.elapsedRealtime() + SESSION_MILLIS;
    }

    public static synchronized boolean isUnlocked() {
        if (keyBytes == null) return false;
        if (SystemClock.elapsedRealtime() >= expiresAt) {
            lock();
            return false;
        }
        return true;
    }

    public static synchronized SecretKey requireKey() throws GeneralSecurityException {
        if (!isUnlocked()) throw new GeneralSecurityException("Private session is locked");
        expiresAt = SystemClock.elapsedRealtime() + SESSION_MILLIS;
        return new SecretKeySpec(Arrays.copyOf(keyBytes, keyBytes.length), "AES");
    }

    public static synchronized void lock() {
        if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
        keyBytes = null;
        expiresAt = 0L;
    }
}
