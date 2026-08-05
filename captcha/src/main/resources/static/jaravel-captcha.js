/**
 * jaravel-captcha.js — 验证码前端库（OOP 风格）
 *
 * 包含两部分：
 *   1. JaravelCaptcha — 加解密工具（向后兼容，无第三方依赖）
 *   2. Captcha        — OOP 验证码组件（自动构建 UI、自动验证、轨迹采集）
 *
 * 支持 5 种验证码类型：number / arithmetic / slider / rotate / click
 * 支持 3 种加密模式：none（纯 Base64）/ aes（AES-CBC）/ rsa（RSA-OAEP）
 *
 * 用法：
 *   const captcha = Captcha.init('container-id', {
 *       type: 'slider',
 *       apiUrl: '/api/captcha/generate'
 *       // 加密参数可省略：不传时自动采用后端 generate 下发的 encType / encKey
 *   });
 *   captcha.on('complete', (key, captchaInput) => {
 *       // key 是「合并凭证」（格式 type.captchaKey，已包含类型信息）
 *       // 把 key 和 captchaInput 随登录表单等业务字段一起提交，
 *       // 服务端一次性校验，避免「先验证码后业务」两段式提交的时间窗漏洞
 *   });
 *   captcha.show();
 *   captcha.refresh();
 *   captcha.destroy();
 *
 * 当 Web Crypto API 不可用时，aes / rsa 会自动降级为 none（纯 Base64），
 * 验证码功能不受影响，仅在控制台输出警告。
 */

// ====================================================================
// Part 1: JaravelCaptcha — 加解密工具（向后兼容）
// ====================================================================

const JaravelCaptcha = (function () {

    // ========== Base64 工具 ==========

    function strToBase64(str) {
        const bytes = new TextEncoder().encode(str);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    function base64ToStr(b64) {
        const binary = atob(b64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return new TextDecoder().decode(bytes);
    }

    function base64ToBytes(b64) {
        const binary = atob(b64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes;
    }

    function bytesToBase64(bytes) {
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    // ========== SHA-256 ==========

    async function sha256(str) {
        const data = new TextEncoder().encode(str);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        return new Uint8Array(hashBuffer);
    }

    // ========== Web Crypto 可用性检测 ==========

    function isWebCryptoAvailable() {
        return !!(typeof window !== 'undefined'
            && window.crypto
            && window.crypto.subtle
            && typeof window.crypto.subtle.encrypt === 'function');
    }

    // ========== NONE 模式（纯 Base64，不依赖 Web Crypto） ==========

    async function encryptNone(plaintext) {
        return strToBase64(plaintext);
    }

    async function decryptNone(ciphertext) {
        return base64ToStr(ciphertext);
    }

    // ========== AES 模式 ==========
    // 后端: AES/CBC/PKCS5Padding, key = SHA-256(userKey)[0:16], IV = same 16 bytes

    async function encryptAes(plaintext, key) {
        const hash = await sha256(key || 'jaravel-captcha-default-key');
        const aesKeyBytes = hash.slice(0, 16); // AES-128
        const iv = aesKeyBytes; // IV = same as key (first 16 bytes of SHA-256)

        const cryptoKey = await crypto.subtle.importKey(
            'raw', aesKeyBytes, { name: 'AES-CBC' }, false, ['encrypt']
        );

        const data = new TextEncoder().encode(plaintext);
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-CBC', iv: iv }, cryptoKey, data
        );

        return bytesToBase64(new Uint8Array(encrypted));
    }

    async function decryptAes(ciphertext, key) {
        const hash = await sha256(key || 'jaravel-captcha-default-key');
        const aesKeyBytes = hash.slice(0, 16);
        const iv = aesKeyBytes;

        const cryptoKey = await crypto.subtle.importKey(
            'raw', aesKeyBytes, { name: 'AES-CBC' }, false, ['decrypt']
        );

        const data = base64ToBytes(ciphertext);
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-CBC', iv: iv }, cryptoKey, data
        );

        return new TextDecoder().decode(decrypted);
    }

    // ========== RSA 模式 ==========
    // 后端: RSA/ECB/OAEPWithSHA-256AndMGF1Padding, RSA-2048
    // 前端使用公钥加密，后端使用私钥解密
    // 支持分块：每块最大 190 字节明文，输出 256 字节密文

    const RSA_BLOCK_SIZE = 256; // RSA-2048 密文块大小
    const RSA_MAX_PLAIN = 190;  // OAEP-SHA256 最大明文块

    async function importRsaPublicKey(publicKeyBase64) {
        const keyData = base64ToBytes(publicKeyBase64);
        return await crypto.subtle.importKey(
            'spki', keyData,
            { name: 'RSA-OAEP', hash: 'SHA-256' },
            false, ['encrypt']
        );
    }

    async function importRsaPrivateKey(privateKeyBase64) {
        const keyData = base64ToBytes(privateKeyBase64);
        return await crypto.subtle.importKey(
            'pkcs8', keyData,
            { name: 'RSA-OAEP', hash: 'SHA-256' },
            false, ['decrypt']
        );
    }

    async function encryptRsa(plaintext, publicKeyBase64) {
        const cryptoKey = await importRsaPublicKey(publicKeyBase64);
        const data = new TextEncoder().encode(plaintext);

        if (data.length <= RSA_MAX_PLAIN) {
            const encrypted = await crypto.subtle.encrypt(
                { name: 'RSA-OAEP' }, cryptoKey, data
            );
            return bytesToBase64(new Uint8Array(encrypted));
        }

        // 分块加密
        const chunks = [];
        for (let offset = 0; offset < data.length; offset += RSA_MAX_PLAIN) {
            const end = Math.min(offset + RSA_MAX_PLAIN, data.length);
            const chunk = data.slice(offset, end);
            const encrypted = await crypto.subtle.encrypt(
                { name: 'RSA-OAEP' }, cryptoKey, chunk
            );
            chunks.push(new Uint8Array(encrypted));
        }

        const total = new Uint8Array(chunks.length * RSA_BLOCK_SIZE);
        chunks.forEach((chunk, i) => total.set(chunk, i * RSA_BLOCK_SIZE));
        return bytesToBase64(total);
    }

    async function decryptRsa(ciphertext, privateKeyBase64) {
        const cryptoKey = await importRsaPrivateKey(privateKeyBase64);
        const data = base64ToBytes(ciphertext);

        if (data.length <= RSA_BLOCK_SIZE) {
            const decrypted = await crypto.subtle.decrypt(
                { name: 'RSA-OAEP' }, cryptoKey, data
            );
            return new TextDecoder().decode(decrypted);
        }

        // 分块解密
        const chunks = [];
        for (let offset = 0; offset < data.length; offset += RSA_BLOCK_SIZE) {
            const end = Math.min(offset + RSA_BLOCK_SIZE, data.length);
            const chunk = data.slice(offset, end);
            const decrypted = await crypto.subtle.decrypt(
                { name: 'RSA-OAEP' }, cryptoKey, chunk
            );
            chunks.push(new Uint8Array(decrypted));
        }

        let totalLength = 0;
        chunks.forEach(chunk => totalLength += chunk.length);
        const total = new Uint8Array(totalLength);
        let offset = 0;
        chunks.forEach(chunk => { total.set(chunk, offset); offset += chunk.length; });
        return new TextDecoder().decode(total);
    }

    // ========== 统一接口 ==========

    /**
     * 加密数据
     * @param {string} plaintext - 明文
     * @param {string} type - 加密类型: 'none' | 'aes' | 'rsa'
     * @param {string} key - 密钥（AES 为对称密钥字符串，RSA 为 Base64 公钥，none 忽略）
     * @returns {Promise<string>} Base64 编码的密文
     */
    async function encrypt(plaintext, type, key) {
        type = (type || 'none').toLowerCase();
        switch (type) {
            case 'aes':
                return await encryptAes(plaintext, key);
            case 'rsa':
                return await encryptRsa(plaintext, key);
            default:
                return await encryptNone(plaintext);
        }
    }

    /**
     * 解密数据
     * @param {string} ciphertext - Base64 编码的密文
     * @param {string} type - 加密类型: 'none' | 'aes' | 'rsa'
     * @param {string} key - 密钥（AES 为对称密钥字符串，RSA 为 Base64 私钥，none 忽略）
     * @returns {Promise<string>} 明文
     */
    async function decrypt(ciphertext, type, key) {
        type = (type || 'none').toLowerCase();
        switch (type) {
            case 'aes':
                return await decryptAes(ciphertext, key);
            case 'rsa':
                return await decryptRsa(ciphertext, key);
            default:
                return await decryptNone(ciphertext);
        }
    }

    // ========== 验证码交互辅助（向后兼容） ==========

    /**
     * 生成验证码
     * @param {string} apiUrl - 生成验证码的 API 地址
     * @param {string} type - 验证码类型
     * @returns {Promise<Object>} { key, encType, encKey, type, captchaKey, imageBase64, expireTime, extra }
     */
    async function fetchCaptcha(apiUrl, type) {
        const url = apiUrl + '?type=' + encodeURIComponent(type || 'rotate');
        const resp = await fetch(url);
        const json = await resp.json();
        if (json.code !== 200) {
            throw new Error(json.msg || '生成验证码失败');
        }
        return json.data;
    }

    /**
     * 提交验证码验证。
     * <p>
     * 只需要「合并凭证 key」+「用户输入」两个参数：key 由 generate 接口下发
     * （格式 `type.captchaKey`），内部已包含验证码类型，无需再单独传 type。
     *
     * @param {string} apiUrl - 验证 API 地址
     * @param {string} key - 生成时返回的合并凭证 data.key
     * @param {string} input - 用户输入（明文）
     * @param {string} [encType] - 加密类型（不传则用 generate 下发的 encType）
     * @param {string} [encKey] - 加密密钥（不传则用 generate 下发的 encKey）
     * @returns {Promise<boolean>} 验证是否通过
     */
    async function submitCaptcha(apiUrl, key, input, encType, encKey) {
        const encryptedInput = await encrypt(input, encType, encKey);
        const resp = await fetch(apiUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                key: key,
                input: encryptedInput
            })
        });
        const json = await resp.json();
        return json.code === 200;
    }

    return {
        encrypt: encrypt,
        decrypt: decrypt,
        encryptNone: encryptNone,
        decryptNone: decryptNone,
        encryptAes: encryptAes,
        decryptAes: decryptAes,
        encryptRsa: encryptRsa,
        decryptRsa: decryptRsa,
        fetchCaptcha: fetchCaptcha,
        submitCaptcha: submitCaptcha,
        isWebCryptoAvailable: isWebCryptoAvailable,
        // 工具方法
        strToBase64: strToBase64,
        base64ToStr: base64ToStr,
        sha256: sha256
    };
})();


