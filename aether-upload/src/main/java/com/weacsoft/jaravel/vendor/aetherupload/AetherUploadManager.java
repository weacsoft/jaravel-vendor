package com.weacsoft.jaravel.vendor.aetherupload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weacsoft.jaravel.vendor.aetherupload.autoconfigure.AetherUploadProperties;
import com.weacsoft.jaravel.vendor.aetherupload.event.ChunkUploadedEvent;
import com.weacsoft.jaravel.vendor.aetherupload.event.UploadAbortedEvent;
import com.weacsoft.jaravel.vendor.aetherupload.event.UploadCompletedEvent;
import com.weacsoft.jaravel.vendor.aetherupload.event.UploadFailedEvent;
import com.weacsoft.jaravel.vendor.aetherupload.event.UploadPreparedEvent;
import com.weacsoft.jaravel.vendor.aetherupload.store.CacheUploadHeaderStore;
import com.weacsoft.jaravel.vendor.aetherupload.store.MemoryUploadHeaderStore;
import com.weacsoft.jaravel.vendor.aetherupload.store.UploadHeaderStore;
import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.event.Event;
import com.weacsoft.jaravel.vendor.event.facade.EventFacade;
import com.weacsoft.jaravel.vendor.storage.StorageManager;
import com.weacsoft.jaravel.vendor.storage.contract.Filesystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AetherUpload 上传核心管理器，对齐 Laravel peinhu/AetherUpload 的资源处理层。
 * <p>
 * 能力：
 * <ul>
 *   <li><b>任意大小上传</b>：分片写入 {@link RandomAccessFile}，临时文件预分配（稀疏文件），不受内存限制</li>
 *   <li><b>分片大小可配</b>：组配置 chunk-size（默认 1MB），亦可允许前端在 prepare 时指定</li>
 *   <li><b>断点/断线续传</b>：记录头存储分片位图；前端携带 identifier 重新 prepare 即可拿到已传分片列表继续上传</li>
 *   <li><b>多组配置</b>：不同组独立的记录头存储（内存 / cache / redis）、目录、限制</li>
 *   <li><b>事件</b>：prepare / chunk / completed / failed / aborted 全生命周期事件</li>
 *   <li><b>同步上传</b>：小文件单请求直接落盘</li>
 * </ul>
 * 线程安全：同一 resourceId 的写入通过本地锁串行化。多实例部署请配置 redis 记录头，
 * 并保证同一 resourceId 的请求路由到同一节点（或共享磁盘）。
 */
public class AetherUploadManager {

