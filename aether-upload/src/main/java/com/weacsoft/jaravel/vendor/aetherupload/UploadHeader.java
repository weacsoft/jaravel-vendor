package com.weacsoft.jaravel.vendor.aetherupload;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 上传记录头（resource header），对齐 AetherUpload 的资源头文件设计。
 * <p>
 * 记录一次分片上传任务的全部元信息：文件名、大小、分片大小、分片位图、状态等。
 * 通过 JSON 序列化存入 {@link com.weacsoft.jaravel.vendor.aetherupload.store.UploadHeaderStore}
 * （内存或 Cache/Redis），因此所有字段均为可序列化的简单类型。
 */
public class UploadHeader {

    /** 上传中 */
    public static final String STATUS_UPLOADING = "uploading";
    /** 已完成 */
    public static final String STATUS_COMPLETED = "completed";
    /** 已中止 */
    public static final String STATUS_ABORTED = "aborted";

    /** 资源 id（服务端生成的唯一标识） */
    private String resourceId;

    /** 所属上传组 */
    private String group;

    /** 原始文件名（已过滤路径分隔符） */
    private String filename;

    /** 文件总大小（字节） */
    private long size;

    /** 分片大小（字节） */
    private long chunkSize;

    /** 总分片数 */
    private int totalChunks;

    /** 已上传分片数 */
    private int uploadedCount;

    /** 已上传分片位图（Base64 编码的 byte[]，每 bit 对应一个分片） */
    private String bitmap;

    /** 状态：uploading / completed / aborted */
    private String status = STATUS_UPLOADING;

    /** 临时文件绝对路径 */
    private String tempPath;

    /** 完成后保存的绝对路径 */
    private String savedPath;

    /** 前端提供的文件唯一标识（断点/断线续传定位用，如文件 hash） */
    private String identifier;

    /** MIME 类型 */
    private String mimeType;

    /** 创建时间戳（毫秒） */
    private long createdAt;

    /** 最近更新时间戳（毫秒） */
    private long updatedAt;

    // ========== 位图操作 ==========

    /**
     * 判断指定分片是否已上传。
     */
    public boolean hasChunk(int index) {
        byte[] bits = bitmapBytes();
        int byteIndex = index / 8;
        if (byteIndex >= bits.length) {
            return false;
        }
        return (bits[byteIndex] & (1 << (index % 8))) != 0;
    }

    /**
     * 标记指定分片已上传。
     *
     * @return 若此前未标记返回 true（首次标记）
     */
    public boolean markChunk(int index) {
        byte[] bits = bitmapBytes();
        int byteIndex = index / 8;
        if (byteIndex >= bits.length) {
            byte[] grown = new byte[(totalChunks + 7) / 8];
            System.arraycopy(bits, 0, grown, 0, bits.length);
            bits = grown;
        }
        boolean first = (bits[byteIndex] & (1 << (index % 8))) == 0;
        bits[byteIndex] |= (byte) (1 << (index % 8));
        this.bitmap = Base64.getEncoder().encodeToString(bits);
        if (first) {
            this.uploadedCount++;
        }
        return first;
    }

    /**
     * 返回已上传分片索引列表（断点续传时前端据此跳过已传分片）。
     */
    public List<Integer> uploadedChunkList() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (hasChunk(i)) {
                list.add(i);
            }
        }
        return list;
    }

    /**
     * 上传进度百分比（0-100，保留两位小数）。
     */
    public double percent() {
        if (totalChunks <= 0) {
            return 0;
        }
        if (STATUS_COMPLETED.equals(status)) {
            return 100;
        }
        return Math.round(uploadedCount * 10000.0 / totalChunks) / 100.0;
    }

    /**
     * 是否已全部上传。
     */
    public boolean allUploaded() {
        return totalChunks > 0 && uploadedCount >= totalChunks;
    }

    private byte[] bitmapBytes() {
        if (bitmap == null || bitmap.isEmpty()) {
            return new byte[(Math.max(totalChunks, 1) + 7) / 8];
        }
        return Base64.getDecoder().decode(bitmap);
    }

    // ========== getter / setter ==========

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(long chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getUploadedCount() {
        return uploadedCount;
    }

    public void setUploadedCount(int uploadedCount) {
        this.uploadedCount = uploadedCount;
    }

    public String getBitmap() {
        return bitmap;
    }

    public void setBitmap(String bitmap) {
        this.bitmap = bitmap;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTempPath() {
        return tempPath;
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
    }

    public String getSavedPath() {
        return savedPath;
    }

    public void setSavedPath(String savedPath) {
        this.savedPath = savedPath;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