// ====================================================================
// Part 2: Captcha — OOP 验证码组件
// ====================================================================

class Captcha {

    // ========== 静态工厂方法 ==========

    /**
     * 初始化验证码组件
     * @param {string} containerId - 容器元素 ID
     * @param {Object} options - 配置选项
     * @returns {Captcha} Captcha 实例
     */
    static init(containerId, options) {
        return new Captcha(containerId, options);
    }

    // ========== 构造函数 ==========

    constructor(containerId, options) {
        this.containerId = containerId;
        this.container = document.getElementById(containerId);

        if (!this.container) {
            throw new Error('[Captcha] 找不到容器元素: #' + containerId);
        }

        // 合并默认选项
        this.options = this._mergeOptions(options);

        // 运行时状态
        /**
         * 合并后的单一凭证（后端 generate 下发的 data.key，格式 `type.captchaKey`）。
         * 校验时只需提交它 + 用户输入两个参数，可与登录表单等其他字段一次性提交，
         * 避免「先验证码、后业务」的两段式提交带来的时间窗漏洞。
         */
        this._key = null;
        /** @deprecated 兼容旧版：拆分后的 captchaKey，新代码请用 {@link _key} */
        this._captchaKey = null;
        this._captchaData = null;
        this._destroyed = false;
        this._refreshing = false;       // 刷新中标记，防止并发刷新
        this._completed = false;        // 用户已完成前端验证操作（点击N个文字/滑动/旋转完成），刷新时重置
        // 弹层模式默认隐藏，等待业务方调用 show() 弹出；内联模式立即可见
        this._visible = !this.options.modal;
        this._loaded = false;           // 是否已成功拉取过验证码（弹层模式下延迟到首次 show()）
        this._listeners = [];       // 所有托管的事件监听器
        this._clickPoints = [];     // click 类型的点击坐标
        this._eventListeners = {};  // 事件监听器 { event: [callback, ...] }
        this._stageBaseWidth = 0;   // 舞台原始像素宽度（= 后端图片宽度）
        this._stageScale = 1;       // 舞台当前缩放系数
        this._lastTouchTapAt = 0;   // 最近一次触摸点击时间，用于抑制幽灵 click
        this._autoCloseTimer = null;

        // 解析加密模式（含 Web Crypto 降级）
        this._resolveEncryption();

        // 注入共享样式
        Captcha._injectSharedStyles();

        // 保留容器原有内容
        this._preserveOriginalContent();

        // 构建 UI
        this._buildUI();

        // 绑定事件
        this._attachEvents();

        // 监听尺寸变化，保持舞台等比缩放（桌面窗口缩放 / 移动端旋屏）
        this._observeResize();

        // 加载验证码：内联模式立即加载；弹层模式延迟到首次 show()，避免未打开就消耗验证码
        if (!this.options.modal) {
            this._loadCaptcha();
        }
    }

    // ========== 选项合并 ==========

    /**
     * 合并配置选项。
     *
     * ## 配置权限边界（重要）
     *
     * 前端**只能**控制展示层，**不能**控制任何影响验证码强度的参数。
     *
     * | 类别 | 归属 | 示例 |
     * |------|------|------|
     * | 安全 / 校验参数 | **后端** `jaravel.captcha.*` | tolerance、noise、interfereLines、length、width、height、clickTargetCount、clickDecoyCount、expireSeconds |
     * | 场景选择 | 前端「选择」，后端「定义」 | `scene: 'login'`（必须在后端 `jaravel.captcha.scenes` 白名单内） |
     * | 展示层配置 | **前端** | modal、maxWidth、theme、文案、zIndex、遮罩行为 |
     *
     * 历史版本允许 `config: {tolerance: 999, clickTargetCount: 1}` 直接透传到后端，
     * 等于把验证码难度交给攻击者控制。现已移除：`config` 中除 `scene` 外的键一律忽略并告警。
     */
    _mergeOptions(options) {
        const opts = options || {};

        // ---- 兼容旧写法：从已废弃的 config 中仅提取 scene，其余键忽略并告警 ----
        let scene = opts.scene || null;
        if (opts.config && typeof opts.config === 'object') {
            const rejected = [];
            for (const key in opts.config) {
                if (!Object.prototype.hasOwnProperty.call(opts.config, key)) continue;
                if (key === 'scene') {
                    if (!scene) scene = opts.config.scene;
                } else {
                    rejected.push(key);
                }
            }
            if (rejected.length > 0) {
                console.warn(
                    '[Captcha] options.config 已废弃：' + rejected.join(', ') +
                    ' 属于后端安全参数，前端设置无效并已忽略。' +
                    '请改用后端 jaravel.captcha.* 配置，或用 scene 选择后端预声明的场景。'
                );
            }
        }

        return {
            type: (opts.type || 'number').toLowerCase(),
            apiUrl: opts.apiUrl || '/api/captcha/generate',
            encryptionType: (opts.encryptionType || 'none').toLowerCase(),
            encryptionKey: opts.encryptionKey || null,
            /**
             * 是否由业务方显式指定了加密参数。
             * <p>
             * 未显式指定时，组件会自动采用后端 generate 接口下发的
             * {@code encType} / {@code encKey}——这一点在启用了全局应用密钥
             * （{@code jaravel.key}）兜底时尤为重要：模块实际使用的密钥可能
             * 与前端硬编码的默认值不同，必须以后端下发的为准。
             */
            encryptionTypeExplicit: !!opts.encryptionType,
            encryptionKeyExplicit: !!opts.encryptionKey,

            // ---- 场景：前端唯一能影响后端生成的入参，且只能「选择」不能「设值」 ----
            scene: (typeof scene === 'string' && scene) ? scene : null,

            // ---- 展示层配置（纯前端，不参与任何后端请求）----
            /** 全屏弹层模式：点击 show() 后弹出全屏遮罩，验证框屏幕居中 */
            modal: opts.modal === true,
            /** 弹层标题（modal 模式） */
            modalTitle: opts.modalTitle || '安全验证',
            /** 是否显示右上角关闭按钮（modal 模式） */
            closable: opts.closable !== false,
            /** 点击遮罩是否关闭（modal 模式） */
            maskClosable: opts.maskClosable !== false,
            /** 按 ESC 是否关闭（modal 模式） */
            escClosable: opts.escClosable !== false,
            /** 弹层层级 */
            zIndex: (typeof opts.zIndex === 'number') ? opts.zIndex : 9999,
            /** 完成验证后是否自动关闭弹层（modal 模式），毫秒；0/false=不自动关闭 */
            autoCloseDelay: (opts.autoCloseDelay === false || opts.autoCloseDelay === 0)
                ? 0 : (typeof opts.autoCloseDelay === 'number' ? opts.autoCloseDelay : 600),
            /** 验证框最大展示宽度（px），实际展示尺寸随容器自适应缩放 */
            maxWidth: (typeof opts.maxWidth === 'number' && opts.maxWidth > 0) ? opts.maxWidth : 360,
            /** 数字/算术类型是否自动聚焦输入框（移动端建议关闭以免弹出键盘遮挡） */
            autoFocus: opts.autoFocus !== false
        };
    }

    // ========== 加密模式解析（含 Web Crypto 降级） ==========

    _resolveEncryption() {
        const requested = this.options.encryptionType;
        if (!JaravelCaptcha.isWebCryptoAvailable()) {
            // Web Crypto 不可用
            if (requested === 'aes' || requested === 'rsa') {
                console.warn(
                    '[JaravelCaptcha] Web Crypto API 不可用，加密模式 "' +
                    requested + '" 自动降级为 "none"（纯 Base64）。验证码功能不受影响。'
                );
            }
            this._effectiveEncType = 'none';
        } else {
            this._effectiveEncType = requested;
        }
    }

    // ========== 共享样式注入 ==========

