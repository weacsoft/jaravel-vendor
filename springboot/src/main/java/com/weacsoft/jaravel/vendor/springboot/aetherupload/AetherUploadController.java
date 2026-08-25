package com.weacsoft.jaravel.vendor.springboot.aetherupload;

import com.weacsoft.jaravel.vendor.aetherupload.AetherUploadManager;
import com.weacsoft.jaravel.vendor.aetherupload.UploadException;
import com.weacsoft.jaravel.vendor.aetherupload.UploadResult;
import com.weacsoft.jaravel.vendor.http.controller.Controllers;
import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;
import com.weacsoft.jaravel.vendor.http.controller.response.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AetherUpload 上传端点控制器。
 * <p>
 * 路由结构（prefix 可配置 / 可自定义注册）：
 * <pre>
 * GET  {prefix}/{group}/config    组配置（前端据此做类型/大小/分片/base64 限制）
 * POST {prefix}/{group}/prepare   创建/恢复上传任务（断点续传返回已传分片）
 * POST {prefix}/{group}/chunk     上传分片（multipart 二进制 或 base64 字段，二者均接受）
 * GET  {prefix}/{group}/progress  查询进度
 * POST {prefix}/{group}/abort     中止上传
 * POST {prefix}/{group}/sync      同步上传（单请求整文件）
 * GET  {prefix}/aether-upload.js  前端上传组件 JS
 * GET  {prefix}/demo              内置演示页
 * </pre>
 * 分片传输模式：
 * <ul>
 *   <li><b>二进制</b>：multipart/form-data，file 字段名 {@code file}</li>
 *   <li><b>base64</b>：表单字段 {@code data}（支持 dataURL 前缀），用于规避安全软件拦截二进制流</li>
 * </ul>
 */
public class AetherUploadController implements Controllers {

    private static final Logger logger = LoggerFactory.getLogger(AetherUploadController.class);

    private final AetherUploadManager manager;

    public AetherUploadController(AetherUploadManager manager) {
        this.manager = manager;
    }

    // ========== 端点 ==========

    /**
     * 组配置端点：前端初始化时拉取，用于前端类型/大小/分片/base64 限制。
     */
    public Response config(Request request) {
        return handle(request, group -> ResponseBuilder.json(ok(manager.groupClientConfig(group))));
    }

    /**
     * 创建（或恢复）上传任务。
     * <p>
     * 参数：filename、size、mimeType?、identifier?（断线续传标识）、chunkSize?（组允许时生效）
     */
    public Response prepare(Request request) {
        return handle(request, group -> {
            String filename = request.get("filename");
            String sizeStr = request.get("size");
            if (filename == null || sizeStr == null) {
                throw UploadException.invalid("缺少参数 filename / size");
            }
            long size = parseLong(sizeStr, "size");
            String mimeType = request.get("mimeType");
            String identifier = request.get("identifier");
            String chunkSizeStr = request.get("chunkSize");
            Long chunkSize = chunkSizeStr == null ? null : parseLong(chunkSizeStr, "chunkSize");
            UploadResult result = manager.prepare(group, filename, size, mimeType, identifier, chunkSize);
            return ResponseBuilder.json(ok(result.toMap()));
        });
    }

    /**
     * 上传分片：multipart 二进制（file 字段）或 base64（data 字段）。
     */
    public Response chunk(Request request) {
        return handle(request, group -> {
            String resourceId = request.get("resourceId");
            String indexStr = request.get("index");
            if (resourceId == null || indexStr == null) {
                throw UploadException.invalid("缺少参数 resourceId / index");
            }
            int index = (int) parseLong(indexStr, "index");
            byte[] data = readChunkData(request);
            UploadResult result = manager.writeChunk(group, resourceId, index, data);
            return ResponseBuilder.json(ok(result.toMap()));
        });
    }

    /**
     * 查询上传进度（后端进度返回，含已传分片列表供断点续传）。
     */
    public Response progress(Request request) {
        return handle(request, group -> {
            String resourceId = request.get("resourceId");
            if (resourceId == null) {
                throw UploadException.invalid("缺少参数 resourceId");
            }
            UploadResult result = manager.progress(group, resourceId);
            return ResponseBuilder.json(ok(result.toMap()));
        });
    }

