package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cookie 加密中间件，对齐 Laravel 的 {@code EncryptCookies}。
 * <p>
 * 使用 AES/CBC/PKCS5Padding 加解密 Cookie。请求阶段解密入站 Cookie，响应阶段加密出站 Cookie。
 * <p>
 * <b>继承式配置</b>：通过覆盖 {@link #encryptionKey()} 和 {@link #except()} 方法自定义密钥与排除列表，
 * 而非通过构造器传参。预定义中间件不标注 {@code @MiddlewareAlias}，由使用者继承后自行标注。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @MiddlewareAlias
 * public class AppEncryptCookies extends EncryptCookies {
 *     @Override
 *     protected String encryptionKey() {
 *         return "my-super-secret-key-32bytes!";
 *     }
 *     @Override
 *     protected String[] except() {
 *         return new String[]{"XSRF-TOKEN"};
 *     }
 * }
 * }</pre>
 *
 * <p><b>安全提示</b>：默认密钥仅用于演示，生产环境必须覆盖 {@link #encryptionKey()} 指定安全密钥（建议 32 字节）。
 */
public class EncryptCookies implements Middleware {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        decryptCookies(request);
        Response response = next.apply(request);
        encryptCookies(response);
        return response;
    }

    /**
     * 加密密钥，子类可覆盖以指定安全密钥。
     *
     * @return 加密密钥，默认为演示用密钥
     */
    protected String encryptionKey() {
        return "default-encryption-key-32bytes";
    }

    /**
     * 不加密的 Cookie 名数组，子类可覆盖以自定义排除列表。
     *
     * @return 排除 Cookie 名数组，默认为空
     */
    protected String[] except() {
        return new String[0];
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
        byte[] keyBytes = encryptionKey().getBytes(StandardCharsets.UTF_8);
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
        return Arrays.asList(except()).contains(cookieName);
    }
}
