package com.weacsoft.jaravel.vendor.springboot;

import com.weacsoft.jaravel.vendor.http.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseReturnValueHandler 返回值类型判断逻辑测试。
 * 测试 supportsReturnType 对 Response 类型及其子类型的识别。
 */
class ResponseReturnValueHandlerTest {

    private final ResponseReturnValueHandler handler = new ResponseReturnValueHandler();

    /** 测试用 Controller，包含不同返回类型的方法 */
    static class TestController {
        public Response handleResponse() { return null; }
        public String handleString() { return null; }
        public Object handleObject() { return null; }
        public Integer handleInteger() { return null; }
    }

    private MethodParameter returnParamOf(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getMethod(methodName);
        return new MethodParameter(method, -1);
    }

    @Test
    void supportsResponseReturnType() throws Exception {
        assertTrue(handler.supportsReturnType(returnParamOf("handleResponse")),
                "Response 返回类型应被支持");
    }

    @Test
    void doesNotSupportStringReturnType() throws Exception {
        assertFalse(handler.supportsReturnType(returnParamOf("handleString")),
                "String 返回类型不应被支持");
    }

    @Test
    void doesNotSupportObjectReturnType() throws Exception {
        assertFalse(handler.supportsReturnType(returnParamOf("handleObject")),
                "Object 返回类型不应被支持");
    }

    @Test
    void doesNotSupportIntegerReturnType() throws Exception {
        assertFalse(handler.supportsReturnType(returnParamOf("handleInteger")),
                "Integer 返回类型不应被支持");
    }
}
