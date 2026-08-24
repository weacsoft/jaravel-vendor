package com.weacsoft.jaravel.vendor.wechat.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 微信消息加解密（"消息加解密说明"标准实现，纯 JDK，无第三方依赖）。
 * <p>
 * 规范要点：
 * <ul>
 *   <li>AES 密钥 = {@code Base64.decode(EncodingAESKey + "=")}（43 位 EncodingAESKey 补 = 后 44 位标准 Base64 → 32 字节密钥）</li>
 *   <li>算法 {@code AES/ECB/NoPadding}（手动去除微信自定的 PKCS#7 填充）</li>
 *   <li>明文结构 = {@code random(16B) || msg_len(4B 大端) || msg || receiveid}</li>
 *   <li>签名 = {@code sha1( sort( token, timestamp, nonce, encrypt ).join("") )}</li>
 * </ul>
 *
 * 本类不可变，可跨线程共享（内部 Cipher 每次调用新建，避免共享状态）。
 *
 * @author weacsoft
 */
public final class WxBizMsgCrypt {

    private static final int RANDOM_LENGTH = 16;

    private final String token;
    private final String appId;
    private final byte[] aesKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param token          公众号开发者后台的 Token
     * @param encodingAesKey 43 位 EncodingAESKey
     * @param appId          公众号 AppID（解密后校验 receiveid 用）
     * @throws WechatCryptoException EncodingAESKey 非法时
     */
    public WxBizMsgCrypt(String token, String encodingAesKey, String appId) {
        if (token == null || token.isEmpty()) {
            throw new WechatCryptoException("token 不能为空");
        }
        if (appId == null || appId.isEmpty()) {
            throw new WechatCryptoException("appId 不能为空");
        }
        if (encodingAesKey != null) {
            if (encodingAesKey.length() != 43) {
                throw new WechatCryptoException("EncodingAESKey 长度必须为 43（当前 " + encodingAesKey.length() + "）");
            }
            try {
                this.aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
            } catch (IllegalArgumentException e) {
                throw new WechatCryptoException("EncodingAESKey 不是合法的 Base64", e);
            }
        } else {
            this.aesKey = null;
        }
        this.token = token;
        this.appId = appId;
    }

    /**
     * 是否具备加解密能力（EncodingAESKey 已配置）。
     *
     * @return true 表示可加解密
     */
    public boolean isCryptEnabled() {
        return aesKey != null;
    }

    /**
     * 计算消息签名：sha1(sort(token, timestamp, nonce, encrypt))。
     *
     * @param timestamp 时间戳字符串
     * @param nonce     随机串
     * @param encrypt   密文 Base64（推送/回复的 Encrypt 值）
     * @return 32 位小写十六进制签名
     */
    public String sign(String timestamp, String nonce, String encrypt) {
        String[] parts = {token, timestamp, nonce, encrypt};
        Arrays.sort(parts);
        String joined = String.join("", parts);
        return sha1Hex(joined);
    }

    /**
     * 校验推送请求签名（{@code msg_signature} 参数）。
     *
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param encrypt      密文 Base64
     * @param msgSignature 请求携带的签名
     * @return 签名匹配返回 true
     */
    public boolean verifySignature(String timestamp, String nonce, String encrypt, String msgSignature) {
        String expected = sign(timestamp, nonce, encrypt);
        return expected != null && expected.equalsIgnoreCase(msgSignature);
    }

