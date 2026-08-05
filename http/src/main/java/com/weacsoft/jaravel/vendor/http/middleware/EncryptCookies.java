package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import com.weacsoft.jaravel.vendor.core.crypto.AppKey;
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
 * <p><b>密钥兜底</b>：未覆盖 {@link #encryptionKey()} 时（即仍返回出厂默认值
 * {@link #DEFAULT_ENCRYPTION_KEY}），框架会自动回退到 core 模块的全局应用密钥
 * {@code jaravel.key}，遵循「模块自身配置优先 → core 全局密钥兜底」。
 * 因此只要在 application 配置里设置过 {@code jaravel.key}（{@code artisan key:generate} 生成），
 * Cookie 加密就不会再使用弱默认密钥。
 *
 * <p><b>安全提示</b>：默认密钥仅用于演示，生产环境请配置 {@code jaravel.key}
 * 或覆盖 {@link #encryptionKey()} 指定安全密钥（建议 32 字节）。
 */
public class EncryptCookies implements Middleware {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    /**
     * 模块出厂默认加密密钥。
     * <p>
     * 仅作为「子类是否覆盖过 {@link #encryptionKey()}」的判定基准：
     * 若实际值仍等于此常量，说明没有自定义密钥，框架回退到全局 {@code jaravel.key}。
     */
    public static final String DEFAULT_ENCRYPTION_KEY = "default-encryption-key-32bytes";

    @Override
    public Response handle(Request request, NextFunction next, String... params) {
        decryptCookies(request);
        Response response = next.apply(request);
        encryptCookies(response);
        return response;
    }

    /**
     * 加密密钥，子类可覆盖以指定安全密钥。
     * <p>
     * 保持默认实现时返回 {@link #DEFAULT_ENCRYPTION_KEY}，实际加解密会由
     * {@link #resolveEncryptionKey()} 回退到全局应用密钥 {@code jaravel.key}。
     *
     * @return 加密密钥，默认为出厂默认值
     */
    protected String encryptionKey() {
        return DEFAULT_ENCRYPTION_KEY;
    }

    /**
     * 解析实际生效的加密密钥：「子类覆盖优先 → core 全局密钥兜底」。
     * <p>
     * 中间件由路由层反射实例化而非 Spring 托管，因此这里通过 core 的
     * {@link SpringContext#beanOrNull(Class)} 安全获取 {@link AppKey}：
     * 容器未初始化或未引入 core 自动装配时返回 {@code null}，
     * 此时保持 {@link #encryptionKey()} 的返回值，行为与旧版本一致。
     *
     * @return 最终用于 AES 加解密的密钥
     */
    protected String resolveEncryptionKey() {
        String moduleKey = encryptionKey();
        AppKey appKey = SpringContext.beanOrNull(AppKey.class);
        if (appKey != null) {
            return appKey.resolve(moduleKey, DEFAULT_ENCRYPTION_KEY);
        }
        return moduleKey;
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
        byte[] keyBytes = resolveEncryptionKey().getBytes(StandardCharsets.UTF_8);
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
