package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

/**
 * 远程方法执行响应。
 * <p>
 * 由服务端返回给客户端，包含执行结果或错误信息。
 */
public class ExecuteResponse {

    /** 对应请求的 ID */
    private String requestId;

    /** 是否执行成功 */
    private boolean success;

    /** 执行结果（JSON 序列化，成功时） */
    private String result;

    /** 结果类型（全限定类名，成功时） */
    private String resultType;

    /** 错误信息（失败时） */
    private String error;

    public ExecuteResponse() {
    }

    public ExecuteResponse(String requestId, boolean success, String result,
                           String resultType, String error) {
        this.requestId = requestId;
        this.success = success;
        this.result = result;
        this.resultType = resultType;
        this.error = error;
    }

    public static ExecuteResponse ok(String requestId, String result, String resultType) {
        return new ExecuteResponse(requestId, true, result, resultType, null);
    }

    public static ExecuteResponse error(String requestId, String error) {
        return new ExecuteResponse(requestId, false, null, null, error);
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