    /**
     * 解密推送的 Encrypt 字段，校验末尾 receiveid == appId。
     *
     * @param encrypted Encrypt Base64
     * @return 明文消息（XML 或 JSON）
     * @throws WechatCryptoException 密文非法、长度异常或 receiveid 不匹配时
     */
    public String decrypt(String encrypted) {
        requireCrypt();
        byte[] plainBytes;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
            plainBytes = stripPkcs7Padding(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (Exception e) {
            throw new WechatCryptoException("微信消息解密失败: " + e.getMessage(), e);
        }
        if (plainBytes.length <= RANDOM_LENGTH + 4) {
            throw new WechatCryptoException("明文长度异常（不足 random+len 头部）");
        }
        int msgLen = readBigEndianInt(plainBytes, RANDOM_LENGTH);
        int msgStart = RANDOM_LENGTH + 4;
        if (msgStart + msgLen > plainBytes.length) {
            throw new WechatCryptoException("明文长度声明超出实际: msgLen=" + msgLen);
        }
        String msg = new String(plainBytes, msgStart, msgLen, StandardCharsets.UTF_8);
        String receiveId = new String(plainBytes, msgStart + msgLen, plainBytes.length - msgStart - msgLen, StandardCharsets.UTF_8);
        if (!appId.equals(receiveId)) {
            throw new WechatCryptoException("receiveid 不匹配: 期望 " + appId + "，实际 " + receiveId);
        }
        return msg;
    }

    /**
     * 加密被动回复内容。
     * <p>
     * 微信规范：明文（random+msgLen+msg+receiveid）先做 PKCS#7（块大小 32）填充 →
     * AES-ECB 加密 → 密文<b>整体</b> Base64（密文长度保持块边界，解密时再从明文去填充）。
     *
     * @param replyMsg 回复明文（XML）
     * @return Base64 密文（供 Encrypt 字段与签名计算）
     * @throws WechatCryptoException 加密失败时
     */
    public String encrypt(String replyMsg) {
        requireCrypt();
        byte[] msgBytes = replyMsg.getBytes(StandardCharsets.UTF_8);
        byte[] appBytes = appId.getBytes(StandardCharsets.UTF_8);
        byte[] randomBytes = new byte[RANDOM_LENGTH];
        random.nextBytes(randomBytes);
        byte[] lenBytes = new byte[4];
        int len = msgBytes.length;
        lenBytes[0] = (byte) (len >>> 24);
        lenBytes[1] = (byte) (len >>> 16);
        lenBytes[2] = (byte) (len >>> 8);
        lenBytes[3] = (byte) len;

        byte[] plain = new byte[RANDOM_LENGTH + 4 + msgBytes.length + appBytes.length];
        int offset = 0;
        System.arraycopy(randomBytes, 0, plain, offset, RANDOM_LENGTH);
        offset += RANDOM_LENGTH;
        System.arraycopy(lenBytes, 0, plain, offset, 4);
        offset += 4;
        System.arraycopy(msgBytes, 0, plain, offset, msgBytes.length);
        offset += msgBytes.length;
        System.arraycopy(appBytes, 0, plain, offset, appBytes.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
            byte[] encrypted = cipher.doFinal(padPkcs7(plain));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new WechatCryptoException("微信消息加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * PKCS#7 填充到块边界（微信规范块大小 32）。
     */
    private static byte[] padPkcs7(byte[] data) {
        int blockSize = 32;
        int pad = blockSize - (data.length % blockSize);
        byte[] out = Arrays.copyOf(data, data.length + pad);
        Arrays.fill(out, data.length, out.length, (byte) pad);
        return out;
    }

    /**
     * 去除 PKCS#7 填充（尾字节的值即填充字节数，1~32；非法则原样返回交由上层校验）。
     */
    private static byte[] stripPkcs7Padding(byte[] data) {
        if (data.length == 0) {
            throw new WechatCryptoException("解密结果长度为 0");
        }
        int pad = data[data.length - 1] & 0xff;
        if (pad < 1 || pad > 32 || pad > data.length) {
            return data;
        }
        return Arrays.copyOf(data, data.length - pad);
    }

    private void requireCrypt() {
        if (aesKey == null) {
            throw new WechatCryptoException("未配置 EncodingAESKey，无法执行加解密");
        }
    }

    private static int readBigEndianInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new WechatCryptoException("SHA-1 不可用: " + e.getMessage(), e);
        }
    }
}
