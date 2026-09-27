package com.sherry.schedule.privatearea;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class PrivateCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;

    private PrivateCrypto() { }

    public static String encrypt(String plainText) throws GeneralSecurityException {
        SecretKey key = PrivateSession.requireKey();
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(
                (plainText == null ? "" : plainText).getBytes(StandardCharsets.UTF_8));
        ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
        payload.put(iv).put(encrypted);
        return Base64.encodeToString(payload.array(), Base64.NO_WRAP);
    }

    public static String decrypt(String encoded) throws GeneralSecurityException {
        SecretKey key = PrivateSession.requireKey();
        byte[] payload;
        try {
            payload = Base64.decode(encoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException failure) {
            throw new GeneralSecurityException("Invalid encrypted value", failure);
        }
        if (payload.length <= IV_LENGTH) throw new GeneralSecurityException("Invalid encrypted value");
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
        System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
