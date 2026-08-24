package com.weacsoft.jaravel.vendor.aetherupload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传操作结果（prepare / chunk / progress / sync 的统一返回体）。
 */
public class UploadResult {

    /** 资源 id */
    public final String resourceId;
    /** 组名 */
    public final String group;
    /** 文件名 */
    public final String filename;
    /** 文件总大小 */
    public final long size;
    /** 分片大小 */
    public final long chunkSize;
    /** 总分片数 */
    public final int totalChunks;
    /** 已上传分片数 */
    public final int uploadedCount;
    /** 进度百分比（0-100） */
    public final double percent;
    /** 状态 */
    public final String status;
    /** 是否完成 */
    public final boolean completed;
    /** 完成后的保存路径（未完成为 null） */
    public final String savedPath;
    /** 已上传分片索引（断点续传时返回，其余场景可为 null） */
    public final List<Integer> uploadedChunks;
    /** 是否为断点续传恢复 */
    public final boolean resumed;

    public UploadResult(UploadHeader header, List<Integer> uploadedChunks, boolean resumed) {
        this.resourceId = header.getResourceId();
        this.group = header.getGroup();
        this.filename = header.getFilename();
        this.size = header.getSize();
        this.chunkSize = header.getChunkSize();
        this.totalChunks = header.getTotalChunks();
        this.uploadedCount = header.getUploadedCount();
        this.percent = header.percent();
        this.status = header.getStatus();
        this.completed = UploadHeader.STATUS_COMPLETED.equals(header.getStatus());
        this.savedPath = header.getSavedPath();
        this.uploadedChunks = uploadedChunks;
        this.resumed = resumed;
    }

    /**
     * 转为 Map，供 JSON 响应输出。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resourceId", resourceId);
        map.put("group", group);
        map.put("filename", filename);
        map.put("size", size);
        map.put("chunkSize", chunkSize);
        map.put("totalChunks", totalChunks);
        map.put("uploadedCount", uploadedCount);
        map.put("percent", percent);
        map.put("status", status);
        map.put("completed", completed);
        map.put("resumed", resumed);
        if (savedPath != null) {
            map.put("savedPath", savedPath);
        }
        if (uploadedChunks != null) {
            map.put("uploadedChunks", uploadedChunks);
        }
        return map;
    }
}
