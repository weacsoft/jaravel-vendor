# aether-upload — 不限大小分片上传模块

对齐 Laravel [peinhu/AetherUpload](https://github.com/peinhu/AetherUpload-Laravel) 的大文件上传模块，并在其基础上扩展。

## 能力一览

| 能力 | 说明 |
| --- | --- |
| 任意大小上传 | 分片 + `RandomAccessFile` 偏移写入（稀疏预分配），不占内存、不限大小 |
| 分片大小可配 | 后端组配置 `chunk-size`（默认 1MB）；前端可在 prepare 时自定义（组开关 `allow-client-chunk-size`） |
| 进度 | 前端百分比进度回调 + 后端 `progress` 端点返回进度与已传分片 |
| 类型/大小限制 | 前端（自动同步组配置）与后端（强制）双重校验：扩展名、MIME（支持 `video/*` 通配）、大小 |
| 事件 | `UploadPreparedEvent` / `ChunkUploadedEvent` / `UploadCompletedEvent` / `UploadFailedEvent` / `UploadAbortedEvent`（event 模块分发） |
| 中间件 | 全局 `middleware` 配置 + 组级 `middleware` 配置 + `register()` 附加中间件，三层叠加 |
| 自定义路由 | `AetherUploadRoutes.register("api/upload", ...)` 任意前缀挂载端点 |
| 多组配置 | 每组独立：记录头存储（内存 / cache / redis）、临时目录、保存目录、限制、base64 等 |
| base64 传输 | 组配置或前端开启后分片以 base64 表单字段传输，规避安全软件拦截二进制流 |
| 同步上传 | `sync` 端点单请求整文件（小文件场景） |
| 断点/断线续传 | 记录头存分片位图；同 `identifier` 重新 prepare 直接返回已传分片继续上传；支持 pause/resume |

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>aether-upload</artifactId>
</dependency>
```

自动配置（`AetherUploadAutoConfiguration`）随依赖生效，无需额外注册。

### 2. 挂载路由

在应用的 `RouteServiceProvider`（路由定义处）中：

```java
// 默认前缀（配置 route-prefix，默认 aetherupload）
AetherUploadRoutes.register();

// 或自定义端点前缀 + 附加中间件
AetherUploadRoutes.register("api/upload", "auth:api", "throttle:60,1");
```

生成的端点（每个上传组一套，可叠加组中间件）：

```
GET  {prefix}/{group}/config     组配置（前端限制用）
POST {prefix}/{group}/prepare    创建/恢复上传任务
POST {prefix}/{group}/chunk      上传分片（multipart 二进制 或 base64 字段）
GET  {prefix}/{group}/progress   查询进度
POST {prefix}/{group}/abort      中止上传
POST {prefix}/{group}/sync       同步上传（单请求整文件）
GET  {prefix}/aether-upload.js   前端上传组件
GET  {prefix}/demo               内置演示页（进度条/续传/base64 全功能）
```

### 3. 配置（application.yml）

```yaml
jaravel:
  aether-upload:
    enabled: true
    route-prefix: aetherupload
    default-group: file
    middleware: []                  # 全局中间件别名
    groups:
      file:                         # 默认组：内存记录头
        chunk-size: 1048576         # 分片 1MB（默认）
        temp-dir: temp              # 临时目录（默认运行目录 temp）
        save-dir: uploads
      video:                        # 视频组：redis 记录头 + 类型限制
        chunk-size: 2097152
        max-size: 2147483648        # 2GB（0 = 不限制）
        allowed-extensions: [mp4, mkv, avi]
        allowed-mime-types: ["video/*"]
        header-store: redis         # cache 模块中名为 redis 的 store
        header-ttl-seconds: 172800
        middleware: [auth]
      secret:                       # base64 组：规避安全软件拦截二进制
        base64: true
        header-store: cache         # cache 模块默认 store
      cloud:                        # 落盘到 storage 磁盘（可为 S3/OSS 等任意驱动）
        disk: media                 # storage 模块中注册的磁盘名
        save-dir: uploads           # 此时为磁盘内的相对子目录
```

`header-store` 取值：

- `memory`（默认）：进程内存记录文件 id / 分片位图
- `cache`：cache 模块默认 store（统一管理）
- 其他值（如 `redis`）：cache 模块中对应名称的 store，引入 `redis-cache` 并配置即可

### 落盘位置（storage 模块集成）

`disk` 未配置（默认）时，完成的文件直接写本地 `save-dir`，不依赖 storage 模块。

配置 `disk` 后，落盘统一走 [storage](../storage/README.md) 模块的 `Filesystem` 抽象，
`save-dir` 变为磁盘内的相对子目录，从而可落到任意驱动：

```yaml
jaravel:
  storage:
    disks:
      media:
        driver: local
        root: /data/media
        url: /media
  aether-upload:
    groups:
      file:
        disk: media
        save-dir: uploads
```

转存策略自动选择，两者都支持任意大小文件：

| 磁盘类型 | 策略 | `savedPath` 形态 |
| --- | --- | --- |
| 本地（`supportsLocalPath()`） | `Files.move`，同分区为原子 rename，零拷贝 | 绝对路径 |
| 远程（S3/OSS 等） | 流式 `putStream`，内存占用恒定 | `disk://{磁盘名}/{相对路径}` |

配合 storage 的 `url()` 可直接生成访问地址：

```java
String relative = savedPath.substring(("disk://" + disk + "/").length());
String url = Storage.disk("media").url(relative);
```

### 4. 前端

```html
<script src="/aetherupload/aether-upload.js"></script>
<script>
var uploader = new AetherUploader({
  endpoint: '/aetherupload',
  group: 'file',
  // chunkSize: 2 * 1024 * 1024,   // 前端自定义分片（组允许时生效）
  // base64: true,                 // 前端强制 base64 传输
  onProgress: function (percent) { bar.style.width = percent + '%'; },
  onSuccess:  function (result) { console.log('done', result.savedPath); },
  onError:    function (err) { console.error(err); }
});
uploader.upload(file);   // 分片上传（自动断点/断线续传）
uploader.pause();        // 暂停
uploader.resume();       // 从断点继续
uploader.abort();        // 中止并清理服务端任务
uploader.uploadSync(file); // 同步上传（小文件）
</script>
```

访问 `GET /aetherupload/demo` 可打开内置演示页体验全部功能。

### 5. 事件监听

```java
EventFacade.listen(UploadCompletedEvent.class, e -> {
    log.info("上传完成: {} -> {}", e.filename, e.savedPath);
});
EventFacade.listen(ChunkUploadedEvent.class, e -> {
    log.debug("分片进度: {}%", e.percent);
});
```

### 6. 后端门面（同步上传 / 进度）

```java
UploadResult r = AetherUpload.uploadSync("file", "a.txt", "text/plain", bytes);
UploadResult p = AetherUpload.progress("file", resourceId);
```

## 断点/断线续传原理

1. 前端 prepare 时携带 `identifier`（文件名+大小+修改时间，也可用文件 hash）；
2. 记录头（含分片位图）保存在组配置的存储中（内存/cache/redis），TTL 为 `header-ttl-seconds`；
3. 断线/刷新后重新 prepare：同 identifier 命中未完成任务时直接返回原 `resourceId` 与 `uploadedChunks`，前端跳过已传分片；
4. 全部分片到齐后临时文件转存为 `save-dir/yyyyMM/{resourceId}_{filename}`（配置 `disk` 时落到对应磁盘）并分发完成事件。

> 多实例部署：请配置 redis 记录头，并保证同一上传任务的请求路由到同一节点（或使用共享磁盘）。

## 传输模式

- **二进制（默认）**：`multipart/form-data`，分片放 `file` 字段；
- **base64**：分片编码为 base64 放普通表单字段 `data`（支持 dataURL 前缀），用于规避中间安全软件对二进制流的拦截。后端两种模式始终同时接受，前端按组配置或本地选项选择。
