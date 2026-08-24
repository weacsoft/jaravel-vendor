/**
 * AetherUpload 前端上传组件（无依赖，原生 JS）。
 *
 * 能力：
 *  - 任意大小文件分片上传（分片大小前端可配，默认取后端组配置，缺省 1MB）
 *  - 百分比进度回调（onProgress）
 *  - 前端文件类型 / 大小限制（自动同步后端组配置，也可本地覆盖）
 *  - base64 分片传输（规避安全软件拦截二进制；由后端组配置或本地选项开启）
 *  - 断点续传 / 断线续传（identifier 定位既有任务，跳过已传分片；支持 pause/resume）
 *  - 上传完成 / 失败 / 中止事件回调
 *
 * 用法：
 *   var uploader = new AetherUploader({
 *     endpoint: '/aetherupload',   // 路由前缀（与后端 register 的前缀一致）
 *     group: 'file',               // 上传组
 *     // chunkSize: 2 * 1024 * 1024,  // 可选：覆盖分片大小（后端组需允许）
 *     // base64: true,                // 可选：强制 base64 传输
 *     // concurrency: 3,              // 可选：并发分片数，默认 3
 *     onProgress: function (percent, info) {},
 *     onSuccess:  function (result) {},
 *     onError:    function (error) {},
 *     onAbort:    function () {}
 *   });
 *   uploader.upload(file);          // file: File 对象
 *   uploader.pause();               // 暂停（断点）
 *   uploader.resume();              // 继续（从断点续传）
 *   uploader.abort();               // 中止并清理服务端任务
 */
