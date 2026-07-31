package com.weacsoft.jaravel.vendor.aetherupload.facade;

import com.weacsoft.jaravel.vendor.aetherupload.AetherUploadManager;
import com.weacsoft.jaravel.vendor.aetherupload.UploadResult;
import com.weacsoft.jaravel.vendor.core.Facade;

/**
 * AetherUpload 门面，对齐 Laravel {@code AetherUpload::} 静态调用。
 * <pre>
 * // 同步上传
 * UploadResult r = AetherUpload.uploadSync("file", "a.txt", "text/plain", bytes);
 * // 查询进度
 * UploadResult p = AetherUpload.progress("file", resourceId);
 * </pre>
 */
public final class AetherUpload {

    private AetherUpload() {
    }

    private static AetherUploadManager inst() {
        return Facade.resolve(AetherUploadManager.class);
    }

    /** 创建（或恢复）分片上传任务 */
    public static UploadResult prepare(String group, String filename, long size,
                                       String mimeType, String identifier, Long clientChunkSize) {
        return inst().prepare(group, filename, size, mimeType, identifier, clientChunkSize);
    }

    /** 写入一个分片 */
    public static UploadResult writeChunk(String group, String resourceId, int chunkIndex, byte[] data) {
        return inst().writeChunk(group, resourceId, chunkIndex, data);
    }

    /** 查询进度 */
    public static UploadResult progress(String group, String resourceId) {
        return inst().progress(group, resourceId);
    }

    /** 中止上传 */
    public static void abort(String group, String resourceId) {
        inst().abort(group, resourceId);
    }

    /** 同步上传（单请求整文件） */
    public static UploadResult uploadSync(String group, String filename, String mimeType, byte[] data) {
        return inst().uploadSync(group, filename, mimeType, data);
    }
}