    static _injectSharedStyles() {
        const STYLE_ID = 'jc-shared-styles';
        if (document.getElementById(STYLE_ID)) return;

        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
/* ===== Jaravel Captcha 共享样式（Material Design MD1 风格） ===== */

.jc-wrapper {
    font-family: 'Roboto', 'Helvetica', 'Arial', sans-serif;
    width: 100%;
    max-width: 360px;
    padding: 16px;
    background: #f5f5f5;
    border-radius: 8px;
    box-sizing: border-box;
    /* 允许纵向滚动，禁止双指缩放带来的坐标漂移 */
    -webkit-text-size-adjust: 100%;
}

/* ===== 响应式舞台（stage） =====
   验证码的滑块 / 旋转 / 点选依赖「图片像素坐标」与「布局像素坐标」严格 1:1，
   否则提交给后端的 gapX / 点选坐标会失真。
   因此这里不缩放内部元素，而是把整个舞台按容器宽度做 CSS transform 等比缩放：
   - 舞台内部始终保持后端下发的原始像素尺寸（坐标计算不受影响）；
   - 视觉上自适应任意屏幕宽度（桌面 / 移动端同一份代码）；
   - 拖动时把屏幕位移除以缩放系数换算回布局位移即可。 */
.jc-stage-wrap {
    width: 100%;
    overflow: hidden;
}
.jc-stage {
    transform-origin: top left;
    will-change: transform;
}

/* ----- 加载与错误提示 ----- */
.jc-loading {
    text-align: center;
    padding: 24px 0;
    color: #999;
    font-size: 14px;
}
.jc-error {
    text-align: center;
    padding: 24px 0;
    color: #c62828;
    font-size: 14px;
}

/* ----- 数字 / 算术验证码 ----- */
.jc-img-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
}
.jc-img {
    height: 60px;
    flex: 1;
    min-width: 0;
    border: 1px solid #e0e0e0;
    border-radius: 6px;
    object-fit: contain;
    background: transparent;
}
.jc-refresh-btn {
    width: 40px;
    height: 40px;
    min-width: 40px;
    border: none;
    border-radius: 50%;
    background: #1976d2;
    color: #fff;
    font-size: 20px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.15s, transform 0.3s;
    flex-shrink: 0;
    padding: 0;
    line-height: 1;
}
.jc-refresh-btn:hover { background: #1565c0; transform: rotate(180deg); }
.jc-refresh-btn:active { background: #0d47a1; }
.jc-input {
    flex: 1;
    width: 100%;
    padding: 10px 14px;
    font-size: 18px;
    text-align: center;
    letter-spacing: 4px;
    border: 2px solid #e0e0e0;
    border-radius: 6px;
    outline: none;
    box-sizing: border-box;
    transition: border-color 0.2s, box-shadow 0.2s;
    font-family: 'Courier New', monospace;
    background: #fff;
}
.jc-input:focus { border-color: #1976d2; box-shadow: 0 0 0 3px rgba(25,118,210,0.12); }

/* ----- 滑动验证码 ----- */
.jc-slider-container {
    position: relative;
    display: block;
    /* 尺寸由 JS 按后端下发的图片尺寸设置，保证与图片像素 1:1 */
}
.jc-slider-bg {
    border-radius: 4px;
    display: block;
}
.jc-slider-block {
    position: absolute;
    top: 0;
    left: 0;
    pointer-events: none;
    filter: drop-shadow(0 0 2px rgba(0,0,0,0.3));
}

/* ----- 旋转验证码 ----- */
.jc-rotate-container {
    position: relative;
    display: inline-block;
}
.jc-rotate-bg {
    border-radius: 8px;
    display: block;
    border: none !important;
    outline: none !important;
}
.jc-rotate-circle {
    position: absolute;
    transform-origin: center center;
    pointer-events: none;
    border: none !important;
    outline: none !important;
    border-radius: 0 !important;
    background: transparent;
    clip-path: none !important;
    -webkit-clip-path: none !important;
}

/* ----- 拖动轨道（滑动 & 旋转共用） ----- */
.jc-drag-track {
    position: relative;
    width: 100%;
    height: 36px;
    background: #e0e0e0;
    border-radius: 18px;
    margin: 12px auto;
    user-select: none;
    -webkit-user-select: none;
    /* 移动端：手指在轨道上拖动时不要触发页面滚动 */
    touch-action: none;
}
.jc-drag-handle {
    position: absolute;
    left: 0;
    top: 0;
    width: 36px;
    height: 36px;
    background: #1976d2;
    border-radius: 50%;
    cursor: grab;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    z-index: 2;
    font-size: 16px;
    box-shadow: 0 2px 6px rgba(0,0,0,0.25);
    transition: background 0.15s;
    -webkit-user-select: none;
    user-select: none;
    line-height: 36px;
    text-align: center;
    padding: 0;
    margin: 0;
    box-sizing: border-box;
    /* 移动端：手柄独占触摸手势，避免与页面滚动 / 缩放冲突 */
    touch-action: none;
    -webkit-touch-callout: none;
    -webkit-tap-highlight-color: transparent;
}
.jc-drag-handle:hover { background: #1565c0; }
.jc-drag-handle:active { cursor: grabbing; background: #0d47a1; }
.jc-drag-tip {
    position: absolute;
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    color: #999;
    font-size: 13px;
    pointer-events: none;
    z-index: 1;
    white-space: nowrap;
}

/* ----- 文字点选验证码 ----- */
.jc-click-prompt {
    font-size: 15px;
    color: #333;
    margin: 0 0 8px 0;
    text-align: center;
    font-weight: 500;
}
.jc-click-area {
    position: relative;
    display: block;
    cursor: pointer;
    /* 移动端：允许点击但禁用双击缩放，避免 300ms 延迟与坐标漂移 */
    touch-action: manipulation;
    -webkit-tap-highlight-color: transparent;
}
.jc-click-img {
    border: 1px solid #e0e0e0;
    border-radius: 4px;
    display: block;
    -webkit-user-select: none;
    user-select: none;
    -webkit-touch-callout: none;
}
.jc-click-mark {
    position: absolute;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: rgba(25, 118, 210, 0.85);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    pointer-events: none;
    font-weight: bold;
    box-shadow: 0 2px 4px rgba(0,0,0,0.3);
}
.jc-click-progress {
    font-size: 13px;
    color: #999;
    margin: 8px 0 0 0;
    text-align: center;
}

/* ----- 结果提示 ----- */
.jc-result { margin-top: 12px; text-align: center; }
.jc-result-chip {
    padding: 6px 16px;
    border-radius: 16px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
}
.jc-result-ok { background: #c8e6c9; color: #2e7d32; }
.jc-result-fail { background: #ffcdd2; color: #c62828; }

/* ----- 旋转角度文字 ----- */
.jc-rotate-angle {
    font-size: 13px;
    color: #999;
    text-align: center;
    margin: 4px 0 0 0;
}

/* ===================================================================
   全屏弹层模式（modal）
   所有验证码类型通用：点击验证按钮后弹出全屏遮罩，验证框屏幕居中。
   使用 position:fixed + flex 居中，兼容桌面与移动端（含刘海屏安全区）。
   =================================================================== */
.jc-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.55);
    /* 移动端浏览器地址栏收起时 100vh 会溢出，用动态视口单位兜底 */
    height: 100%;
    padding: 16px;
    padding-top: calc(16px + env(safe-area-inset-top, 0px));
    padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
    box-sizing: border-box;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    opacity: 0;
    transition: opacity 0.18s ease;
}
.jc-overlay.jc-overlay-open { opacity: 1; }

.jc-modal {
    position: relative;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
    width: 100%;
    max-width: 400px;
    margin: auto;
    box-sizing: border-box;
    transform: translateY(12px) scale(0.98);
    transition: transform 0.18s ease;
}
.jc-overlay-open .jc-modal { transform: translateY(0) scale(1); }

.jc-modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px;
    border-bottom: 1px solid #eee;
}
.jc-modal-title {
    font-size: 16px;
    font-weight: 500;
    color: #212121;
    margin: 0;
}
.jc-modal-close {
    width: 32px;
    height: 32px;
    min-width: 32px;
    border: none;
    background: transparent;
    color: #757575;
    font-size: 22px;
    line-height: 1;
    cursor: pointer;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    transition: background 0.15s, color 0.15s;
    touch-action: manipulation;
    -webkit-tap-highlight-color: transparent;
}
.jc-modal-close:hover { background: #f0f0f0; color: #212121; }
.jc-modal-close:active { background: #e0e0e0; }

.jc-modal-body { padding: 8px; }

/* 弹层内的验证框铺满弹层宽度、去掉外层灰底 */
.jc-modal .jc-wrapper {
    max-width: 100%;
    background: transparent;
    padding: 8px;
}

/* 弹层打开时锁定页面滚动 */
.jc-body-locked {
    overflow: hidden !important;
    touch-action: none;
}

/* 小屏适配：进一步压缩留白 */
@media (max-width: 420px) {
    .jc-overlay { padding: 10px; }
    .jc-modal-header { padding: 12px 12px; }
    .jc-modal-title { font-size: 15px; }
    .jc-wrapper { padding: 12px; }
    .jc-modal .jc-wrapper { padding: 6px; }
}
`;
        document.head.appendChild(style);
    }

    // ========== 保留容器原有内容 ==========

    _preserveOriginalContent() {
        const hiddenWrap = document.createElement('div');
        hiddenWrap.style.display = 'none';
        hiddenWrap.setAttribute('data-jc-original', 'true');
        while (this.container.firstChild) {
            hiddenWrap.appendChild(this.container.firstChild);
        }
        this.container.appendChild(hiddenWrap);
        this._originalContentWrap = hiddenWrap;
    }

    // ========== UI 构建 ==========

    _buildUI() {
        const wrapper = document.createElement('div');
        wrapper.className = 'jc-wrapper';
        wrapper.style.maxWidth = this.options.maxWidth + 'px';
        if (!this._visible && !this.options.modal) wrapper.style.display = 'none';

        // 加载提示
        const loading = document.createElement('div');
        loading.className = 'jc-loading';
        loading.textContent = '正在加载验证码...';
        wrapper.appendChild(loading);
        this._loadingEl = loading;

        // 错误提示
        const error = document.createElement('div');
        error.className = 'jc-error';
        error.style.display = 'none';
        wrapper.appendChild(error);
        this._errorEl = error;

        // 内容容器
        const content = document.createElement('div');
        content.className = 'jc-content';
        content.style.display = 'none';
        wrapper.appendChild(content);
        this._contentEl = content;

        // 结果提示
        const result = document.createElement('div');
        result.className = 'jc-result';
        result.style.display = 'none';
        wrapper.appendChild(result);
        this._resultEl = result;

        // 根据类型构建具体 UI
        switch (this.options.type) {
            case 'number':
            case 'arithmetic':
                this._buildNumberArithmetic(content);
                break;
            case 'slider':
                this._buildSlider(content);
                break;
            case 'rotate':
                this._buildRotate(content);
                break;
            case 'click':
                this._buildClick(content);
                break;
            default:
                this._buildNumberArithmetic(content);
                break;
        }

        this._wrapper = wrapper;

        if (this.options.modal) {
            // 全屏弹层模式：验证框挂到 body 上的全屏遮罩内，屏幕居中
            this._buildModal(wrapper);
        } else {
            // 内联模式：保持原行为，直接挂在业务容器内
            this.container.appendChild(wrapper);
        }
    }

    // ----- 全屏弹层外壳 -----

    /**
     * 构建全屏弹层外壳（遮罩 + 居中卡片）。
     * <p>
     * 遮罩挂载到 {@code document.body} 而非业务容器，避免被父级
     * {@code overflow:hidden} / {@code transform} / {@code z-index} 层叠上下文裁剪 ——
     * 这是弹层在真实业务页面里最常见的失效原因。
     *
     * @param {HTMLElement} wrapper 验证框主体
     */
    _buildModal(wrapper) {
        const overlay = document.createElement('div');
        overlay.className = 'jc-overlay';
        overlay.style.zIndex = String(this.options.zIndex);
        overlay.style.display = 'none';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');

        const modal = document.createElement('div');
        modal.className = 'jc-modal';

        // 头部：标题 + 关闭按钮
        const header = document.createElement('div');
        header.className = 'jc-modal-header';

        const title = document.createElement('p');
        title.className = 'jc-modal-title';
        title.textContent = this.options.modalTitle;
        header.appendChild(title);

        if (this.options.closable) {
            const closeBtn = document.createElement('button');
            closeBtn.className = 'jc-modal-close';
            closeBtn.type = 'button';
            closeBtn.title = '关闭';
            closeBtn.setAttribute('aria-label', '关闭');
            closeBtn.innerHTML = '&times;';
            header.appendChild(closeBtn);
            this._modalCloseEl = closeBtn;
        }
        modal.appendChild(header);

        // 主体
        const body = document.createElement('div');
        body.className = 'jc-modal-body';
        body.appendChild(wrapper);
        modal.appendChild(body);

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        this._overlayEl = overlay;
        this._modalEl = modal;
    }

    // ----- 数字 / 算术 -----

    _buildNumberArithmetic(content) {
        const imgRow = document.createElement('div');
        imgRow.className = 'jc-img-row';

        const img = document.createElement('img');
        img.className = 'jc-img';
        img.alt = '验证码';
        imgRow.appendChild(img);
        this._imgEl = img;

        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'jc-refresh-btn';
        refreshBtn.type = 'button';
        refreshBtn.title = '刷新验证码';
        refreshBtn.innerHTML = '&#8635;'; // ↻
        imgRow.appendChild(refreshBtn);
        this._refreshBtnEl = refreshBtn;

        content.appendChild(imgRow);

        const input = document.createElement('input');
        input.className = 'jc-input';
        input.type = 'text';
        input.placeholder = '输入验证码';
        input.autocomplete = 'off';
        content.appendChild(input);
        this._inputEl = input;
    }

    // ----- 滑动 -----

    _buildSlider(content) {
        // 舞台：图片与轨道共处同一坐标系，保证 handle.offsetLeft == 图片像素 X
        const stage = this._createStage(content);

        const container = document.createElement('div');
        container.className = 'jc-slider-container';

        const bg = document.createElement('img');
        bg.className = 'jc-slider-bg';
        bg.alt = '背景';
        container.appendChild(bg);
        this._sliderBgEl = bg;

        const block = document.createElement('img');
        block.className = 'jc-slider-block';
        block.alt = '滑块';
        container.appendChild(block);
        this._sliderBlockEl = block;

        stage.appendChild(container);
        this._sliderContainerEl = container;

        // 拖动轨道
        const track = document.createElement('div');
        track.className = 'jc-drag-track';

        const handle = document.createElement('div');
        handle.className = 'jc-drag-handle';
        handle.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;"><polyline points="9 18 15 12 9 6"></polyline></svg>';
        track.appendChild(handle);
        this._sliderHandleEl = handle;

        const tip = document.createElement('span');
        tip.className = 'jc-drag-tip';
        tip.textContent = '向右拖动滑块';
        track.appendChild(tip);

        stage.appendChild(track);
        this._sliderTrackEl = track;

        // 刷新按钮
        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'jc-refresh-btn';
        refreshBtn.type = 'button';
        refreshBtn.title = '刷新验证码';
        refreshBtn.innerHTML = '&#8635;';
        refreshBtn.style.margin = '0 auto';
        refreshBtn.style.display = 'flex';
        content.appendChild(refreshBtn);
        this._refreshBtnEl = refreshBtn;
    }

    // ----- 旋转 -----

    _buildRotate(content) {
        const stage = this._createStage(content);

        const container = document.createElement('div');
        container.className = 'jc-rotate-container';

        const bg = document.createElement('img');
        bg.className = 'jc-rotate-bg';
        bg.alt = '背景';
        container.appendChild(bg);
        this._rotateBgEl = bg;

        const circle = document.createElement('img');
        circle.className = 'jc-rotate-circle';
        circle.alt = '圆盘';
        container.appendChild(circle);
        this._rotateCircleEl = circle;

        stage.appendChild(container);
        this._rotateContainerEl = container;

        // 角度文字
        const angleText = document.createElement('p');
        angleText.className = 'jc-rotate-angle';
        angleText.textContent = '当前角度：0\u00b0';
        stage.appendChild(angleText);
        this._rotateAngleEl = angleText;

        // 拖动轨道
        const track = document.createElement('div');
        track.className = 'jc-drag-track';

        const handle = document.createElement('div');
        handle.className = 'jc-drag-handle';
        handle.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>';
        track.appendChild(handle);
        this._rotateHandleEl = handle;

        const tip = document.createElement('span');
        tip.className = 'jc-drag-tip';
        tip.textContent = '拖动滑块旋转圆盘';
        track.appendChild(tip);

        stage.appendChild(track);
        this._rotateTrackEl = track;

        // 刷新按钮
        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'jc-refresh-btn';
        refreshBtn.type = 'button';
        refreshBtn.title = '刷新验证码';
        refreshBtn.innerHTML = '&#8635;';
        refreshBtn.style.margin = '0 auto';
        refreshBtn.style.display = 'flex';
        content.appendChild(refreshBtn);
        this._refreshBtnEl = refreshBtn;
    }

    // ----- 文字点选 -----

    _buildClick(content) {
        const prompt = document.createElement('p');
        prompt.className = 'jc-click-prompt';
        content.appendChild(prompt);
        this._clickPromptEl = prompt;

        const stage = this._createStage(content);

        const area = document.createElement('div');
        area.className = 'jc-click-area';

        const img = document.createElement('img');
        img.className = 'jc-click-img';
        img.alt = '点选验证码';
        area.appendChild(img);
        this._clickImgEl = img;

        stage.appendChild(area);
        this._clickAreaEl = area;

        const progress = document.createElement('p');
        progress.className = 'jc-click-progress';
        progress.textContent = '已点击 0 / 0';
        content.appendChild(progress);
        this._clickProgressEl = progress;

        // 刷新按钮
        const refreshBtn = document.createElement('button');
        refreshBtn.className = 'jc-refresh-btn';
        refreshBtn.type = 'button';
        refreshBtn.title = '刷新验证码';
        refreshBtn.innerHTML = '&#8635;';
        refreshBtn.style.margin = '8px auto 0 auto';
        refreshBtn.style.display = 'flex';
        content.appendChild(refreshBtn);
        this._refreshBtnEl = refreshBtn;
    }

    // ========== 响应式舞台（跨端自适应的核心） ==========

    /**
     * 创建响应式舞台并挂载到内容区。
     * <p>
     * <b>为什么需要舞台</b>：滑动缺口 X、点选坐标都以「后端图片像素」为单位提交，
     * 若直接用 CSS 把图片缩放到 100% 宽度，布局像素与图片像素不再 1:1，
     * 拖动值与点击坐标都会失真（在手机上表现为「怎么滑都不对」）。
     * <p>
     * 舞台的做法是：内部所有元素保持后端下发的原始像素尺寸，
     * 整体用 {@code transform: scale(k)} 等比缩放到容器宽度。
     * 这样一份代码同时适配桌面与移动端，无需任何条件分支。
     *
     * @param {HTMLElement} content 内容容器
     * @returns {HTMLElement} 舞台元素
     */
    _createStage(content) {
        const wrap = document.createElement('div');
        wrap.className = 'jc-stage-wrap';

        const stage = document.createElement('div');
        stage.className = 'jc-stage';
        wrap.appendChild(stage);

        content.appendChild(wrap);
        this._stageWrapEl = wrap;
        this._stageEl = stage;
        return stage;
    }

    /**
     * 设置舞台的原始像素宽度（等于后端图片宽度），并立即重算缩放。
     *
     * @param {number} width 后端图片宽度（像素）
     */
    _setStageBaseWidth(width) {
        const w = (typeof width === 'number' && width > 0) ? Math.round(width) : 0;
        if (w <= 0) return;
        this._stageBaseWidth = w;
        if (this._stageEl) {
            this._stageEl.style.width = w + 'px';
        }
        this._updateStageScale();
    }

    /**
     * 按容器可用宽度重算舞台缩放系数。
     * <p>
     * 只缩小不放大（{@code k <= 1}），避免小图在大屏被拉糊。
     * transform 不参与布局，因此需要手动把外层高度收缩为 {@code 原始高度 * k}，
     * 否则底部会出现一大片空白。
     */
    _updateStageScale() {
        if (!this._stageEl || !this._stageWrapEl || this._stageBaseWidth <= 0) return;

        const available = this._stageWrapEl.clientWidth;
        if (available <= 0) return; // 隐藏状态下宽度为 0，等可见时再算

        const scale = Math.min(1, available / this._stageBaseWidth);
        this._stageScale = scale;
        this._stageEl.style.transform = (scale < 1) ? ('scale(' + scale + ')') : '';

        const naturalHeight = this._stageEl.offsetHeight;
        if (naturalHeight > 0) {
            const h = Math.ceil(naturalHeight * scale);
            if (this._lastWrapHeight !== h) {
                this._stageWrapEl.style.height = h + 'px';
                this._lastWrapHeight = h;
            }
        }
    }

    /**
     * 当前舞台缩放系数（屏幕像素 → 布局像素的换算依据）。
     * @returns {number} 大于 0 的缩放系数
     */
    _getStageScale() {
        return (this._stageScale > 0) ? this._stageScale : 1;
    }

    /**
     * 监听尺寸变化：窗口缩放、移动端旋屏、容器宽度变化。
     * <p>
     * ResizeObserver 只在<b>宽度</b>变化时才重算 —— 高度变化是本方法自身
     * 修改 wrap 高度造成的，忽略它可彻底避免回调自激循环。
     */
    _observeResize() {
        const update = () => this._updateStageScale();

        this._addManagedListener(window, 'resize', update);
        this._addManagedListener(window, 'orientationchange', update);

        if (typeof ResizeObserver !== 'undefined' && this._stageWrapEl) {
            this._lastWrapWidth = -1;
            this._resizeObserver = new ResizeObserver((entries) => {
                if (!entries || entries.length === 0) return;
                const w = entries[0].contentRect.width;
                if (Math.abs(w - this._lastWrapWidth) < 0.5) return;
                this._lastWrapWidth = w;
                update();
            });
            this._resizeObserver.observe(this._stageWrapEl);
        }
    }

    // ========== 事件绑定 ==========

    _attachEvents() {
        // 刷新按钮（所有类型通用）
        if (this._refreshBtnEl) {
            this._addManagedListener(this._refreshBtnEl, 'click', () => {
                this.refresh();
            });
        }

        switch (this.options.type) {
            case 'number':
            case 'arithmetic':
                this._attachNumberArithmeticEvents();
                break;
            case 'slider':
                this._attachSliderEvents();
                break;
            case 'rotate':
                this._attachRotateEvents();
                break;
            case 'click':
                this._attachClickEvents();
                break;
        }

        // 全屏弹层模式的关闭交互
        if (this.options.modal) {
            this._attachModalEvents();
        }
    }

    // ----- 全屏弹层事件 -----

    _attachModalEvents() {
        // 关闭按钮
        if (this._modalCloseEl) {
            this._addManagedListener(this._modalCloseEl, 'click', () => this.hide());
        }

        // 点击遮罩空白处关闭（点弹层内部不关闭）
        if (this.options.maskClosable && this._overlayEl) {
            this._addManagedListener(this._overlayEl, 'mousedown', (e) => {
                if (e.target === this._overlayEl) {
                    this._maskPressed = true;
                }
            });
            this._addManagedListener(this._overlayEl, 'mouseup', (e) => {
                // 要求按下与抬起都在遮罩上，避免在弹层内拖动到遮罩误关闭
                if (this._maskPressed && e.target === this._overlayEl) {
                    this.hide();
                }
                this._maskPressed = false;
            });
            // 移动端：touchend 直接判定
            this._addManagedListener(this._overlayEl, 'touchend', (e) => {
                if (e.target === this._overlayEl) {
                    this.hide();
                }
            });
        }

        // ESC 关闭
        if (this.options.escClosable) {
            this._escHandler = (e) => {
                if (e.key === 'Escape' && this._visible) {
                    this.hide();
                }
            };
            this._addManagedListener(document, 'keydown', this._escHandler);
        }
    }

    // ----- 数字 / 算术事件 -----

    _attachNumberArithmeticEvents() {
        // 按 Enter 触发完成
        this._addManagedListener(this._inputEl, 'keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this._complete();
            }
        });
    }

    // ----- 滑动事件 -----

    _attachSliderEvents() {
        this._sliderBlockSize = 36;
        this._dragController = this._createDragController(
            this._sliderTrackEl,
            this._sliderHandleEl,
            {
                getMaxLeft: () => 300 - 36,
                onMove: (value) => {
                    if (this._sliderBlockEl) {
                        this._sliderBlockEl.style.left = value + 'px';
                    }
                },
                onEnd: (value, trajectory) => {
                    // 松开触发完成
                    this._complete();
                }
            }
        );
    }

    // ----- 旋转事件 -----

    _attachRotateEvents() {
        this._dragController = this._createDragController(
            this._rotateTrackEl,
            this._rotateHandleEl,
            {
                scaleTo: 360,
                onMove: (angle) => {
                    if (this._rotateCircleEl) {
                        this._rotateCircleEl.style.transform = 'rotate(' + angle + 'deg)';
                    }
                    if (this._rotateAngleEl) {
                        this._rotateAngleEl.textContent = '当前角度：' + angle + '\u00b0';
                    }
                },
                onEnd: (angle, trajectory) => {
                    // 松开触发完成
                    this._complete();
                }
            }
        );
    }

    // ----- 文字点选事件 -----

    _attachClickEvents() {
        this._clickCount = 0;
        this._clickImgWidth = 0;
        this._clickImgHeight = 0;

        // 触摸：touchend 立即响应，不必等浏览器合成 click（省掉最长 300ms 延迟）
        this._addManagedListener(this._clickImgEl, 'touchend', (e) => {
            if (!e.changedTouches || e.changedTouches.length === 0) return;
            // 阻止浏览器随后合成的「幽灵 click」，否则一次点按会被记录两次
            e.preventDefault();
            this._lastTouchTapAt = Date.now();
            const t = e.changedTouches[0];
            this._registerClickPoint(t.clientX, t.clientY);
        }, { passive: false });

        // 鼠标：桌面端走标准 click
        this._addManagedListener(this._clickImgEl, 'click', (e) => {
            // 双保险：部分浏览器 preventDefault 后仍会补发 click，按时间窗过滤
            if (Date.now() - this._lastTouchTapAt < 700) return;
            this._registerClickPoint(e.clientX, e.clientY);
        });
    }

    /**
     * 记录一个点击坐标（鼠标与触摸共用）。
     * <p>
     * 坐标换算分两步，务必区分两个坐标系：
     * <ul>
     *   <li><b>屏幕像素</b> — {@code clientX/Y}、{@code getBoundingClientRect()}，受 transform 缩放影响</li>
     *   <li><b>布局像素</b> — 舞台内元素的 left/top，等于后端图片像素（舞台基准宽 = 图片宽）</li>
     * </ul>
     * 提交给后端的是图片像素坐标；视觉标记挂在舞台内，必须用布局像素定位，
     * 直接拿屏幕像素去放标记会在缩放后整体偏移。
     *
     * @param {number} clientX 屏幕 X
     * @param {number} clientY 屏幕 Y
     */
    _registerClickPoint(clientX, clientY) {
        if (!this._captchaData || this._completed) return;
        if (this._clickPoints.length >= this._clickCount) return;

        const rect = this._clickImgEl.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) return;

        const displayX = clientX - rect.left;
        const displayY = clientY - rect.top;

        // rect 是缩放后的视觉尺寸，除以它即可一步换算到图片像素，无需再乘舞台系数
        const imgWidth = this._clickImgWidth || this._clickImgEl.naturalWidth || rect.width;
        const imgHeight = this._clickImgHeight || this._clickImgEl.naturalHeight || rect.height;
        const realX = Math.round(displayX * (imgWidth / rect.width));
        const realY = Math.round(displayY * (imgHeight / rect.height));

        // 越界点击（缩放取整误差）钳制回图片范围内
        if (realX < 0 || realY < 0 || realX > imgWidth || realY > imgHeight) return;

        this._clickPoints.push({ x: realX, y: realY });

        // 视觉标记：舞台内布局像素 == 图片像素
        const mark = document.createElement('div');
        mark.className = 'jc-click-mark';
        mark.style.left = (realX - 15) + 'px';
        mark.style.top = (realY - 15) + 'px';
        mark.textContent = this._clickPoints.length;
        this._clickAreaEl.appendChild(mark);

        // 更新进度
        this._clickProgressEl.textContent =
            '已点击 ' + this._clickPoints.length + ' / ' + this._clickCount;

        // 达到所需点击数后触发完成
        if (this._clickPoints.length >= this._clickCount) {
            this._complete();
        }
    }

    // ========== 通用拖动控制器（滑动 & 旋转共用） ==========

    /**
     * 创建拖动控制器
     * @param {HTMLElement} trackEl - 轨道元素
     * @param {HTMLElement} handleEl - 滑块手柄元素
     * @param {Object} opts - { getMaxLeft, scaleTo, onMove, onEnd }
     * @returns {{reset: Function, getValue: Function, getTrajectory: Function}}
     */
    _createDragController(trackEl, handleEl, opts) {
        let isDragging = false;
        let startClientX = 0;
        let startLeft = 0;
        let startTime = 0;
        let trajectory = [];
        let currentValue = 0;
        let maxLeft = 0;
        let enabled = true;  // 交互启用标记

        const calcMaxLeft = () => {
            let ml = trackEl.offsetWidth - handleEl.offsetWidth;
            if (opts.getMaxLeft) {
                const override = opts.getMaxLeft();
                if (override > 0 && override < ml) {
                    ml = override;
                }
            }
            return ml;
        };

        const calcValue = (left) => {
            if (opts.scaleTo) {
                if (maxLeft <= 0) return 0;
                return Math.round((left / maxLeft) * opts.scaleTo);
            }
            return left;
        };

        const pointerDown = (clientX) => {
            if (!enabled) return;  // 交互已禁用时忽略
            isDragging = true;
            maxLeft = calcMaxLeft();
            startClientX = clientX;
            startLeft = handleEl.offsetLeft;
            startTime = Date.now();
            currentValue = calcValue(startLeft);
            // 轨迹采集开始
            trajectory = [{ t: 0, v: currentValue }];
            document.body.style.userSelect = 'none';
        };

        const pointerMove = (clientX) => {
            if (!isDragging) return;
            // 屏幕位移 → 布局位移。
            // 舞台整体用 transform:scale(k) 缩放（移动端小屏 k<1），而 offsetLeft /
            // offsetWidth 读到的都是<b>未缩放</b>的布局像素。若直接用 clientX 差值，
            // 手指移动 100 屏幕像素会被当成 100 布局像素，实际只该走 100/k —— 
            // 缺口位置会系统性偏移，导致移动端永远对不准。
            const dx = (clientX - startClientX) / this._getStageScale();
            const newLeft = Math.round(Math.max(0, Math.min(maxLeft, startLeft + dx)));
            handleEl.style.left = newLeft + 'px';
            currentValue = calcValue(newLeft);
            // 轨迹采集：记录每个移动点
            trajectory.push({ t: Date.now() - startTime, v: currentValue });
            if (opts.onMove) opts.onMove(currentValue);
        };

        const pointerUp = () => {
            if (!isDragging) return;
            isDragging = false;
            document.body.style.userSelect = '';
            // 轨迹至少 2 个点才触发结束回调
            if (trajectory.length > 1 && opts.onEnd) {
                opts.onEnd(currentValue, trajectory);
            }
        };

        // 鼠标事件
        const onMouseDown = (e) => {
            e.preventDefault();
            e.stopPropagation();
            pointerDown(e.clientX);
        };
        const onMouseMove = (e) => {
            pointerMove(e.clientX);
        };
        const onMouseUp = () => {
            pointerUp();
        };

        // 触摸事件
        const onTouchStart = (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (e.touches.length > 0) {
                pointerDown(e.touches[0].clientX);
            }
        };
        const onTouchMove = (e) => {
            if (isDragging) {
                e.preventDefault();
                if (e.touches.length > 0) {
                    pointerMove(e.touches[0].clientX);
                }
            }
        };
        const onTouchEnd = () => {
            pointerUp();
        };

        // 注册所有监听器（便于 destroy 时统一移除）
        this._addManagedListener(handleEl, 'mousedown', onMouseDown);
        this._addManagedListener(document, 'mousemove', onMouseMove);
        this._addManagedListener(document, 'mouseup', onMouseUp);
        this._addManagedListener(handleEl, 'touchstart', onTouchStart, { passive: false });
        this._addManagedListener(document, 'touchmove', onTouchMove, { passive: false });
        this._addManagedListener(document, 'touchend', onTouchEnd);

        return {
            reset() {
                handleEl.style.left = '0px';
                currentValue = 0;
                trajectory = [];
            },
            getValue() {
                return currentValue;
            },
            getTrajectory() {
                return trajectory;
            },
            setEnabled(val) {
                enabled = val;
                if (!val) {
                    // 禁用时立即停止正在进行的拖动
                    isDragging = false;
                    document.body.style.userSelect = '';
                }
                // 视觉反馈：禁用时降低不透明度
                handleEl.style.opacity = val ? '' : '0.5';
            }
        };
    }

    // ========== 托管事件监听器 ==========

    _addManagedListener(el, event, handler, options) {
        el.addEventListener(event, handler, options);
        this._listeners.push({ el, event, handler, options });
    }

    // ========== 加载验证码 ==========

    async _loadCaptcha() {
        this._showLoading();
        // 触发 beforeGet 事件（获取验证码前，包含刷新场景）
        this._emit('beforeGet', this.options.type);
        try {
            // 构建 URL。
            // 只发送 type 与 scene 两个参数：
            //   - type  ：验证码形态（展示层，前端可决定）
            //   - scene ：场景<b>名</b>，具体数值由后端 jaravel.captcha.scenes 白名单定义
            // 任何 tolerance / length / clickTargetCount 之类的安全参数都不再从前端传出，
            // 后端也已停止接受 —— 前端能「选场景」，但不能「定难度」。
            let url = this.options.apiUrl + '?type=' + encodeURIComponent(this.options.type);
            if (this.options.scene) {
                url += '&scene=' + encodeURIComponent(this.options.scene);
            }
            const resp = await fetch(url);
            const json = await resp.json();
            if (json.code !== 200) {
                throw new Error(json.msg || '生成验证码失败');
            }
            this._captchaData = json.data;
            this._captchaKey = json.data.captchaKey;
            // 合并凭证：优先用后端下发的 data.key；老版本后端没有该字段时本地拼装
            this._key = json.data.key
                || ((json.data.type || this.options.type) + '.' + json.data.captchaKey);

            // 采用后端下发的加密参数（业务方未显式指定时）。
            // 启用全局应用密钥兜底后，模块实际密钥由后端决定，前端必须跟随。
            if (json.data.encType && !this.options.encryptionTypeExplicit) {
                this.options.encryptionType = String(json.data.encType).toLowerCase();
                this._resolveEncryption();
            }
            if (json.data.encKey && !this.options.encryptionKeyExplicit) {
                this.options.encryptionKey = json.data.encKey;
            } else if (json.data.encKey && this.options.encryptionKey !== json.data.encKey) {
                // 显式配置的密钥与后端实际生效的密钥不一致：服务端将无法解密用户输入，
                // 校验会恒定失败。这类问题只表现为 403，极难排查，因此在此显式告警。
                console.warn('[Captcha] 前端显式配置的 encryptionKey 与后端下发的 encKey 不一致，'
                    + '服务端解密必定失败。请移除前端硬编码密钥，改用后端下发值'
                    + '（后端启用 jaravel.key 全局密钥兜底时实际密钥会变化）。');
            }

            this._loaded = true;
            this._renderCaptcha(json.data);
            // 触发 afterGet 事件（验证码已加载并渲染完成）
            this._emit('afterGet', this._key, this._captchaData);
        } catch (e) {
            this._showError('加载失败: ' + e.message);
            console.error('[Captcha] 加载验证码失败:', e);
        }
    }

    // ========== 渲染验证码 ==========

    _renderCaptcha(data) {
        this._hideLoading();
        this._clearResult();

        switch (this.options.type) {
            case 'number':
            case 'arithmetic':
                this._renderNumberArithmetic(data);
                break;
            case 'slider':
                this._renderSlider(data);
                break;
            case 'rotate':
                this._renderRotate(data);
                break;
            case 'click':
                this._renderClick(data);
                break;
        }
    }

    _renderNumberArithmetic(data) {
        this._imgEl.src = this._imgSrc(data.imageBase64);
        this._inputEl.value = '';
        this._inputEl.focus();
    }

    _renderSlider(data) {
        const extra = data.extra || {};
        const width = extra.width || 0;
        const height = extra.height || 0;

        this._sliderBgEl.src = this._imgSrc(data.imageBase64);
        this._sliderBlockEl.src = this._imgSrc(extra.sliderImage);
        this._sliderBlockEl.style.top = (extra.gapY || 0) + 'px';
        this._sliderBlockEl.style.left = '0px';
        this._sliderBlockSize = extra.blockSize || 40;

        // 容器锁定为后端图片的原始像素尺寸，缩放交给舞台统一处理
        if (width > 0 && this._sliderContainerEl) {
            this._sliderContainerEl.style.width = width + 'px';
            this._sliderBgEl.style.width = width + 'px';
            if (height > 0) {
                this._sliderContainerEl.style.height = height + 'px';
                this._sliderBgEl.style.height = height + 'px';
            }
            if (this._sliderTrackEl) this._sliderTrackEl.style.width = width + 'px';
            this._setStageBaseWidth(width);
        }

        if (this._dragController) this._dragController.reset();
    }

    _renderRotate(data) {
        const extra = data.extra || {};
        const size = extra.size || 300;
        const r = extra.r || 100;
        const cx = extra.cx || size / 2;
        const cy = extra.cy || size / 2;

        // 容器 & 背景图动态尺寸
        this._rotateContainerEl.style.width = size + 'px';
        this._rotateContainerEl.style.height = size + 'px';
        this._rotateBgEl.src = this._imgSrc(data.imageBase64);
        this._rotateBgEl.style.width = size + 'px';
        this._rotateBgEl.style.height = size + 'px';

        // 圆盘图：2r×2r，居中对齐背景圆心，无边框无 clip-path
        this._rotateCircleEl.src = this._imgSrc(extra.circleImage);
        this._rotateCircleEl.style.width = (r * 2) + 'px';
        this._rotateCircleEl.style.height = (r * 2) + 'px';
        this._rotateCircleEl.style.left = (cx - r) + 'px';
        this._rotateCircleEl.style.top = (cy - r) + 'px';
        this._rotateCircleEl.style.transform = 'rotate(0deg)';

        if (this._rotateAngleEl) {
            this._rotateAngleEl.textContent = '当前角度：0\u00b0';
        }

        // 舞台基准 = 圆盘图原始边长
        if (this._rotateTrackEl) this._rotateTrackEl.style.width = size + 'px';
        this._setStageBaseWidth(size);

        if (this._dragController) this._dragController.reset();
    }

    _renderClick(data) {
        const extra = data.extra || {};
        this._clickPromptEl.textContent = extra.prompt || '请依次点击图中文字';
        this._clickImgEl.src = this._imgSrc(data.imageBase64);
        this._clickCount = extra.clickCount || 0;
        this._clickImgWidth = extra.width || 0;
        this._clickImgHeight = extra.height || 0;
        this._clickProgressEl.textContent = '已点击 0 / ' + this._clickCount;
        this._clickPoints = [];
        // 清除旧的点击标记
        const oldMarks = this._clickAreaEl.querySelectorAll('.jc-click-mark');
        oldMarks.forEach((m) => m.remove());

        // 舞台基准 = 图片原始像素宽度，使点击坐标换算与缩放解耦
        if (this._clickImgWidth > 0) {
            this._clickImgEl.style.width = this._clickImgWidth + 'px';
            if (this._clickImgHeight > 0) {
                this._clickImgEl.style.height = this._clickImgHeight + 'px';
            }
            this._setStageBaseWidth(this._clickImgWidth);
        }
    }

    // ========== 工具方法 ==========

    /**
     * 将 Base64 字符串转换为可用的 img src
     * 兼容完整 data URI 和纯 Base64 两种格式
     */
    _imgSrc(base64) {
        if (!base64) return '';
        if (base64.startsWith('data:')) return base64;
        return 'data:image/png;base64,' + base64;
    }

    _showLoading() {
        if (this._loadingEl) this._loadingEl.style.display = 'block';
        if (this._errorEl) this._errorEl.style.display = 'none';
        if (this._contentEl) this._contentEl.style.display = 'none';
    }

    _showError(msg) {
        if (this._errorEl) {
            this._errorEl.textContent = msg;
            this._errorEl.style.display = 'block';
        }
        if (this._loadingEl) this._loadingEl.style.display = 'none';
        if (this._contentEl) this._contentEl.style.display = 'none';
    }

    _hideLoading() {
        if (this._loadingEl) this._loadingEl.style.display = 'none';
        if (this._errorEl) this._errorEl.style.display = 'none';
        if (this._contentEl) this._contentEl.style.display = 'block';
    }

    _showResult(success, msg) {
        if (!this._resultEl) return;
        this._resultEl.style.display = 'block';
        if (success) {
            this._resultEl.innerHTML =
                '<span class="jc-result-chip jc-result-ok">' + msg + '</span>';
        } else {
            this._resultEl.innerHTML =
                '<span class="jc-result-chip jc-result-fail">' + msg + '</span>';
        }
    }

    _clearResult() {
        if (this._resultEl) {
            this._resultEl.style.display = 'none';
            this._resultEl.innerHTML = '';
        }
    }

    /**
     * 获取当前用户输入的原始明文（未加密）
     * @returns {string}
     */
    _getRawInput() {
        switch (this.options.type) {
            case 'number':
            case 'arithmetic':
                return this._inputEl ? this._inputEl.value.trim() : '';

            case 'slider': {
                const value = this._dragController ? this._dragController.getValue() : 0;
                const trajectory = this._dragController ? this._dragController.getTrajectory() : [];
                return JSON.stringify({ value: value, trajectory: trajectory });
            }

            case 'rotate': {
                const value = this._dragController ? this._dragController.getValue() : 0;
                const trajectory = this._dragController ? this._dragController.getTrajectory() : [];
                return JSON.stringify({ value: value, trajectory: trajectory });
            }

            case 'click':
                return JSON.stringify({ clicks: this._clickPoints || [] });

            default:
                return '';
        }
    }

    // ========== 重置 UI 状态 ==========

    _resetUI() {
        if (this._inputEl) this._inputEl.value = '';
        if (this._dragController) this._dragController.reset();
        if (this._rotateCircleEl) this._rotateCircleEl.style.transform = 'rotate(0deg)';
        if (this._rotateAngleEl) this._rotateAngleEl.textContent = '当前角度：0\u00b0';
        if (this._sliderBlockEl) this._sliderBlockEl.style.left = '0px';
        if (this._clickPoints) this._clickPoints = [];
        if (this._clickAreaEl) {
            const marks = this._clickAreaEl.querySelectorAll('.jc-click-mark');
            marks.forEach((m) => m.remove());
        }
        if (this._clickProgressEl) this._clickProgressEl.textContent = '已点击 0 / 0';
        this._clearResult();
        // 恢复交互
        this._setInteractionEnabled(true);
    }

    /**
     * 启用或禁用所有验证码交互（拖动、输入、点击）。
     * <p>
     * 验证通过或失败后调用 {@code _setInteractionEnabled(false)}，
     * 刷新时通过 {@link #_resetUI()} 恢复为 {@code true}。
     *
     * @param enabled true=启用交互, false=禁用交互
     */
    _setInteractionEnabled(enabled) {
        // 文本输入框
        if (this._inputEl) {
            this._inputEl.readOnly = !enabled;
            this._inputEl.style.pointerEvents = enabled ? '' : 'none';
        }
        // 拖动滑块 / 旋转圆盘手柄
        if (this._dragController && this._dragController.setEnabled) {
            this._dragController.setEnabled(enabled);
        }
        // 点击区域
        if (this._clickAreaEl) {
            this._clickAreaEl.style.pointerEvents = enabled ? '' : 'none';
        }
        // 刷新按钮始终可用
    }

    // ========== 公开 API ==========

    /**
     * 显示验证码。
     * <p>
     * 弹层模式下会打开全屏遮罩、锁定页面滚动，并在首次打开时才真正拉取验证码
     * （避免用户从未打开弹层却白白消耗一次验证码额度）。
     *
     * @returns {Captcha} this（链式）
     */
    show() {
        if (this._destroyed || this._visible) return this;
        this._visible = true;
        this._clearAutoClose();

        if (this._wrapper) this._wrapper.style.display = 'block';

        if (this.options.modal && this._overlayEl) {
            this._clearOverlayHideTimer();
            this._overlayEl.style.display = 'flex';
            this._lockBodyScroll();
        }

        // 首次打开才加载：弹层模式的延迟加载；若已完成过一轮则重新来一张
        if (!this._loaded) {
            this._loadCaptcha();
        } else if (this._completed) {
            this.refresh();
        }

        // 隐藏时 clientWidth 为 0 无法计算缩放，等一帧布局稳定后再算
        this._afterFrame(() => {
            // 展开类必须在 display:flex 生效之后的下一帧才加：同一帧内浏览器会
            // 合并样式计算，opacity/transform 过渡不会触发，遮罩将停在
            // opacity:0 + translateY(12px)（视觉上完全不可见且不居中）。
            if (this._visible && this.options.modal && this._overlayEl) {
                this._overlayEl.classList.add('jc-overlay-open');
            }
            this._updateStageScale();
            this._focusFirstInput();
        });

        this._emit('show');
        return this;
    }

    /**
     * 隐藏验证码。
     * <p>
     * 弹层模式下关闭遮罩并解除页面滚动锁定。
     *
     * @returns {Captcha} this（链式）
     */
    hide() {
        if (this._destroyed || !this._visible) return this;
        this._visible = false;
        this._clearAutoClose();

        if (this.options.modal) {
            if (this._overlayEl) {
                const overlay = this._overlayEl;
                overlay.classList.remove('jc-overlay-open');
                // 等淡出过渡跑完再移出渲染树；直接 display:none 会让遮罩瞬间闪断
                this._clearOverlayHideTimer();
                this._overlayHideTimer = setTimeout(() => {
                    this._overlayHideTimer = null;
                    if (!this._visible) overlay.style.display = 'none';
                }, Captcha.OVERLAY_TRANSITION_MS);
            }
            this._unlockBodyScroll();
        } else if (this._wrapper) {
            this._wrapper.style.display = 'none';
        }

        this._emit('hide');
        return this;
    }

    // ========== 弹层辅助 ==========

    /** 取消待执行的遮罩隐藏定时器（淡出未完成时又被重新打开） */
    _clearOverlayHideTimer() {
        if (this._overlayHideTimer) {
            clearTimeout(this._overlayHideTimer);
            this._overlayHideTimer = null;
        }
    }

    /**
     * 锁定 body 滚动。
     * <p>
     * 用全局计数而非布尔标记：页面同时存在多个验证码弹层时，
     * 只有最后一个关闭才解锁，避免先关的那个把后开的滚动锁提前解除。
     */
    _lockBodyScroll() {
        if (this._bodyLocked) return;
        this._bodyLocked = true;
        Captcha._modalOpenCount = (Captcha._modalOpenCount || 0) + 1;
        if (Captcha._modalOpenCount === 1) {
            document.body.classList.add('jc-body-locked');
        }
    }

    _unlockBodyScroll() {
        if (!this._bodyLocked) return;
        this._bodyLocked = false;
        Captcha._modalOpenCount = Math.max(0, (Captcha._modalOpenCount || 1) - 1);
        if (Captcha._modalOpenCount === 0) {
            document.body.classList.remove('jc-body-locked');
        }
    }

    /**
     * 验证完成后按 {@code autoCloseDelay} 自动关闭弹层。
     * 仅弹层模式生效；{@code autoCloseDelay: 0 | false} 表示不自动关闭。
     */
    _scheduleAutoClose() {
        if (!this.options.modal || !this._visible) return;
        const delay = this.options.autoCloseDelay;
        if (!delay || delay <= 0) return;

        this._clearAutoClose();
        this._autoCloseTimer = setTimeout(() => {
            this._autoCloseTimer = null;
            if (!this._destroyed) this.hide();
        }, delay);
    }

    _clearAutoClose() {
        if (this._autoCloseTimer) {
            clearTimeout(this._autoCloseTimer);
            this._autoCloseTimer = null;
        }
    }

    /**
     * 弹层打开后把焦点移到输入框（仅字符型验证码，且移动端不自动聚焦，
     * 避免软键盘瞬间弹起顶掉刚显示的验证码图片）。
     */
    _focusFirstInput() {
        if (!this.options.autoFocus || !this._inputEl || !this._visible) return;
        if (Captcha._isTouchDevice()) return;
        try { this._inputEl.focus(); } catch (e) { /* 忽略 */ }
    }

    /**
     * 等待一帧后执行（布局稳定后再读取尺寸）。
     * @param {Function} fn 回调
     */
    _afterFrame(fn) {
        if (typeof requestAnimationFrame === 'function') {
            requestAnimationFrame(() => { if (!this._destroyed) fn(); });
        } else {
            setTimeout(() => { if (!this._destroyed) fn(); }, 16);
        }
    }

    /**
     * 是否为触摸设备（仅用于交互细节优化，不做功能分支 —— 
     * 鼠标与触摸事件始终同时绑定，保证同一份代码跨端可用）。
     * @returns {boolean}
     */
    static _isTouchDevice() {
        return (typeof window !== 'undefined')
            && ('ontouchstart' in window || (navigator && navigator.maxTouchPoints > 0));
    }

    /**
     * 刷新验证码（重新生成并渲染）。
     * 触发 beforeGet → 加载 → afterGet 事件链。
     */
    refresh() {
        // 防止并发刷新（快速点击刷新按钮、定时器+手动刷新同时触发等）
        if (this._refreshing) return;
        this._refreshing = true;

        // 重置完成状态
        this._completed = false;
        this._key = null;
        this._captchaKey = null;

        this._resetUI();
        var self = this;
        this._loadCaptcha().finally(function() {
            self._refreshing = false;
        });
    }

    // ========== 事件系统 ==========

    /**
     * 注册事件监听器。
     * <p>
     * 支持的事件：
     * <ul>
     *   <li>{@code beforeGet} — 获取验证码前（含刷新），参数：{@code (type)}</li>
     *   <li>{@code afterGet} — 验证码加载并渲染完成后，参数：{@code (key, captchaData)}</li>
     *   <li>{@code complete} — 用户完成前端验证操作，参数：{@code (key, captchaInput)}</li>
     * </ul>
     * 其中 {@code key} 为后端下发的<b>合并凭证</b>（格式 {@code type.captchaKey}），
     * 校验时只需把它和 {@code captchaInput} 两个值随业务表单一起提交。
     *
     * @param {string} event 事件名
     * @param {Function} callback 回调函数
     * @returns {Captcha} this（链式）
     */
    on(event, callback) {
        if (typeof callback !== 'function') return this;
        if (!this._eventListeners[event]) {
            this._eventListeners[event] = [];
        }
        this._eventListeners[event].push(callback);
        return this;
    }

    /**
     * 移除事件监听器。
     *
     * @param {string} event 事件名
     * @param {Function} callback 要移除的回调函数（不传则移除该事件的所有监听器）
     * @returns {Captcha} this（链式）
     */
    off(event, callback) {
        if (!this._eventListeners[event]) return this;
        if (!callback) {
            this._eventListeners[event] = [];
        } else {
            this._eventListeners[event] = this._eventListeners[event].filter(
                function(fn) { return fn !== callback; }
            );
        }
        return this;
    }

    /**
     * 触发事件，调用所有注册的监听器。
     */
    _emit(event) {
        var listeners = this._eventListeners[event];
        if (!listeners) return;
        // 从第二个参数开始传递给回调
        var args = Array.prototype.slice.call(arguments, 1);
        for (var i = 0; i < listeners.length; i++) {
            try {
                listeners[i].apply(null, args);
            } catch (e) {
                console.error('[Captcha] 事件监听器异常 (' + event + '):', e);
            }
        }
    }

    // ========== 数据获取 ==========

    /**
     * 获取当前<b>合并凭证</b>（格式 {@code type.captchaKey}）。
     * <p>
     * 这是校验接口唯一需要的凭证参数，配合 {@link getCaptchaInput} 的返回值
     * 即可完成校验：{@code POST /api/captcha/verify { key, input }}。
     *
     * @returns {string|null}
     */
    getKey() {
        return this._key;
    }

    /**
     * 获取当前 captchaKey（不含类型前缀）。
     *
     * @deprecated 请改用 {@link getKey}——合并凭证已包含类型信息，
     *             单独的 captchaKey 需要额外传 type，属于两段式旧用法。
     * @returns {string|null}
     */
    getCaptchaKey() {
        return this._captchaKey;
    }

    /**
     * 获取用户输入（加密后）。
     * 业务方可在 complete 事件回调中调用，或在外部主动调用来获取加密后的验证参数。
     * @returns {Promise<string>} Base64 编码的加密密文
     */
    async getCaptchaInput() {
        const raw = this._getRawInput();
        return await JaravelCaptcha.encrypt(
            raw, this._effectiveEncType, this.options.encryptionKey
        );
    }

    /**
     * 用户完成前端验证操作时内部调用。
     * <p>
     * 不提交到后端，不判断准确与否。仅触发 complete 事件，参数为
     * {@code (key, encryptedInput)}——即「合并凭证 + 加密后的用户输入」两个值，
     * 由业务方决定后续处理（推荐随业务表单一次性提交，服务端一并校验）。
     */
    async _complete() {
        // 防止重复完成
        if (this._completed) return;
        if (!this._key) {
            console.warn('[Captcha] 验证码尚未加载');
            return;
        }

        // 检查输入是否为空（仅文本类验证码）
        const raw = this._getRawInput();
        if (this.options.type === 'number' || this.options.type === 'arithmetic') {
            if (!raw) {
                console.warn('[Captcha] 请输入验证码');
                this._showResult(false, '请输入验证码');
                return;
            }
        }

        this._completed = true;
        // 禁用交互，防止修改已完成的数据
        this._setInteractionEnabled(false);

        // 加密用户输入
        const encryptedInput = await JaravelCaptcha.encrypt(
            raw, this._effectiveEncType, this.options.encryptionKey
        );

        this._showResult(true, '已完成');

        // 触发 complete 事件，只传递已处理的参数（不含原始明文）
        // 第一个参数为「合并凭证 key」，业务方把它和 input 一起随业务表单提交即可
        this._emit('complete', this._key, encryptedInput);

        // 弹层模式：完成后短暂停留展示结果，再自动收起
        this._scheduleAutoClose();
    }

    /**
     * 销毁组件，清理 DOM 和事件监听器
     */
    destroy() {
        if (this._destroyed) return;
        this._destroyed = true;

        // 自动关闭定时器
        this._clearAutoClose();
        // 遮罩淡出定时器（淡出途中被 destroy，回调会摸到已移除的节点）
        this._clearOverlayHideTimer();

        // 解除 body 滚动锁（弹层未关就 destroy 时，页面会永久锁死滚动）
        this._unlockBodyScroll();

        // 断开尺寸观察，否则 ResizeObserver 会持有已移除 DOM 的引用造成泄漏
        if (this._resizeObserver) {
            try { this._resizeObserver.disconnect(); } catch (e) { /* 忽略 */ }
            this._resizeObserver = null;
        }

        // 移除所有托管的事件监听器（含 window resize / document keydown-ESC / 拖动监听）
        this._listeners.forEach(({ el, event, handler, options }) => {
            el.removeEventListener(event, handler, options);
        });
        this._listeners = [];

        // 清空事件监听器
        this._eventListeners = {};

        // 移除验证码 UI
        if (this._wrapper && this._wrapper.parentNode) {
            this._wrapper.parentNode.removeChild(this._wrapper);
        }

        // 移除挂在 body 上的全屏遮罩（弹层模式下它不在业务容器内，不会被上面的清理带走）
        if (this._overlayEl && this._overlayEl.parentNode) {
            this._overlayEl.parentNode.removeChild(this._overlayEl);
        }

        // 恢复容器原有内容
        if (this._originalContentWrap && this._originalContentWrap.parentNode) {
            const container = this.container;
            while (this._originalContentWrap.firstChild) {
                container.appendChild(this._originalContentWrap.firstChild);
            }
            container.removeChild(this._originalContentWrap);
        }

        // 清空引用
        this._wrapper = null;
        this._contentEl = null;
        this._loadingEl = null;
        this._errorEl = null;
        this._resultEl = null;
        this._imgEl = null;
        this._inputEl = null;
        this._refreshBtnEl = null;
        this._sliderBgEl = null;
        this._sliderBlockEl = null;
        this._sliderContainerEl = null;
        this._sliderTrackEl = null;
        this._sliderHandleEl = null;
        this._rotateBgEl = null;
        this._rotateCircleEl = null;
        this._rotateContainerEl = null;
        this._rotateTrackEl = null;
        this._rotateHandleEl = null;
        this._rotateAngleEl = null;
        this._clickPromptEl = null;
        this._clickAreaEl = null;
        this._clickImgEl = null;
        this._clickProgressEl = null;
        this._dragController = null;
        this._captchaData = null;
        this._key = null;
        this._captchaKey = null;
        this._clickPoints = [];
        this._overlayEl = null;
        this._modalEl = null;
        this._modalCloseEl = null;
        this._stageEl = null;
        this._stageWrapEl = null;
        this._escHandler = null;
    }
}

/**
 * 遮罩淡入淡出时长（毫秒），与 CSS 中 .jc-overlay 的 transition 保持一致。
 * 关闭时据此延迟移出渲染树，避免动画被 display:none 打断。
 */
Captcha.OVERLAY_TRANSITION_MS = 200;


// ====================================================================
// 导出
// ====================================================================

if (typeof window !== 'undefined') {
    window.JaravelCaptcha = JaravelCaptcha;
    window.Captcha = Captcha;
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { JaravelCaptcha, Captcha };
}