    private static final Logger logger = LoggerFactory.getLogger(AetherUploadManager.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter SUB_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    /** 默认分片大小（1MB）：组未配置或配置非法时兜底 */
    private static final long DEFAULT_CHUNK_SIZE = 1024 * 1024L;

    /** 默认分片数上限：位图约 12.5KB，同时约束 uploadedChunkList 的响应体规模 */
    private static final long DEFAULT_MAX_CHUNKS = 100_000L;

    /** 单分片大小兜底上限（10MB）：约束自适应放大与客户端声明值 */
    private static final long MAX_CHUNK_SIZE_LIMIT = 10 * 1024 * 1024L;

    /** 分片临时目录机会式清理的最小间隔（10 分钟），避免每次 prepare 都扫目录 */
    private static final long TEMP_SWEEP_INTERVAL_MILLIS = 10 * 60 * 1000L;

    /** 「匿名且未限大小」告警只打印一次 */
    private static final java.util.concurrent.atomic.AtomicBoolean ANONYMOUS_UNLIMITED_WARNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 上次清理分片临时目录的时间戳（毫秒） */
    private final java.util.concurrent.atomic.AtomicLong lastTempSweepAt =
            new java.util.concurrent.atomic.AtomicLong(0L);

    /**
     * 上传归属主体解析器（登录用户 id；返回 {@code null} 表示匿名）。
     * <p>
     * 框架主体零 Spring、不直接依赖 auth：由宿主（如 springboot 的装配）注入
     * 「从当前认证上下文取用户 id」的实现；未注入时一律视为匿名。
     */
    private volatile java.util.function.Supplier<String> ownerResolver = () -> null;

    /**
     * 设置上传归属主体解析器。
     *
     * @param resolver 返回当前用户 id 或 {@code null}（匿名）；传 {@code null} 表示恢复为匿名
     */
    public void setOwnerResolver(java.util.function.Supplier<String> resolver) {
        this.ownerResolver = resolver != null ? resolver : () -> null;
    }

    /** 当前请求的上传归属主体（匿名返回 null） */
    private String currentOwner() {
        try {
            java.util.function.Supplier<String> resolver = this.ownerResolver;
            String owner = resolver == null ? null : resolver.get();
            return owner == null || owner.isEmpty() ? null : owner;
        } catch (Exception e) {
            // 解析失败一律按匿名处理：宁可禁用续传，也不能把归属判错导致越权
            logger.debug("[aether-upload] 解析上传归属失败，按匿名处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 断言当前主体有权访问该上传任务。
     * <p>
     * 归属为 {@code null}（匿名任务）时不校验 —— 匿名任务本就无归属可言，其越权面
     * 由「匿名默认禁用 identifier 续传」来收敛。
     *
     * @param header 任务记录头
     */
    private void assertOwnership(UploadHeader header) {
        String owner = header.getOwnerId();
        if (owner == null) {
            return;
        }
        if (!owner.equals(currentOwner())) {
            logger.warn("[aether-upload] 归属校验失败: resourceId={}, owner={}, requester={}",
                    header.getResourceId(), owner, currentOwner());
            throw UploadException.invalid("无权访问该上传任务");
        }
    }

    /** 单组运行时：配置 + 记录头存储 */
    public static final class GroupRuntime {
        public final String name;
        public final AetherUploadProperties.GroupConfig config;
        final UploadHeaderStore store;

        GroupRuntime(String name, AetherUploadProperties.GroupConfig config, UploadHeaderStore store) {
            this.name = name;
            this.config = config;
            this.store = store;
        }
    }

    private final AetherUploadProperties properties;
    private final CacheManager cacheManager;
    /** 存储管理器，用于组配置 disk 时将完成的文件落到指定磁盘；未引入时为 null */
    private final StorageManager storageManager;
    private final Map<String, GroupRuntime> groups = new ConcurrentHashMap<>();
    /** resourceId -> 本地写锁 */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public AetherUploadManager(AetherUploadProperties properties, CacheManager cacheManager) {
        this(properties, cacheManager, null);
    }

    /**
     * @param properties     模块配置
     * @param cacheManager   缓存管理器（记录头存储），可为 null
     * @param storageManager 存储管理器，可为 null；为 null 时组配置的 disk 失效并降级为本地目录落盘
     */
    public AetherUploadManager(AetherUploadProperties properties, CacheManager cacheManager,
                               StorageManager storageManager) {
        this.properties = properties;
        this.cacheManager = cacheManager;
        this.storageManager = storageManager;
        initGroups();
    }

    private void initGroups() {
        Map<String, AetherUploadProperties.GroupConfig> configured = properties.getGroups();
        if (configured == null || configured.isEmpty()) {
            // 未配置任何组：自动创建默认组（全部默认值）
            String name = properties.getDefaultGroup();
            groups.put(name, buildGroup(name, new AetherUploadProperties.GroupConfig()));
            logger.info("[aether-upload] 未配置 groups，自动创建默认组: {}", name);
            return;
        }
        for (Map.Entry<String, AetherUploadProperties.GroupConfig> e : configured.entrySet()) {
            groups.put(e.getKey(), buildGroup(e.getKey(), e.getValue()));
        }
        // 确保默认组存在
        groups.computeIfAbsent(properties.getDefaultGroup(),
                name -> buildGroup(name, new AetherUploadProperties.GroupConfig()));
    }

    private GroupRuntime buildGroup(String name, AetherUploadProperties.GroupConfig cfg) {
        UploadHeaderStore store;
        String storeName = cfg.getHeaderStore() == null ? "memory" : cfg.getHeaderStore().trim();
        if (storeName.isEmpty() || "memory".equalsIgnoreCase(storeName)) {
            store = new MemoryUploadHeaderStore();
        } else if (cacheManager == null) {
            logger.warn("[aether-upload] 组 {} 配置 header-store={} 但未引入 cache 管理器，降级为内存记录头",
                    name, storeName);
            store = new MemoryUploadHeaderStore();
        } else if ("cache".equalsIgnoreCase(storeName)) {
            store = new CacheUploadHeaderStore(cacheManager.store());
        } else {
            store = new CacheUploadHeaderStore(cacheManager.store(storeName));
        }
        logger.info("[aether-upload] 注册上传组: name={}, chunkSize={}, headerStore={}, tempDir={}, saveDir={}",
                name, cfg.getChunkSize(), storeName, cfg.getTempDir(), cfg.getSaveDir());
        return new GroupRuntime(name, cfg, store);
    }

    // ========== 组信息 ==========

    /**
     * 解析上传组，不存在时抛出 {@link UploadException}。
     */
    public GroupRuntime group(String name) {
        String key = (name == null || name.isEmpty()) ? properties.getDefaultGroup() : name;
        GroupRuntime g = groups.get(key);
        if (g == null) {
            throw UploadException.groupNotFound(key);
        }
        return g;
    }

    /**
     * 返回所有组名。
     */
    public java.util.Set<String> groupNames() {
        return groups.keySet();
    }

    /**
     * 返回组的前端可见配置（供 /config 端点，前端据此做类型/大小/分片/base64 限制）。
     */
    public Map<String, Object> groupClientConfig(String name) {
        GroupRuntime g = group(name);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", g.name);
        map.put("chunkSize", g.config.getChunkSize());
        map.put("maxSize", g.config.getMaxSize());
        map.put("allowedExtensions", g.config.getAllowedExtensions());
        map.put("allowedMimeTypes", g.config.getAllowedMimeTypes());
        map.put("base64", g.config.isBase64());
        map.put("allowClientChunkSize", g.config.isAllowClientChunkSize());
        return map;
    }

    // ========== 分片上传 ==========

    /**
     * 创建（或恢复）上传任务，对齐 AetherUpload 的 preprocess。
     *
     * @param groupName       组名（null 用默认组）
     * @param filename        文件名
     * @param size            文件总大小（字节）
     * @param mimeType        MIME 类型（可空）
     * @param identifier      前端文件唯一标识（可空；提供后支持断线续传）
     * @param clientChunkSize 前端期望分片大小（可空；组允许时生效）
     * @return 上传任务信息（含已上传分片列表，供断点续传）
     */
    public UploadResult prepare(String groupName, String filename, long size,
                                String mimeType, String identifier, Long clientChunkSize) {
        GroupRuntime g = group(groupName);
        String safeName = sanitizeFilename(filename);
        try {
            validate(g, safeName, size, mimeType);
        } catch (UploadException e) {
            dispatch(new UploadFailedEvent(g.name, null, safeName, size, e.getMessage()));
            throw e;
        }
        if (size <= 0) {
            throw UploadException.invalid("分片上传要求文件大小必须大于 0");
        }
        // 安全可见性：既未限大小又没挂中间件 = 端点可被匿名调用并预分配磁盘
        warnIfAnonymousAndUnlimited(g);
        // 清理超过记录头 TTL 的分片临时文件（否则匿名反复 prepare 会让 .part 无界堆积）
        sweepStaleTempFiles(g, Math.max(60_000L, g.config.getHeaderTtlSeconds() * 1000L));

        // 断线续传：identifier 已有未完成任务时直接恢复。
        // 归属约束：① 键按主体作用域隔离（不同用户即使 identifier 相同也不会互相命中）；
        //           ② 匿名默认不启用续传（identifier 由前端按文件名/大小/mtime 可预测拼出，
        //              允许匿名续传等于「知道三要素即可复用/劫持他人上传任务」）。
        String owner = currentOwner();
        boolean resumeAllowed = identifier != null && !identifier.isEmpty()
                && (owner != null || g.config.isAnonymousResumeEnabled());
        if (resumeAllowed) {
            String existingId = g.store.get(idKey(g.name, owner, identifier));
            if (existingId != null) {
                UploadHeader header = loadHeader(g, existingId);
                if (header != null && UploadHeader.STATUS_UPLOADING.equals(header.getStatus())
                        && header.getSize() == size
                        && chunkSizeMatches(header, clientChunkSize)
                        && Files.exists(Paths.get(header.getTempPath()))) {
                    dispatch(new UploadPreparedEvent(g.name, header.getResourceId(), header.getFilename(),
                            size, header.getTotalChunks(), header.getChunkSize(), true));
                    return new UploadResult(header, header.uploadedChunkList(), true);
                }
            }
        }

        long chunkSize = resolveChunkSize(g, size, clientChunkSize);
        long maxChunks = g.config.getMaxChunks() > 0 ? g.config.getMaxChunks() : DEFAULT_MAX_CHUNKS;
        long hardMaxChunk = g.config.getMaxChunkSize() > 0 ? g.config.getMaxChunkSize() : MAX_CHUNK_SIZE_LIMIT;
        // 分片数上限反推最小分片：大文件自动放大分片，而不是拒绝（避免「静默限制文件大小」）。
        // 但放大必须受「单分片上限」约束，否则超大 size 会把分片放大到荒谬值，
        // 再被用于 raf.setLength(size) 预分配 —— 那正是磁盘 DoS 的入口。
        long minChunkForCap = (size + maxChunks - 1) / maxChunks;
        if (minChunkForCap > hardMaxChunk) {
            throw UploadException.invalid("文件过大：要满足分片数上限 " + maxChunks
                    + " 需要 " + minChunkForCap + " 字节的分片，已超过单分片上限 " + hardMaxChunk
                    + "；请调大 max-chunks/max-chunk-size，或为端点配置 max-size 限制");
        }
        if (chunkSize < minChunkForCap) {
            chunkSize = minChunkForCap;
        }
        // 关键：先用 long 计算再收窄。原先直接 (int)((size + chunkSize - 1) / chunkSize)，
        // size=8e9 & chunkSize=1 时会得到 -589934592（负），分片状态机随之失效、上传永不完成。
        long totalChunksLong = (size + chunkSize - 1) / chunkSize;
        if (totalChunksLong <= 0 || totalChunksLong > maxChunks || totalChunksLong > Integer.MAX_VALUE) {
            throw UploadException.invalid("分片数超出上限（" + totalChunksLong + " > " + maxChunks
                    + "），请调大分片大小或调整 max-chunks 配置");
        }
        int totalChunks = (int) totalChunksLong;

        // 校验全部通过后再落盘：上限/溢出被拒时不会创建任何 .part（避免匿名预分配磁盘 DoS）
        String resourceId = UUID.randomUUID().toString().replace("-", "");
        Path tempPath = resolveDir(g.config.getTempDir()).resolve(resourceId + ".part");
        try {
            Files.createDirectories(tempPath.getParent());
            // 预分配（稀疏文件），支持乱序分片写入
            try (RandomAccessFile raf = new RandomAccessFile(tempPath.toFile(), "rw")) {
                raf.setLength(size);
            }
        } catch (IOException e) {
            throw UploadException.io("创建临时文件失败", e);
        }

        UploadHeader header = new UploadHeader();
        header.setResourceId(resourceId);
        header.setGroup(g.name);
        header.setFilename(safeName);
        header.setSize(size);
        header.setChunkSize(chunkSize);
        header.setTotalChunks(totalChunks);
        header.setStatus(UploadHeader.STATUS_UPLOADING);
        header.setTempPath(tempPath.toAbsolutePath().toString());
        header.setIdentifier(identifier);
        header.setOwnerId(owner);
        header.setMimeType(mimeType);
        header.setCreatedAt(System.currentTimeMillis());
        header.setUpdatedAt(header.getCreatedAt());
        saveHeader(g, header);

        if (identifier != null && !identifier.isEmpty() && resumeAllowed) {
            g.store.put(idKey(g.name, owner, identifier), resourceId, g.config.getHeaderTtlSeconds());
        }

        dispatch(new UploadPreparedEvent(g.name, resourceId, safeName, size, totalChunks, chunkSize, false));
        return new UploadResult(header, header.uploadedChunkList(), false);
    }

    /**
     * 续传时客户端声明的分片大小是否与既有任务一致。
     * <p>
     * 官方前端按「服务端回显的 chunkSize」切分，正常情况下必然一致；但第三方前端若按自己的
     * chunkSize 切分，续传后写分片会因「分片大小不符」被拒（契约脆弱点）。这里改为
     * 「声明值不一致就不复用旧任务」，让客户端重新 prepare 并拿到服务端认可的分片大小。
     *
     * @param header          既有任务记录头
     * @param clientChunkSize 客户端本次声明的分片大小（可空）
     * @return 一致（或客户端未声明）返回 true
     */
    private static boolean chunkSizeMatches(UploadHeader header, Long clientChunkSize) {
        if (clientChunkSize == null || clientChunkSize <= 0) {
            return true;
        }
        return clientChunkSize == header.getChunkSize();
    }

    /**
     * 解析本次生效的分片大小：组配置 → 客户端值（受区间钳制）→ 默认值。
     * <p>
     * 客户端值被<b>钳制</b>而不是拒绝，是为了对既有前端保持兼容：前端始终以服务端
     * 回显的 {@code chunkSize} 切分（见 {@code aether-upload.js} 的 {@code self.chunkSize = data.chunkSize}），
     * 因此钳制 + 回显对默认前端是透明的；只有钳制后仍超限才会报错。
     *
     * @param g              组运行时
     * @param size           文件大小
     * @param clientChunkSize 客户端声明值（可空）
     * @return 生效的分片大小（字节）
     */
    private long resolveChunkSize(GroupRuntime g, long size, Long clientChunkSize) {
        long chunkSize = g.config.getChunkSize();
        if (clientChunkSize != null && clientChunkSize > 0 && g.config.isAllowClientChunkSize()) {
            long min = g.config.getMinChunkSize() > 0 ? g.config.getMinChunkSize() : 1024L;
            long max = g.config.getMaxChunkSize() > 0 ? g.config.getMaxChunkSize() : MAX_CHUNK_SIZE_LIMIT;
            if (max < min) {
                max = min;
            }
            long clamped = Math.min(Math.max(clientChunkSize, min), max);
            if (clamped != clientChunkSize) {
                logger.warn("[aether-upload] 客户端分片大小 {} 超出允许区间 [{}, {}]，已钳制为 {}（以服务端回显为准）",
                        clientChunkSize, min, max, clamped);
            }
            chunkSize = clamped;
        }
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        return chunkSize;
    }

    /**
     * 机会式清理过期分片临时文件（限频）。
     * <p>
     * 判定条件：{@code .part} 且最后修改时间早于「now - 记录头 TTL」。上传中的文件会因写入而
     * 不断刷新 mtime，因此不会被误删；超过 TTL 的续传记录本身也已失效。
     *
     * @param g         组运行时
     * @param ttlMillis 记录头 TTL（毫秒）
     */
    private void sweepStaleTempFiles(GroupRuntime g, long ttlMillis) {
        long now = System.currentTimeMillis();
        long last = lastTempSweepAt.get();
        if (now - last < TEMP_SWEEP_INTERVAL_MILLIS || !lastTempSweepAt.compareAndSet(last, now)) {
            return;
        }
        Path dir = resolveDir(g.config.getTempDir());
        if (!Files.isDirectory(dir)) {
            return;
        }
        int[] removed = {0};
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".part"))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() < now - ttlMillis;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            if (Files.deleteIfExists(p)) {
                                removed[0]++;
                            }
                        } catch (IOException ignored) {
                            // 单个文件删不掉不影响后续
                        }
                    });
        } catch (IOException e) {
            logger.debug("[aether-upload] 清理分片临时目录失败: {}", e.getMessage());
            return;
        }
        if (removed[0] > 0) {
            logger.info("[aether-upload] 已清理过期分片临时文件 {} 个: dir={}", removed[0], dir);
        }
    }

    /**
     * 匿名 + 未限制大小时打印一次性告警：这是「单请求预分配磁盘」的真实风险面。
     *
     * @param g 组运行时
     */
    private void warnIfAnonymousAndUnlimited(GroupRuntime g) {
        boolean noMiddleware = g.config.getMiddleware() == null || g.config.getMiddleware().isEmpty();
        if (g.config.getMaxSize() <= 0 && noMiddleware
                && ANONYMOUS_UNLIMITED_WARNED.compareAndSet(false, true)) {
            logger.warn("[aether-upload] 组 {} 既未配置 max-size 也未挂任何中间件："
                    + "prepare/chunk 端点可被匿名调用，且 size 会直接用于预分配临时文件。"
                    + "公网部署请配置 max-size，并为上传端点挂上 auth 中间件（另见 identifier 归属校验说明）。",
                    g.name);
        }
    }

    /**
     * 写入一个分片，对齐 AetherUpload 的 uploading（追加子资源）。
     * <p>
     * 全部分片写入完成后自动合并落盘并分发 {@link UploadCompletedEvent}。
     *
     * @param groupName  组名
     * @param resourceId 资源 id
     * @param chunkIndex 分片索引（从 0 开始）
     * @param data       分片二进制数据（base64 传输时控制器已解码）
     * @return 最新进度（completed=true 时含 savedPath）
     */
    public UploadResult writeChunk(String groupName, String resourceId, int chunkIndex, byte[] data) {
        GroupRuntime g = group(groupName);
        Object lock = locks.computeIfAbsent(resourceId, k -> new Object());
        synchronized (lock) {
            UploadHeader header = loadHeader(g, resourceId);
            if (header == null) {
                throw UploadException.headerNotFound(resourceId);
            }
            assertOwnership(header);
            if (UploadHeader.STATUS_COMPLETED.equals(header.getStatus())) {
                return new UploadResult(header, null, false);
            }
            if (!UploadHeader.STATUS_UPLOADING.equals(header.getStatus())) {
                throw UploadException.invalid("上传任务状态异常: " + header.getStatus());
            }
            if (chunkIndex < 0 || chunkIndex >= header.getTotalChunks()) {
                throw UploadException.invalid("分片索引越界: " + chunkIndex + " / " + header.getTotalChunks());
            }
            long offset = header.getChunkSize() * chunkIndex;
            long expected = Math.min(header.getChunkSize(), header.getSize() - offset);
            if (data == null || data.length != expected) {
                throw UploadException.invalid("分片大小不符: index=" + chunkIndex
                        + ", expected=" + expected + ", actual=" + (data == null ? 0 : data.length));
            }

            try (RandomAccessFile raf = new RandomAccessFile(header.getTempPath(), "rw")) {
                raf.seek(offset);
                raf.write(data);
            } catch (IOException e) {
                UploadException ue = UploadException.io("写入分片失败", e);
                dispatch(new UploadFailedEvent(g.name, resourceId, header.getFilename(),
                        header.getSize(), ue.getMessage()));
                throw ue;
            }

            header.markChunk(chunkIndex);
            header.setUpdatedAt(System.currentTimeMillis());

            if (header.allUploaded()) {
                finalizeUpload(g, header);
                locks.remove(resourceId);
            } else {
                saveHeader(g, header);
            }

            dispatch(new ChunkUploadedEvent(g.name, resourceId, header.getFilename(), header.getSize(),
                    chunkIndex, header.getUploadedCount(), header.getTotalChunks(), header.percent()));
            if (UploadHeader.STATUS_COMPLETED.equals(header.getStatus())) {
                dispatch(new UploadCompletedEvent(g.name, resourceId, header.getFilename(),
                        header.getSize(), header.getSavedPath()));
            }
            return new UploadResult(header, null, false);
        }
    }

    /**
     * 查询上传进度（后端进度返回；断点续传时返回已传分片列表）。
     */
    public UploadResult progress(String groupName, String resourceId) {
        GroupRuntime g = group(groupName);
        UploadHeader header = loadHeader(g, resourceId);
        if (header == null) {
            throw UploadException.headerNotFound(resourceId);
        }
        assertOwnership(header);
        return new UploadResult(header, header.uploadedChunkList(), false);
    }

    /**
     * 中止上传：删除临时文件与记录头，分发 {@link UploadAbortedEvent}。
     */
    public void abort(String groupName, String resourceId) {
        GroupRuntime g = group(groupName);
        UploadHeader header = loadHeader(g, resourceId);
        if (header == null) {
            return;
        }
        assertOwnership(header);
        Object lock = locks.computeIfAbsent(resourceId, k -> new Object());
        synchronized (lock) {
            try {
                Files.deleteIfExists(Paths.get(header.getTempPath()));
            } catch (IOException e) {
                logger.warn("[aether-upload] 删除临时文件失败: {}", header.getTempPath(), e);
            }
            g.store.remove(headerKey(g.name, resourceId));
            if (header.getIdentifier() != null && !header.getIdentifier().isEmpty()) {
                g.store.remove(idKey(g.name, header.getOwnerId(), header.getIdentifier()));
            }
        }
        locks.remove(resourceId);
        dispatch(new UploadAbortedEvent(g.name, resourceId, header.getFilename(), header.getSize()));
    }

    // ========== 同步上传 ==========

    /**
     * 同步上传：单请求整文件直接落盘（小文件场景），完成后分发 {@link UploadCompletedEvent}。
     *
     * @param groupName 组名
     * @param filename  文件名
     * @param mimeType  MIME 类型（可空）
     * @param data      文件完整数据
     * @return 上传结果（completed=true）
     */
    public UploadResult uploadSync(String groupName, String filename, String mimeType, byte[] data) {
        GroupRuntime g = group(groupName);
        String safeName = sanitizeFilename(filename);
        long size = data == null ? 0 : data.length;
        try {
            validate(g, safeName, size, mimeType);
        } catch (UploadException e) {
            dispatch(new UploadFailedEvent(g.name, null, safeName, size, e.getMessage()));
            throw e;
        }

        String resourceId = UUID.randomUUID().toString().replace("-", "");
        byte[] payload = data == null ? new byte[0] : data;
        String savedLocation;
        try {
            Filesystem disk = diskOf(g);
            if (disk == null) {
                Path saved = savedPath(g, resourceId, safeName);
                Files.createDirectories(saved.getParent());
                Files.write(saved, payload);
                savedLocation = saved.toAbsolutePath().toString();
            } else {
                String relative = diskRelativePath(g, resourceId, safeName);
                disk.put(relative, payload);
                savedLocation = disk.supportsLocalPath()
                        ? disk.path(relative)
                        : "disk://" + disk.name() + "/" + relative;
            }
        } catch (Exception e) {
            UploadException ue = UploadException.io("同步上传写入失败", e);
            dispatch(new UploadFailedEvent(g.name, resourceId, safeName, size, ue.getMessage()));
            throw ue;
        }

        UploadHeader header = new UploadHeader();
        header.setResourceId(resourceId);
        header.setGroup(g.name);
        header.setFilename(safeName);
        header.setSize(size);
        header.setChunkSize(size);
        header.setTotalChunks(1);
        header.setUploadedCount(1);
        header.setStatus(UploadHeader.STATUS_COMPLETED);
        header.setSavedPath(savedLocation);
        header.setMimeType(mimeType);
        header.setCreatedAt(System.currentTimeMillis());
        header.setUpdatedAt(header.getCreatedAt());
        saveHeader(g, header);

        dispatch(new UploadCompletedEvent(g.name, resourceId, safeName, size, header.getSavedPath()));
        return new UploadResult(header, null, false);
    }

    // ========== 校验 ==========

    /**
     * 后端类型 / 大小校验（前端限制可被绕过，后端始终强制校验）。
     */
    public void validate(GroupRuntime g, String filename, long size, String mimeType) {
        long maxSize = g.config.getMaxSize();
        if (maxSize > 0 && size > maxSize) {
            throw UploadException.sizeExceeded(size, maxSize);
        }
        java.util.List<String> exts = g.config.getAllowedExtensions();
        if (exts != null && !exts.isEmpty()) {
            String ext = extension(filename);
            boolean ok = exts.stream().anyMatch(e -> e != null && e.toLowerCase(Locale.ROOT)
                    .replace(".", "").equals(ext));
            if (!ok) {
                throw UploadException.typeNotAllowed("扩展名 ." + ext);
            }
        }
        java.util.List<String> mimes = g.config.getAllowedMimeTypes();
        if (mimes != null && !mimes.isEmpty() && mimeType != null && !mimeType.isEmpty()) {
            String mt = mimeType.toLowerCase(Locale.ROOT);
            boolean ok = mimes.stream().anyMatch(m -> {
                if (m == null) {
                    return false;
                }
                String pattern = m.toLowerCase(Locale.ROOT);
                if (pattern.endsWith("/*")) {
                    return mt.startsWith(pattern.substring(0, pattern.length() - 1));
                }
                return mt.equals(pattern);
            });
            if (!ok) {
                throw UploadException.typeNotAllowed("MIME " + mimeType);
            }
        }
    }

    // ========== 内部工具 ==========

    private void finalizeUpload(GroupRuntime g, UploadHeader header) {
        String savedLocation;
        try {
            Filesystem disk = diskOf(g);
            if (disk == null) {
                // 无 storage 磁盘：直接在本地目录内移动（同分区时为原子 rename，零拷贝）
                Path saved = savedPath(g, header.getResourceId(), header.getFilename());
                Files.createDirectories(saved.getParent());
                Files.move(Paths.get(header.getTempPath()), saved, StandardCopyOption.REPLACE_EXISTING);
                savedLocation = saved.toAbsolutePath().toString();
            } else {
                savedLocation = moveToDisk(g, disk, header);
            }
        } catch (UploadException e) {
            dispatch(new UploadFailedEvent(g.name, header.getResourceId(), header.getFilename(),
                    header.getSize(), e.getMessage()));
            throw e;
        } catch (Exception e) {
            UploadException ue = UploadException.io("合并保存文件失败", e);
            dispatch(new UploadFailedEvent(g.name, header.getResourceId(), header.getFilename(),
                    header.getSize(), ue.getMessage()));
            throw ue;
        }
        header.setStatus(UploadHeader.STATUS_COMPLETED);
        header.setSavedPath(savedLocation);
        header.setUpdatedAt(System.currentTimeMillis());
        saveHeader(g, header);
        if (header.getIdentifier() != null && !header.getIdentifier().isEmpty()) {
            g.store.remove(idKey(g.name, header.getOwnerId(), header.getIdentifier()));
        }
        logger.info("[aether-upload] 上传完成: group={}, resourceId={}, file={}, size={}, path={}",
                g.name, header.getResourceId(), header.getFilename(), header.getSize(), savedLocation);
    }

    private Path savedPath(GroupRuntime g, String resourceId, String filename) {
        return resolveDir(g.config.getSaveDir())
                .resolve(LocalDate.now().format(SUB_DIR))
                .resolve(resourceId + "_" + filename);
    }

    /**
     * 解析组配置的 storage 磁盘；未配置 disk 或未引入 storage 模块时返回 {@code null}
     * （此时落盘降级为直接写本地 {@code save-dir}）。
     *
     * @param g 组运行时
     * @return 磁盘实例或 null
     */
    private Filesystem diskOf(GroupRuntime g) {
        String diskName = g.config.getDisk();
        if (diskName == null || diskName.isBlank() || storageManager == null) {
            return null;
        }
        return storageManager.disk(diskName);
    }

    /**
     * 磁盘内的相对保存路径（{@code save-dir/yyyyMMdd/resourceId_filename}）。
     */
    private String diskRelativePath(GroupRuntime g, String resourceId, String filename) {
        String dir = g.config.getSaveDir();
        String prefix = (dir == null || dir.isBlank()) ? "" : dir.replace('\\', '/') + "/";
        return prefix + LocalDate.now().format(SUB_DIR) + "/" + resourceId + "_" + filename;
    }

    /**
     * 将合并完成的临时文件转存到 storage 磁盘。
     * <p>
     * 本地磁盘走 {@code Files.move}（同分区为原子 rename，零拷贝）；
     * 远程磁盘（S3/OSS 等）走流式 {@code putStream}，内存占用恒定，支持任意大小文件。
     *
     * @return 落盘后的定位信息（本地磁盘为绝对路径，远程磁盘为 {@code disk://name/path}）
     */
    private String moveToDisk(GroupRuntime g, Filesystem disk, UploadHeader header) throws IOException {
        String relative = diskRelativePath(g, header.getResourceId(), header.getFilename());
        Path temp = Paths.get(header.getTempPath());

        if (disk.supportsLocalPath()) {
            Path target = Paths.get(disk.path(relative));
            Files.createDirectories(target.getParent());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toString();
        }

        try (InputStream in = Files.newInputStream(temp)) {
            disk.putStream(relative, in);
        }
        Files.deleteIfExists(temp);
        return "disk://" + disk.name() + "/" + relative;
    }

    private Path resolveDir(String dir) {
        Path path = Paths.get(dir == null || dir.isEmpty() ? "temp" : dir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path;
    }

    private void saveHeader(GroupRuntime g, UploadHeader header) {
        try {
            g.store.put(headerKey(g.name, header.getResourceId()),
                    MAPPER.writeValueAsString(header), g.config.getHeaderTtlSeconds());
        } catch (IOException e) {
            throw UploadException.io("序列化上传记录头失败", e);
        }
    }

    private UploadHeader loadHeader(GroupRuntime g, String resourceId) {
        String json = g.store.get(headerKey(g.name, resourceId));
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, UploadHeader.class);
        } catch (IOException e) {
            logger.warn("[aether-upload] 解析上传记录头失败: {}", resourceId, e);
            return null;
        }
    }

    private static String headerKey(String group, String resourceId) {
        return "h:" + group + ":" + resourceId;
    }

    private static String idKey(String group, String owner, String identifier) {
        // 归属作用域：不同主体的同名 identifier 不得互相复用（防止可预测 identifier 劫持他人任务）
        return "i:" + group + ":" + (owner == null ? "-" : owner) + ":" + identifier;
    }

    /**
     * 过滤文件名中的路径分隔符与控制字符，防止目录穿越。
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed";
        }
        String name = filename.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1f\"*:<>?|]", "_").trim();
        return name.isEmpty() ? "unnamed" : name;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void dispatch(Event event) {
        try {
            EventFacade.dispatch(event);
        } catch (Throwable t) {
            // 事件分发器不可用（如未启动 Spring 容器的测试环境）时静默降级
            logger.debug("[aether-upload] 事件分发跳过: {}", event.getClass().getSimpleName());
        }
    }
}