    /**
     * 中止上传，清理临时文件与记录头。
     */
    public Response abort(Request request) {
        return handle(request, group -> {
            String resourceId = request.get("resourceId");
            if (resourceId == null) {
                throw UploadException.invalid("缺少参数 resourceId");
            }
            manager.abort(group, resourceId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("aborted", true);
            return ResponseBuilder.json(ok(body));
        });
    }

    /**
     * 同步上传：单请求整文件（multipart file 字段或 base64 data + filename 字段）。
     */
    public Response sync(Request request) {
        return handle(request, group -> {
            String filename;
            String mimeType;
            byte[] data;
            if (request.hasFile("file")) {
                MultipartFile file = request.file("file");
                filename = file.getOriginalFilename();
                mimeType = file.getContentType();
                try {
                    data = file.getBytes();
                } catch (IOException e) {
                    throw UploadException.io("读取上传文件失败", e);
                }
            } else {
                String base64 = request.get("data");
                if (base64 == null) {
                    throw UploadException.invalid("缺少上传数据（file 或 data）");
                }
                filename = request.get("filename");
                if (filename == null) {
                    throw UploadException.invalid("base64 同步上传需提供 filename");
                }
                mimeType = request.get("mimeType");
                data = decodeBase64(base64);
            }
            UploadResult result = manager.uploadSync(group, filename, mimeType, data);
            return ResponseBuilder.json(ok(result.toMap()));
        });
    }

    /**
     * 前端上传组件 JS（classpath 内置资源）。
     */
    public Response script(Request request) {
        byte[] js = readResource("/aetherupload/aether-upload.js");
        return ResponseBuilder.staticFile(js, "application/javascript; charset=utf-8", 3600);
    }

    /**
     * 内置演示页（含百分比进度条 / 断点续传 / base64 演示）。
     */
    public Response demo(Request request) {
        byte[] html = readResource("/aetherupload/demo.html");
        return ResponseBuilder.html(new String(html, StandardCharsets.UTF_8));
    }

    // ========== 内部工具 ==========

    @FunctionalInterface
    private interface GroupAction {
        Response run(String group);
    }

    /**
     * 统一解析组名并转换异常为 JSON 错误响应。
     */
    private Response handle(Request request, GroupAction action) {
        try {
            return action.run(resolveGroup(request));
        } catch (UploadException e) {
            return errorResponse(422, e.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("[aether-upload] 端点处理异常", e);
            return errorResponse(500, "internal_error", "上传服务内部错误");
        }
    }

    /**
     * 解析组名：优先请求参数 group，其次从 URI 倒数第二段推断
     * （路由形如 {prefix}/{group}/{action}），最终回退默认组。
     */
    private String resolveGroup(Request request) {
        String group = request.get("group");
        if (group != null && !group.isEmpty()) {
            return group;
        }
        String uri = request.uri();
        if (uri != null && !uri.isEmpty()) {
            String[] segments = uri.split("/");
            if (segments.length >= 2) {
                String candidate = segments[segments.length - 2];
                if (manager.groupNames().contains(candidate)) {
                    return candidate;
                }
            }
        }
        return null; // manager 会回退到默认组
    }

    /**
     * 读取分片数据：multipart 二进制优先，其次 base64 字段。
     */
    private byte[] readChunkData(Request request) {
        if (request.hasFile("file")) {
            try {
                return request.file("file").getBytes();
            } catch (IOException e) {
                throw UploadException.io("读取分片数据失败", e);
            }
        }
        String base64 = request.get("data");
        if (base64 != null && !base64.isEmpty()) {
            return decodeBase64(base64);
        }
        throw UploadException.invalid("缺少分片数据（multipart file 或 base64 data）");
    }

    /**
     * 解码 base64（容忍 dataURL 前缀与 URL-safe 编码）。
     */
    private static byte[] decodeBase64(String base64) {
        String value = base64;
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            value = value.substring(comma + 1);
        }
        value = value.replace('\n', ' ').replace('\r', ' ').replace(" ", "");
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException e2) {
                throw UploadException.invalid("base64 数据解码失败");
            }
        }
    }

    private static long parseLong(String value, String field) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw UploadException.invalid("参数 " + field + " 必须为数字: " + value);
        }
    }

    private static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("data", data);
        return body;
    }

    private static Response errorResponse(int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 1);
        body.put("error", code);
        body.put("message", message);
        return ResponseBuilder.raw()
                .status(status)
                .contentType("application/json; charset=utf-8")
                .body(ResponseBuilder.toJson(body));
    }

    private static byte[] readResource(String path) {
        try (InputStream in = AetherUploadController.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("内置资源缺失: " + path);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("读取内置资源失败: " + path, e);
        }
    }
}
