package com.weacsoft.jaravel.middleware;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.middleware.Middleware;
import jakarta.servlet.http.Cookie;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class EncryptCookies implements Middleware {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    protected String encryptionKey;
    protected String[] except = new String[0];

    public EncryptCookies() {
        this.encryptionKey = "default-encryption-key-32bytes";
    }

    public EncryptCookies(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        decryptCookies(request);
        Response response = next.apply(request);
        encryptCookies(response);
        return response;
    }

    protected void decryptCookies(Request request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookieObjects();
        if (cookies == null) {
            return;
        }

        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (!isExcluded(cookie.getName())) {
                try {
                    String decryptedValue = decrypt(cookie.getValue());
                    request.replaceCookie(cookie.getName(), decryptedValue);
                } catch (Exception e) {
                    // If decryption fails, keep the original value
                }
            }
        }
    }

    protected void encryptCookies(Response response) {
        jakarta.servlet.http.Cookie[] cookies = response.getCookies();
        if (cookies == null) {
            return;
        }

        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (!isExcluded(cookie.getName())) {
                try {
                    String encryptedValue = encrypt(cookie.getValue());
                    jakarta.servlet.http.Cookie newCookie = new jakarta.servlet.http.Cookie(cookie.getName(), encryptedValue);
                    newCookie.setPath(cookie.getPath());
                    newCookie.setDomain(cookie.getDomain());
                    newCookie.setMaxAge(cookie.getMaxAge());
                    newCookie.setSecure(cookie.getSecure());
                    newCookie.setHttpOnly(cookie.isHttpOnly());
                    response.addCookie(newCookie);
                } catch (Exception e) {
                    // If encryption fails, keep the original value
                }
            }
        }
    }

    protected String encrypt(String value) throws Exception {
        SecretKeySpec keySpec = generateKey();
        IvParameterSpec ivSpec = generateIv();

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] iv = ivSpec.getIV();

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    protected String decrypt(String encryptedValue) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedValue);

        byte[] iv = new byte[16];
        byte[] encrypted = new byte[combined.length - 16];

        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = generateKey();
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    protected SecretKeySpec generateKey() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes32 = new byte[32];
        System.arraycopy(keyBytes, 0, keyBytes32, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(keyBytes32, KEY_ALGORITHM);
    }

    protected IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 0);
        return new IvParameterSpec(iv);
    }

    protected boolean isExcluded(String cookieName) {
        return Arrays.asList(except).contains(cookieName);
    }

    public void setExcept(String[] except) {
        this.except = except;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}
