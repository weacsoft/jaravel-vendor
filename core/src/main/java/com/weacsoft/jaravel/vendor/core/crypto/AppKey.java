package com.weacsoft.jaravel.vendor.core.crypto;

/**
 * 统一应用密钥接口，对齐 Laravel 的 {@code APP_KEY}。
 * <p>
 * 框架各加密模块（captcha / jwt / http cookies 等）在加密时，按
 * 「模块自身配置优先 → core 全局密钥兜底」的顺序解析最终使用的密钥：
 * <ul>
 *   <li>模块自身显式配置了密钥（即与模块出厂默认值不同）→ 使用模块密钥；</li>
 *   <li>否则 → 使用 {@link #getKey()} 返回的全局应用密钥（Base64 编码的随机串）。</li>
 * </ul>
 * 这样业务方只需在 application 配置中设置一次 {@code jaravel.key}，
 * 所有模块即可共享同一把主密钥，避免每个模块各自维护一套弱默认密钥。
 *
 * <h3>使用示例（模块侧）</h3>
 * <pre>{@code
 * // 模块已有的出厂默认密钥
 * private static final String DEFAULT = "my-module-default-key";
 * // 解析：模块自己配了就用模块的，否则回退到全局 APP_KEY
 * String effective = appKey.resolve(moduleProps.getSecret(), DEFAULT);
 * }</pre>
 */
public interface AppKey {

    /**
     * 返回全局应用密钥（Base64 编码的随机串）。
     * <p>
     * 该密钥用于兜底：当模块自身未配置专用密钥时，所有加密统一回退到此密钥。
     *
     * @return 全局应用密钥（非空）
     */
    String getKey();

    /**
     * 按「模块自身配置优先 → 全局密钥兜底」解析最终使用的密钥。
     *
     * @param moduleKey        模块自身配置的密钥（可能未配置，等于模块出厂默认值）
     * @param moduleDefaultKey 模块的出厂默认密钥（用于判断用户是否覆盖了默认值）
     * @return 解析后的最终密钥（非空）
     */
    default String resolve(String moduleKey, String moduleDefaultKey) {
        if (moduleKey != null && !moduleKey.isEmpty() && !moduleKey.equals(moduleDefaultKey)) {
            return moduleKey;
        }
        return getKey();
    }
}
