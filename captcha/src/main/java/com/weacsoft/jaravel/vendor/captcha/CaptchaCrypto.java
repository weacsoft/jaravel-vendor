package com.weacsoft.jaravel.vendor.captcha;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 验证码数据加解密工具类 — 支持 NONE / AES / RSA 三种模式。
 * <p>
 * 所有模式的前后端交互均采用 Base64 编码：
 * <ul>
 *   <li><b>NONE</b> — 纯 Base64 编码，不加密（适用于开发调试或内网环境）</li>
 *   <li><b>AES</b> — AES/CBC/PKCS5Padding 加密后 Base64 编码（对称加密，前后端共享密钥）</li>
 *   <li><b>RSA</b> — RSA/ECB/OAEPWithSHA-256AndMGF1Padding 加密后 Base64 编码（非对称加密）</li>
 * </ul>
 * <p>
 * 核心层仅使用 JDK 内置加密库，不引入任何第三方依赖。
 * <p>
 * <h3>AES 模式</h3>
 * <ol>
 *   <li>对用户自定义密钥做 SHA-256 哈希，取前 16 字节作为 AES-128 密钥</li>
 *   <li>使用固定 IV（与密钥相同的前 16 字节）进行 AES/CBC/PKCS5Padding 加密</li>
 *   <li>将密文 Base64 编码后返回</li>
 * </ol>
 * <p>
 * <h3>RSA 模式</h3>
 * <ul>
 *   <li>使用 RSA-2048 密钥对，OAEPWithSHA-256AndMGF1Padding 填充</li>
 *   <li>前端使用公钥加密，后端使用私钥解密</li>
 *   <li>后端生成 captchaKey 时也使用公钥加密（前端无需解密 captchaKey，只需原样传回）</li>
 *   <li>支持自动分块：明文超过单块最大长度（190 字节）时自动分块加密，每块输出固定 256 字节</li>
 *   <li>可通过 {@link #generateKeyPair()} 生成新密钥对，通过 {@link #exportPublicKey(KeyPair)} /
 *       {@link #exportPrivateKey(KeyPair)} 导出 Base64 格式密钥</li>
 * </ul>
 * <p>
 * <h3>前端 JS 对应实现</h3>
 * 前端使用浏览器内置 Web Crypto API（crypto.subtle），无需引入第三方库。
 * 详见 jaravel-captcha.js 文件。
 */
public class CaptchaCrypto {

    /** 加密类型 */
    public enum Type {
        /** 不加密，仅 Base64 编码 */
        NONE,
        /** AES/CBC/PKCS5Padding 对称加密 */
        AES,
        /** RSA/ECB/OAEPWithSHA-256AndMGF1Padding 非对称加密 */
        RSA
    }

    // ==================== AES 常量 ====================
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    // ==================== RSA 常量 ====================
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int RSA_KEY_SIZE = 2048;
    /** RSA-2048 密文块长度（字节） */
    private static final int RSA_BLOCK_SIZE = 256;
    /** OAEPWithSHA-256 填充开销，明文最大块 = 256 - 2*32 - 2 = 190 字节 */
    private static final int RSA_MAX_PLAINTEXT_BLOCK = 190;

    // ==================== 加密状态 ====================
    private final Type type;

    /** AES 密钥与 IV */
    private final SecretKeySpec aesKeySpec;
    private final IvParameterSpec aesIvSpec;

    /** RSA 公钥与私钥（至少有一个非 null） */
    private final PublicKey rsaPublicKey;
    private final PrivateKey rsaPrivateKey;

    // ==================== 构造方法 ====================

    /**
     * NONE 模式构造。
     */
    public CaptchaCrypto() {
        this.type = Type.NONE;
        this.aesKeySpec = null;
        this.aesIvSpec = null;
        this.rsaPublicKey = null;
        this.rsaPrivateKey = null;
    }

    /**
     * AES 模式构造。
     *
     * @param key 用户自定义密钥字符串（会被 SHA-256 哈希后取前 16 字节）
     */
    public CaptchaCrypto(String key) {
        this.type = Type.AES;
        if (key == null || key.isBlank()) {
            key = "jaravel-captcha-default-key";
        }
        byte[] hash = sha256(key);
        byte[] keyBytes = new byte[16];
        System.arraycopy(hash, 0, keyBytes, 0, 16);
        this.aesKeySpec = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        this.aesIvSpec = new IvParameterSpec(keyBytes);
        this.rsaPublicKey = null;
        this.rsaPrivateKey = null;
    }

    /**
     * RSA 模式构造（从 Base64 编码的密钥字符串）。
     *
     * @param publicKeyBase64  Base64 编码的 X.509 公钥（前端加密用，可为 null）
     * @param privateKeyBase64 Base64 编码的 PKCS#8 私钥（后端解密用，可为 null）
     */
    public CaptchaCrypto(String publicKeyBase64, String privateKeyBase64) {
        this.type = Type.RSA;
        this.aesKeySpec = null;
        this.aesIvSpec = null;
        this.rsaPublicKey = publicKeyBase64 != null ? parsePublicKey(publicKeyBase64) : null;
        this.rsaPrivateKey = privateKeyBase64 != null ? parsePrivateKey(privateKeyBase64) : null;
    }

    /**
     * RSA 模式构造（从 KeyPair）。
     *
     * @param keyPair RSA 密钥对
     */
    public CaptchaCrypto(KeyPair keyPair) {
        this.type = Type.RSA;
        this.aesKeySpec = null;
        this.aesIvSpec = null;
        this.rsaPublicKey = keyPair != null ? keyPair.getPublic() : null;
        this.rsaPrivateKey = keyPair != null ? keyPair.getPrivate() : null;
    }

    // ==================== 工厂方法 ====================

    /**
     * 根据类型和密钥创建加密实例。
     *
     * @param type 加密类型（"none"、"aes"、"rsa"）
     * @param key  密钥（NONE 模式忽略；AES 模式为对称密钥字符串；RSA 模式为 Base64 公钥或 "pubKey|privKey" 格式）
     * @return CaptchaCrypto 实例
     */
    public static CaptchaCrypto create(String type, String key) {
        if (type == null) type = "none";
        switch (type.toLowerCase()) {
            case "aes":
                return new CaptchaCrypto(key);
            case "rsa":
                if (key != null && key.contains("|")) {
                    String[] parts = key.split("\\|", 2);
                    return new CaptchaCrypto(
                            parts[0].isEmpty() ? null : parts[0],
                            parts[1].isEmpty() ? null : parts[1]);
                }
                // 只有公钥（仅加密）或只有私钥（仅解密）
                return new CaptchaCrypto(key, null);
            default:
                return new CaptchaCrypto();
        }
    }

    /**
     * 生成新的 RSA-2048 密钥对。
     *
     * @return 密钥对
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * 导出公钥为 Base64 字符串。
     */
    public static String exportPublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded());
    }

    /**
     * 导出私钥为 Base64 字符串。
     */
    public static String exportPrivateKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(
                keyPair.getPrivate().getEncoded());
    }

    // ==================== 加密 / 解密 ====================

    /**
     * 加密明文，返回 Base64 编码的密文。
     *
     * @param plaintext 明文
     * @return Base64 密文，加密失败返回 null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            switch (type) {
                case NONE:
                    return Base64.getEncoder().encodeToString(
                            plaintext.getBytes(StandardCharsets.UTF_8));
                case AES:
                    return encryptAes(plaintext);
                case RSA:
                    return encryptRsa(plaintext);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密 Base64 编码的密文。
     *
     * @param ciphertext Base64 密文
     * @return 明文，解密失败返回 null
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }
        try {
            switch (type) {
                case NONE:
                    return new String(Base64.getDecoder().decode(ciphertext),
                            StandardCharsets.UTF_8);
                case AES:
                    return decryptAes(ciphertext);
                case RSA:
                    return decryptRsa(ciphertext);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 返回当前加密类型。
     */
    public Type getType() {
        return type;
    }

    /**
     * 是否已启用加密（非 NONE 模式）。
     */
    public boolean isEnabled() {
        return type != Type.NONE;
    }

    // ==================== AES 实现 ====================

    private String encryptAes(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, aesIvSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decryptAes(String ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, aesIvSpec);
        byte[] decoded = Base64.getDecoder().decode(ciphertext);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ==================== RSA 实现（支持分块） ====================

    private String encryptRsa(String plaintext) throws Exception {
        if (rsaPublicKey == null) {
            throw new IllegalStateException("RSA public key is required for encryption");
        }
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);

        // 单块直接加密
        if (data.length <= RSA_MAX_PLAINTEXT_BLOCK) {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encrypted = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encrypted);
        }

        // 分块加密
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(RSA_MAX_PLAINTEXT_BLOCK, data.length - offset);
            byte[] block = cipher.doFinal(data, offset, len);
            bos.write(block);
            offset += len;
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private String decryptRsa(String ciphertext) throws Exception {
        if (rsaPrivateKey == null) {
            throw new IllegalStateException("RSA private key is required for decryption");
        }
        byte[] data = Base64.getDecoder().decode(ciphertext);

        // 单块直接解密
        if (data.length <= RSA_BLOCK_SIZE) {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] decrypted = cipher.doFinal(data);
            return new String(decrypted, StandardCharsets.UTF_8);
        }

        // 分块解密
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(RSA_BLOCK_SIZE, data.length - offset);
            byte[] block = cipher.doFinal(data, offset, len);
            bos.write(block);
            offset += len;
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ==================== 工具方法 ====================

    /**
     * 对字符串做 SHA-256 哈希。
     */
    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 解析 Base64 编码的 X.509 公钥。
     */
    private static PublicKey parsePublicKey(String base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RSA public key", e);
        }
    }

    /**
     * 解析 Base64 编码的 PKCS#8 私钥。
     */
    private static PrivateKey parsePrivateKey(String base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RSA private key", e);
        }
    }
}
