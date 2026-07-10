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
 *       apiUrl: '/api/captcha/generate',
 *       verifyUrl: '/api/captcha/verify',
 *       encryptionType: 'aes',
 *       encryptionKey: 'my-secret-key',
 *       onSuccess: (captchaKey, captchaInput) => { ... },
 *       onFail: () => { ... }
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
     * @returns {Promise<Object>} { captchaKey, type, imageBase64, expireTime, extra }
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
     * 提交验证码验证
     * @param {string} apiUrl - 验证 API 地址
     * @param {string} type - 验证码类型
     * @param {string} captchaKey - 生成时返回的 captchaKey
     * @param {string} input - 用户输入（明文）
     * @param {string} encType - 加密类型
     * @param {string} encKey - 加密密钥
     * @returns {Promise<boolean>} 验证是否通过
     */
    async function submitCaptcha(apiUrl, type, captchaKey, input, encType, encKey) {
        const encryptedInput = await encrypt(input, encType, encKey);
        const resp = await fetch(apiUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                type: type,
                captchaKey: captchaKey,
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
        this._captchaKey = null;
        this._captchaData = null;
        this._destroyed = false;
        this._verifying = false;
        this._refreshing = false;       // 刷新中标记，防止并发刷新
        this._captchaUsed = false;      // 验证码已被使用（验证成功或失败后置 true，刷新时重置）
        this._visible = true;
        this._listeners = [];       // 所有托管的事件监听器
        this._clickPoints = [];     // click 类型的点击坐标
        this._failRefreshTimer = null;  // 验证失败后的延迟刷新定时器

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

        // 加载验证码
        this._loadCaptcha();
    }

    // ========== 选项合并 ==========

    _mergeOptions(options) {
        const opts = options || {};
        return {
            type: (opts.type || 'number').toLowerCase(),
            apiUrl: opts.apiUrl || '/api/captcha/generate',
            // verifyUrl 仅在 autoVerify=true 时使用；autoVerify=false 时可省略
            verifyUrl: opts.verifyUrl || '/api/captcha/verify',
            encryptionType: (opts.encryptionType || 'none').toLowerCase(),
            encryptionKey: opts.encryptionKey || null,
            autoVerify: opts.autoVerify !== false,  // 默认 true
            onSuccess: typeof opts.onSuccess === 'function' ? opts.onSuccess : null,
            onFail: typeof opts.onFail === 'function' ? opts.onFail : null,
            onComplete: typeof opts.onComplete === 'function' ? opts.onComplete : null
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
    max-width: 360px;
    padding: 16px;
    background: #f5f5f5;
    border-radius: 8px;
    box-sizing: border-box;
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
    display: inline-block;
    width: 300px;
    height: 150px;
}
.jc-slider-bg {
    width: 300px;
    height: 150px;
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
    width: 300px;
    height: 36px;
    background: #e0e0e0;
    border-radius: 18px;
    margin: 12px auto;
    user-select: none;
    -webkit-user-select: none;
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
    display: inline-block;
    cursor: pointer;
}
.jc-click-img {
    border: 1px solid #e0e0e0;
    border-radius: 4px;
    display: block;
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
        if (!this._visible) wrapper.style.display = 'none';

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

        this.container.appendChild(wrapper);
        this._wrapper = wrapper;
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

        content.appendChild(container);
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

        content.appendChild(track);
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

        content.appendChild(container);
        this._rotateContainerEl = container;

        // 角度文字
        const angleText = document.createElement('p');
        angleText.className = 'jc-rotate-angle';
        angleText.textContent = '当前角度：0\u00b0';
        content.appendChild(angleText);
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

        content.appendChild(track);
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

        const area = document.createElement('div');
        area.className = 'jc-click-area';

        const img = document.createElement('img');
        img.className = 'jc-click-img';
        img.alt = '点选验证码';
        area.appendChild(img);
        this._clickImgEl = img;

        content.appendChild(area);
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
    }

    // ----- 数字 / 算术事件 -----

    _attachNumberArithmeticEvents() {
        // 按 Enter 自动验证
        this._addManagedListener(this._inputEl, 'keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.verify();
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
                    // 松开自动验证
                    this.verify();
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
                    // 松开自动验证
                    this.verify();
                }
            }
        );
    }

    // ----- 文字点选事件 -----

    _attachClickEvents() {
        this._clickCount = 0;
        this._clickImgWidth = 0;
        this._clickImgHeight = 0;

        this._addManagedListener(this._clickImgEl, 'click', (e) => {
            if (!this._captchaData) return;
            if (this._clickPoints.length >= this._clickCount) return;

            const rect = this._clickImgEl.getBoundingClientRect();
            const displayX = e.clientX - rect.left;
            const displayY = e.clientY - rect.top;

            const imgWidth = this._clickImgWidth || this._clickImgEl.naturalWidth || rect.width;
            const imgHeight = this._clickImgHeight || this._clickImgEl.naturalHeight || rect.height;
            const scaleX = imgWidth / rect.width;
            const scaleY = imgHeight / rect.height;
            const realX = Math.round(displayX * scaleX);
            const realY = Math.round(displayY * scaleY);

            this._clickPoints.push({ x: realX, y: realY });

            // 添加视觉标记
            const mark = document.createElement('div');
            mark.className = 'jc-click-mark';
            mark.style.left = (displayX - 15) + 'px';
            mark.style.top = (displayY - 15) + 'px';
            mark.textContent = this._clickPoints.length;
            this._clickAreaEl.appendChild(mark);

            // 更新进度
            this._clickProgressEl.textContent =
                '已点击 ' + this._clickPoints.length + ' / ' + this._clickCount;

            // 达到所需点击数后自动验证
            if (this._clickPoints.length >= this._clickCount) {
                this.verify();
            }
        });
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
            const dx = clientX - startClientX;
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
        try {
            const url = this.options.apiUrl + '?type=' + encodeURIComponent(this.options.type);
            const resp = await fetch(url);
            const json = await resp.json();
            if (json.code !== 200) {
                throw new Error(json.msg || '生成验证码失败');
            }
            this._captchaData = json.data;
            this._captchaKey = json.data.captchaKey;
            this._renderCaptcha(json.data);
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
        this._sliderBgEl.src = this._imgSrc(data.imageBase64);
        this._sliderBlockEl.src = this._imgSrc(extra.sliderImage);
        this._sliderBlockEl.style.top = (extra.gapY || 0) + 'px';
        this._sliderBlockEl.style.left = '0px';
        this._sliderBlockSize = extra.blockSize || 40;
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
     * 显示验证码
     */
    show() {
        this._visible = true;
        if (this._wrapper) this._wrapper.style.display = 'block';
    }

    /**
     * 隐藏验证码
     */
    hide() {
        this._visible = false;
        if (this._wrapper) this._wrapper.style.display = 'none';
    }

    /**
     * 刷新验证码（重新生成并渲染）
     */
    refresh() {
        // 防止并发刷新（快速点击刷新按钮、定时器+手动刷新同时触发等）
        if (this._refreshing) return;
        this._refreshing = true;

        // 取消验证失败后挂起的延迟刷新，避免手动刷新后又被定时器二次刷新
        if (this._failRefreshTimer) {
            clearTimeout(this._failRefreshTimer);
            this._failRefreshTimer = null;
        }

        // 重置验证状态
        this._captchaUsed = false;
        this._captchaKey = null;

        this._resetUI();
        var self = this;
        this._loadCaptcha().finally(function() {
            self._refreshing = false;
        });
    }

    /**
     * 获取当前 captchaKey
     * @returns {string|null}
     */
    getCaptchaKey() {
        return this._captchaKey;
    }

    /**
     * 获取用户输入（加密后）
     * @returns {Promise<string>} Base64 编码的加密密文
     */
    async getCaptchaInput() {
        const raw = this._getRawInput();
        return await JaravelCaptcha.encrypt(
            raw, this._effectiveEncType, this.options.encryptionKey
        );
    }

    /**
     * 触发验证或完成回调。
     * - autoVerify=true（默认）：调用 verifyUrl 进行服务端验证，成功后调用 onSuccess
     * - autoVerify=false：不调用服务端验证，直接调用 onComplete 返回 captchaKey 和加密后的 input
     * @returns {Promise<boolean|void>}
     */
    async verify() {
        // 防止重复验证
        if (this._verifying) return false;

        // 验证码已被使用（成功或失败后），拒绝再次验证
        if (this._captchaUsed) {
            console.warn('[Captcha] 验证码已被使用，请刷新后重试');
            return false;
        }

        // 检查验证码是否已加载
        if (!this._captchaKey) {
            console.warn('[Captcha] 验证码尚未加载');
            if (this.options.onFail) this.options.onFail();
            return false;
        }

        // 检查输入是否为空（仅文本类验证码）
        const raw = this._getRawInput();
        if (this.options.type === 'number' || this.options.type === 'arithmetic') {
            if (!raw) {
                console.warn('[Captcha] 请输入验证码');
                this._showResult(false, '请输入验证码');
                if (this.options.onFail) this.options.onFail();
                return false;
            }
        }

        this._verifying = true;
        this._captchaUsed = true;  // 标记为已使用，防止 verify 完成前被重复调用
        try {
            // 加密用户输入
            const encryptedInput = await JaravelCaptcha.encrypt(
                raw, this._effectiveEncType, this.options.encryptionKey
            );

            // autoVerify=false 模式：不调用服务端验证，直接回调
            if (!this.options.autoVerify) {
                if (this.options.onComplete) {
                    this.options.onComplete(this._captchaKey, encryptedInput);
                }
                this._showResult(true, '已完成');
                this._setInteractionEnabled(false);
                return true;
            }

            // autoVerify=true 模式：调用服务端验证
            const resp = await fetch(this.options.verifyUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    type: this.options.type,
                    captchaKey: this._captchaKey,
                    input: encryptedInput
                })
            });

            const json = await resp.json();

            if (json.code === 200) {
                // 验证通过 — 禁用交互
                this._showResult(true, json.msg || '验证通过');
                this._setInteractionEnabled(false);
                if (this.options.onSuccess) {
                    this.options.onSuccess(this._captchaKey, encryptedInput);
                }
                return true;
            } else if (json.code === 410) {
                // 验证码已被使用（一次性消费）— 禁用交互，立即刷新
                this._showResult(false, json.msg || '验证码已失效');
                this._setInteractionEnabled(false);
                if (this.options.onFail) this.options.onFail();
                var self = this;
                this._failRefreshTimer = setTimeout(function() {
                    self._failRefreshTimer = null;
                    self.refresh();
                }, 800);
                return false;
            } else {
                // 验证失败 — 禁用交互，2秒后自动刷新
                this._showResult(false, json.msg || '验证失败');
                this._setInteractionEnabled(false);
                if (this.options.onFail) this.options.onFail();
                var self = this;
                this._failRefreshTimer = setTimeout(function() {
                    self._failRefreshTimer = null;
                    self.refresh();
                }, 2000);
                return false;
            }
        } catch (e) {
            console.error('[Captcha] 验证请求失败:', e);
            this._showResult(false, '请求失败: ' + e.message);
            this._setInteractionEnabled(false);
            if (this.options.onFail) this.options.onFail();
            var self = this;
            this._failRefreshTimer = setTimeout(function() {
                self._failRefreshTimer = null;
                self.refresh();
            }, 2000);
            return false;
        } finally {
            this._verifying = false;
        }
    }

    /**
     * 销毁组件，清理 DOM 和事件监听器
     */
    destroy() {
        if (this._destroyed) return;
        this._destroyed = true;

        // 取消挂起的延迟刷新定时器
        if (this._failRefreshTimer) {
            clearTimeout(this._failRefreshTimer);
            this._failRefreshTimer = null;
        }

        // 移除所有托管的事件监听器
        this._listeners.forEach(({ el, event, handler, options }) => {
            el.removeEventListener(event, handler, options);
        });
        this._listeners = [];

        // 移除验证码 UI
        if (this._wrapper && this._wrapper.parentNode) {
            this._wrapper.parentNode.removeChild(this._wrapper);
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
        this._captchaKey = null;
        this._clickPoints = [];
    }
}


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
