package com.weacsoft.jaravel.vendor.plugin.jar.remote.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExecuteResponse} 构建与字段访问单元测试。
 */
class ExecuteResponseTest {

    @Test
    void okFactoryBuildsSuccessResponse() {
        ExecuteResponse resp = ExecuteResponse.ok("req-1", "{\"v\":42}", "java.lang.Integer");

        assertTrue(resp.isSuccess());
        assertEquals("req-1", resp.getRequestId());
        assertEquals("{\"v\":42}", resp.getResult());
        assertEquals("java.lang.Integer", resp.getResultType());
        assertNull(resp.getError());
    }

    @Test
    void errorFactoryBuildsFailureResponse() {
        ExecuteResponse resp = ExecuteResponse.error("req-2", "boom");

        assertFalse(resp.isSuccess());
        assertEquals("req-2", resp.getRequestId());
        assertEquals("boom", resp.getError());
        assertNull(resp.getResult());
        assertNull(resp.getResultType());
    }

    @Test
    void settersOverrideFields() {
        ExecuteResponse resp = new ExecuteResponse();
        resp.setRequestId("req-3");
        resp.setSuccess(true);
        resp.setResult("ok");
        resp.setResultType("java.lang.String");
        resp.setError(null);

        assertEquals("req-3", resp.getRequestId());
        assertTrue(resp.isSuccess());
        assertEquals("ok", resp.getResult());
        assertEquals("java.lang.String", resp.getResultType());
        assertNull(resp.getError());
    }

    @Test
    void fullConstructorAssignsAllFields() {
        ExecuteResponse resp = new ExecuteResponse("req-4", false, null, null, "denied");

        assertFalse(resp.isSuccess());
        assertEquals("req-4", resp.getRequestId());
        assertEquals("denied", resp.getError());
    }
}