(function (global) {
    'use strict';

    function AetherUploader(options) {
        options = options || {};
        this.endpoint = (options.endpoint || '/aetherupload').replace(/\/+$/, '');
        this.group = options.group || 'file';
        this.localChunkSize = options.chunkSize || null;
        this.localBase64 = typeof options.base64 === 'boolean' ? options.base64 : null;
        this.concurrency = options.concurrency || 3;
        this.headers = options.headers || {};
        // 本地限制（为 null 时使用后端组配置）
        this.allowedExtensions = options.allowedExtensions || null;
        this.maxSize = options.maxSize || null;

        this.onProgress = options.onProgress || function () {};
        this.onSuccess = options.onSuccess || function () {};
        this.onError = options.onError || function () {};
        this.onAbort = options.onAbort || function () {};

        this._reset();
    }

    AetherUploader.prototype._reset = function () {
        this.file = null;
        this.resourceId = null;
        this.chunkSize = 1024 * 1024;
        this.totalChunks = 0;
        this.uploaded = {};       // index -> true
        this.uploadedCount = 0;
        this.paused = false;
        this.aborted = false;
        this.running = 0;
        this.nextIndex = 0;
        this.serverConfig = null;
        this.useBase64 = false;
    };

    // ---------- HTTP 工具 ----------

    AetherUploader.prototype._url = function (action) {
        return this.endpoint + '/' + encodeURIComponent(this.group) + '/' + action;
    };

    AetherUploader.prototype._request = function (method, action, body, isForm) {
        var self = this;
        var url = this._url(action);
        if (method === 'GET' && body) {
            var qs = Object.keys(body).map(function (k) {
                return encodeURIComponent(k) + '=' + encodeURIComponent(body[k]);
            }).join('&');
            url += (url.indexOf('?') < 0 ? '?' : '&') + qs;
            body = null;
        }
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open(method, url, true);
            Object.keys(self.headers).forEach(function (k) {
                xhr.setRequestHeader(k, self.headers[k]);
            });
            xhr.onload = function () {
                var json = null;
                try { json = JSON.parse(xhr.responseText); } catch (e) { /* ignore */ }
                if (xhr.status >= 200 && xhr.status < 300 && json && json.code === 0) {
                    resolve(json.data);
                } else {
                    reject(new Error((json && json.message) || ('HTTP ' + xhr.status)));
                }
            };
            xhr.onerror = function () { reject(new Error('网络错误')); };
            if (body instanceof FormData) {
                xhr.send(body);
            } else if (body) {
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
                xhr.send(Object.keys(body).map(function (k) {
                    return encodeURIComponent(k) + '=' + encodeURIComponent(body[k]);
                }).join('&'));
            } else {
                xhr.send();
            }
        });
    };

    // ---------- 配置与校验 ----------

    AetherUploader.prototype._loadConfig = function () {
        var self = this;
        if (self.serverConfig) {
            return Promise.resolve(self.serverConfig);
        }
        return self._request('GET', 'config', { group: self.group }).then(function (cfg) {
            self.serverConfig = cfg;
            return cfg;
        });
    };

    AetherUploader.prototype._validate = function (file, cfg) {
        var maxSize = this.maxSize !== null ? this.maxSize : (cfg.maxSize || 0);
        if (maxSize > 0 && file.size > maxSize) {
            throw new Error('文件大小超过限制（' + maxSize + ' 字节）');
        }
        var exts = this.allowedExtensions !== null ? this.allowedExtensions : (cfg.allowedExtensions || []);
        if (exts.length > 0) {
            var dot = file.name.lastIndexOf('.');
            var ext = dot < 0 ? '' : file.name.substring(dot + 1).toLowerCase();
            var ok = exts.some(function (e) { return String(e).toLowerCase().replace('.', '') === ext; });
            if (!ok) {
                throw new Error('文件类型不允许（.' + ext + '）');
            }
        }
        var mimes = cfg.allowedMimeTypes || [];
        if (mimes.length > 0 && file.type) {
            var mt = file.type.toLowerCase();
            var mimeOk = mimes.some(function (m) {
                m = String(m).toLowerCase();
                return m.slice(-2) === '/*' ? mt.indexOf(m.slice(0, -1)) === 0 : mt === m;
            });
            if (!mimeOk) {
                throw new Error('MIME 类型不允许（' + file.type + '）');
            }
        }
    };

    // ---------- 上传主流程 ----------

    /**
     * 开始上传（自动断点续传：同一文件重新调用会从上次进度继续）。
     */
    AetherUploader.prototype.upload = function (file) {
        var self = this;
        self._reset();
        self.file = file;
        self._loadConfig().then(function (cfg) {
            self._validate(file, cfg);
            self.useBase64 = self.localBase64 !== null ? self.localBase64 : !!cfg.base64;
            var wantChunk = (self.localChunkSize && cfg.allowClientChunkSize)
                ? self.localChunkSize : cfg.chunkSize;
            // identifier：文件名 + 大小 + 最后修改时间，定位断线前的任务
            var identifier = [file.name, file.size, file.lastModified || 0].join('-');
            var params = {
                group: self.group,
                filename: file.name,
                size: file.size,
                mimeType: file.type || '',
                identifier: identifier
            };
            if (self.localChunkSize && cfg.allowClientChunkSize) {
                params.chunkSize = wantChunk;
            }
            return self._request('POST', 'prepare', params);
        }).then(function (data) {
            self.resourceId = data.resourceId;
            self.chunkSize = data.chunkSize;
            self.totalChunks = data.totalChunks;
            (data.uploadedChunks || []).forEach(function (i) { self.uploaded[i] = true; });
            self.uploadedCount = (data.uploadedChunks || []).length;
            if (data.completed) {
                self.onProgress(100, data);
                self.onSuccess(data);
                return;
            }
            self._emitProgress(null);
            self._pump();
        }).catch(function (err) {
            self.onError(err);
        });
    };

    /** 暂停上传（保留服务端进度，可 resume） */
    AetherUploader.prototype.pause = function () {
        this.paused = true;
    };

    /** 从断点继续上传 */
    AetherUploader.prototype.resume = function () {
        if (!this.file || this.aborted) {
            return;
        }
        if (this.resourceId) {
            this.paused = false;
            this._pump();
        } else {
            this.upload(this.file);
        }
    };

    /** 中止上传并清理服务端任务 */
    AetherUploader.prototype.abort = function () {
        var self = this;
        self.aborted = true;
        self.paused = true;
        if (self.resourceId) {
            self._request('POST', 'abort', { group: self.group, resourceId: self.resourceId })
                .then(function () { self.onAbort(); })
                .catch(function () { self.onAbort(); });
        } else {
            self.onAbort();
        }
    };

    /** 查询服务端进度（断线重连后可用于恢复展示） */
    AetherUploader.prototype.progress = function () {
        return this._request('GET', 'progress', { group: this.group, resourceId: this.resourceId });
    };

    // ---------- 分片调度 ----------

    AetherUploader.prototype._pump = function () {
        var self = this;
        if (self.paused || self.aborted) {
            return;
        }
        while (self.running < self.concurrency) {
            var index = self._nextPending();
            if (index === -1) {
                return;
            }
            self._sendChunk(index);
        }
    };

    AetherUploader.prototype._nextPending = function () {
        while (this.nextIndex < this.totalChunks && this.uploaded[this.nextIndex]) {
            this.nextIndex++;
        }
        if (this.nextIndex >= this.totalChunks) {
            return -1;
        }
        var index = this.nextIndex;
        this.nextIndex++;
        return index;
    };

    AetherUploader.prototype._sendChunk = function (index) {
        var self = this;
        self.running++;
        var start = index * self.chunkSize;
        var end = Math.min(start + self.chunkSize, self.file.size);
        var blob = self.file.slice(start, end);

        var send;
        if (self.useBase64) {
            // base64 模式：分片编码为 base64 字符串以普通表单字段传输，
            // 避免二进制流被中间安全软件拦截
            send = self._blobToBase64(blob).then(function (base64) {
                return self._request('POST', 'chunk', {
                    group: self.group,
                    resourceId: self.resourceId,
                    index: index,
                    data: base64
                });
            });
        } else {
            // 二进制模式：multipart/form-data
            var form = new FormData();
            form.append('group', self.group);
            form.append('resourceId', self.resourceId);
            form.append('index', index);
            form.append('file', blob, 'chunk-' + index);
            send = self._request('POST', 'chunk', form, true);
        }

        send.then(function (data) {
            self.running--;
            if (!self.uploaded[index]) {
                self.uploaded[index] = true;
                self.uploadedCount++;
            }
            self._emitProgress(data);
            if (data.completed) {
                self.onSuccess(data);
                return;
            }
            self._pump();
        }).catch(function (err) {
            self.running--;
            if (self.aborted || self.paused) {
                return;
            }
            // 单片失败重试一次；仍失败则回调错误（用户可稍后 resume 断点续传）
            if (!self.uploaded['retry_' + index]) {
                self.uploaded['retry_' + index] = true;
                self._sendChunk(index);
            } else {
                self.paused = true;
                self.onError(err);
            }
        });
    };

    AetherUploader.prototype._emitProgress = function (data) {
        var percent = data && typeof data.percent === 'number'
            ? data.percent
            : Math.round(this.uploadedCount * 10000 / Math.max(this.totalChunks, 1)) / 100;
        this.onProgress(percent, data || {
            uploadedCount: this.uploadedCount,
            totalChunks: this.totalChunks
        });
    };

    AetherUploader.prototype._blobToBase64 = function (blob) {
        return new Promise(function (resolve, reject) {
            var reader = new FileReader();
            reader.onload = function () {
                var result = String(reader.result);
                var comma = result.indexOf(',');
                resolve(comma >= 0 ? result.substring(comma + 1) : result);
            };
            reader.onerror = function () { reject(new Error('读取文件分片失败')); };
            reader.readAsDataURL(blob);
        });
    };

    // ---------- 同步上传（小文件单请求） ----------

    /**
     * 同步上传：单请求整文件。base64 模式下以 base64 字段传输。
     */
    AetherUploader.prototype.uploadSync = function (file) {
        var self = this;
        return self._loadConfig().then(function (cfg) {
            self._validate(file, cfg);
            var useBase64 = self.localBase64 !== null ? self.localBase64 : !!cfg.base64;
            if (useBase64) {
                return self._blobToBase64(file).then(function (base64) {
                    return self._request('POST', 'sync', {
                        group: self.group,
                        filename: file.name,
                        mimeType: file.type || '',
                        data: base64
                    });
                });
            }
            var form = new FormData();
            form.append('group', self.group);
            form.append('file', file, file.name);
            return self._request('POST', 'sync', form, true);
        }).then(function (data) {
            self.onProgress(100, data);
            self.onSuccess(data);
            return data;
        }).catch(function (err) {
            self.onError(err);
            throw err;
        });
    };

    global.AetherUploader = AetherUploader;
})(typeof window !== 'undefined' ? window : this);
