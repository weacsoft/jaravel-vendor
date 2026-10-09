package com.weacsoft.jaravel.vendor.http.middleware;

import com.weacsoft.jaravel.vendor.core.SpringContext;
import com.weacsoft.jaravel.vendor.core.crypto.AppKey;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cookie 加密中间件，对齐 Laravel 的 {@code EncryptCookies}。
 * <p>
 * 请求阶段解密入站 Cookie，响应阶段加密出站 Cookie；算法为
 * <b>AES-256-CBC + HMAC-SHA256（encrypt-then-MAC）</b>。
 *
 * <h3>Cookie 线格式（v2）</h3>
 * <pre>
 *   v2:Base64( IV(16B) || AES-CBC 密文 || HMAC-SHA256(IV || 密文)(32B) )
 * </pre>
 * 三个安全要点（对应历史缺陷）：
 * <ol>
 *   <li><b>失败即丢弃</b>：解密/验签失败时<b>移除</b>该 Cookie，绝不保留请求里带来的原值 ——
 *       否则客户端只要发一个非密文（如 {@code is_admin=1}）就被当成明文接受，加密形同虚设；</li>
 *   <li><b>随机 IV</b>：每次加密使用 {@link SecureRandom} 生成 IV（旧实现 IV 恒为全零 → 确定性密文）；</li>
 *   <li><b>完整性保护</b>：密文带 HMAC-SHA256 且<b>先验签后解密</b>，常量时间比较。
 *       仅有 CBC 而无 MAC 时，攻击者可在不知道密钥的情况下对密文做比特翻转来篡改 Cookie 内容。</li>
 * </ol>
 * 密钥由 {@code SHA-256(密钥材料)} 派生（旧实现是把 UTF-8 字节补零/截断到 32 字节），
 * 加密与 MAC 使用两个不同的派生密钥。
 * <p>
 * <b>升级注意</b>：带 {@code v2:} 前缀的才被接受，旧格式 Cookie 一律丢弃 → 升级后用户会掉一次登录态。
 * 若确需灰度兼容，可继承并覆盖 {@link #decrypt(String)} 自行放行旧格式（不建议）。
 *
 * <p><b>密钥兜底</b>：未覆盖 {@link #encryptionKey()} 时（即仍返回出厂默认值
 * {@link #DEFAULT_ENCRYPTION_KEY}），框架会自动回退到 core 模块的全局应用密钥
 * {@code jaravel.key}，遵循「模块自身配置优先 → core 全局密钥兜底」。
 *
 * <p><b>安全提示</b>：默认密钥仅用于演示，生产环境请配置 {@code jaravel.key}
 * 或覆盖 {@link #encryptionKey()}（建议 32 字节随机串）。
 */
public class EncryptCookies implements Middleware {

    private static final Logger logger = LoggerFactory.getLogger(EncryptCookies.class);

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 线格式版本前缀：无此前缀的 Cookie 视为非法（防「明文冒充密文」） */
    private static final String FORMAT_PREFIX = "v2:";

    /** MAC 派生密钥的用途标签（与加密密钥分离） */
    private static final String MAC_KEY_LABEL = "|jaravel-cookie-mac";

    private static final int IV_LENGTH = 16;
    private static final int MAC_LENGTH = 32;
    private static final int KEY_LENGTH = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     *
     * @return 最终用于派生 AES/HMAC 密钥的密钥材料
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

    /**
     * 解密入站 Cookie：<b>失败即移除</b>（不保留请求带来的原值）。
     *
     * @param request 当前请求
     */
    protected void decryptCookies(Request request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookieObjects();
        if (cookies == null) {
            return;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (isExcluded(cookie.getName())) {
                continue;
            }
            try {
                String decryptedValue = decrypt(cookie.getValue());
                request.replaceCookie(cookie.getName(), decryptedValue);
            } catch (Exception e) {
                // 关键：丢弃而不是保留原值 —— 否则攻击者发一个明文 Cookie 就会被当作有效值
                request.removeCookie(cookie.getName());
                logger.warn("[jaravel-cookie] Cookie '{}' 解密/验签失败，已丢弃: {}",
                        cookie.getName(), e.getMessage());
            }
        }
    }

    /**
     * 加密出站 Cookie：<b>原地替换</b>同名 Cookie 的值，避免「明文 + 密文」同名双下发。
     *
     * @param response 响应
     */
    protected void encryptCookies(Response response) {
        jakarta.servlet.http.Cookie[] cookies = response.getCookies();
        if (cookies == null) {
            return;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (isExcluded(cookie.getName())) {
                continue;
            }
            try {
                // getCookies() 返回的是数组副本，但元素仍是响应内部持有的同一 Cookie 对象，
                // 因此 setValue 会直接改变最终写出的 Set-Cookie，不会产生重复头。
                cookie.setValue(encrypt(cookie.getValue()));
            } catch (Exception e) {
                // 加密失败宁可不下发（置空），也不能把明文发出去
                logger.error("[jaravel-cookie] Cookie '{}' 加密失败，已置空以失败关闭: {}",
                        cookie.getName(), e.getMessage());
                cookie.setValue("");
            }
        }
    }

    /**
     * 加密：{@code v2:Base64(IV || 密文 || HMAC)}。
     *
     * @param value 明文
     * @return 线格式字符串
     * @throws Exception 加密异常
     */
    protected String encrypt(String value) throws Exception {
        byte[] iv = generateIv().getIV();
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        byte[] mac = hmac(iv, cipherText);
        byte[] combined = new byte[iv.length + cipherText.length + mac.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        System.arraycopy(mac, 0, combined, iv.length + cipherText.length, mac.length);
        return FORMAT_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密：先校验格式前缀与 HMAC（常量时间），再解密。
     *
     * @param encryptedValue 线格式字符串
     * @return 明文
     * @throws Exception 格式非法、验签失败或解密失败
     */
    protected String decrypt(String encryptedValue) throws Exception {
        if (encryptedValue == null || !encryptedValue.startsWith(FORMAT_PREFIX)) {
            throw new IllegalArgumentException("Cookie 不是 v2 密文格式（可能是明文伪造）");
        }
        byte[] combined = Base64.getDecoder()
                .decode(encryptedValue.substring(FORMAT_PREFIX.length()));
        if (combined.length <= IV_LENGTH + MAC_LENGTH) {
            throw new IllegalArgumentException("Cookie 密文长度非法");
        }
        int cipherLength = combined.length - IV_LENGTH - MAC_LENGTH;
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, IV_LENGTH + cipherLength);
        byte[] mac = Arrays.copyOfRange(combined, IV_LENGTH + cipherLength, combined.length);

        // 先验签（常量时间）再解密：CBC 无 MAC 时可被比特翻转篡改
        if (!MessageDigest.isEqual(hmac(iv, cipherText), mac)) {
            throw new SecurityException("Cookie HMAC 校验失败（内容被篡改或密钥不匹配）");
        }

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, generateKey(), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    /**
     * 派生 AES-256 密钥：{@code SHA-256(密钥材料)}（不再补零/截断）。
     *
     * @return AES 密钥
     */
    protected SecretKeySpec generateKey() {
        return new SecretKeySpec(sha256(resolveEncryptionKey()), KEY_ALGORITHM);
    }

    /**
     * 生成随机 IV（每次加密都不同，消除确定性密文）。
     *
     * @return IV
     */
    protected IvParameterSpec generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    protected boolean isExcluded(String cookieName) {
        return Arrays.asList(except()).contains(cookieName);
    }

    /**
     * 计算 {@code HMAC-SHA256(派生 MAC 密钥, iv || 密文)}。
     *
     * @param iv       IV
     * @param cipherText 密文
     * @return 32 字节 MAC
     * @throws Exception 算法不可用
     */
    private byte[] hmac(byte[] iv, byte[] cipherText) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(sha256(resolveEncryptionKey() + MAC_KEY_LABEL), HMAC_ALGORITHM));
        mac.update(iv);
        mac.update(cipherText);
        return mac.doFinal();
    }

    /**
     * SHA-256 摘要。
     *
     * @param input 输入
     * @return 32 字节摘要
     */
    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用: " + e.getMessage(), e);
        }
    }
}