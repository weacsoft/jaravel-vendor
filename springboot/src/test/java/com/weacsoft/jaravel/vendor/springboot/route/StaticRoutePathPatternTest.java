package com.weacsoft.jaravel.vendor.springboot.route;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 静态资源路由的 Spring {@code PathPattern} 语义回归测试（审计 M3 / R9）。
 * <p>
 * 背景：静态资源路由原先用 {@code /static/{path}}（只匹配单段 → 嵌套资源恒 404）。
 * 修复为 {@code /static/{*path}}（capture-the-rest）后有两个必须钉死的性质：
 * <ol>
 *   <li>{@code {*path}} 能匹配多段路径（含前导 {@code /} 的剩余路径）与 {@code /static} 本身；</li>
 *   <li><b>不能</b>给它拼接尾斜杠变体（{@code /static/{*path}/}）——PathPattern 语法要求
 *       capture-the-rest 必须是模式的最后一个元素，拼接后会解析失败（轻则该谓词无效，
 *       重则应用启动失败）。这正是 {@code createRoutePredicate} 里「含 {* 则跳过尾斜杠变体」
 *       这一段守卫存在的原因。</li>
 * </ol>
 * 本测试直接验证 Spring 的谓词行为，无需启动 Tomcat / 浏览器（本机可跑）。
 */
class StaticRoutePathPatternTest {

    private static ServerRequest request(String path) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.path()).thenReturn(path);
        // RequestPredicates 实际读取 requestPath().pathWithinApplication()，两者都要给
        when(request.requestPath()).thenReturn(
                org.springframework.http.server.RequestPath.parse(path, ""));
        return request;
    }

    @Test
    void captureTheRestMatchesNestedStaticResources() {
        RequestPredicate predicate = RequestPredicates.path("/static/{*path}");

        assertTrue(predicate.test(request("/static/css/app.css")),
                "多段静态资源必须命中（旧 {path} 写法恒 404）");
        assertTrue(predicate.test(request("/static/a/b/c/d.png")), "任意深度都必须命中");
        assertTrue(predicate.test(request("/static/app.css")), "单段也必须命中");
    }

    @Test
    void captureTheRestMatchesPrefixWithoutTrailingSegment() {
        RequestPredicate predicate = RequestPredicates.path("/static/{*path}");

        // {*path} 允许「空剩余」，因此 /static 与 /static/ 都能命中（无需尾斜杠变体）
        assertDoesNotThrow(() -> predicate.test(request("/static")));
        assertDoesNotThrow(() -> predicate.test(request("/static/")));
    }

    @Test
    void trailingSlashVariantIsInvalidPattern() {
        // 这就是「必须跳过尾斜杠变体」的证据：拼上 '/' 后 PathPattern 无法解析
        assertThrows(Exception.class, () -> RequestPredicates.path("/static/{*path}/"),
                "capture-the-rest 后不得再跟模式数据，否则解析失败（曾有启动失败风险）");
    }

    @Test
    void singleSegmentPatternWouldMissNestedPaths() {
        // 反向断言：旧写法确实无法匹配多段（解释这个修复的必要性）
        RequestPredicate oldStyle = RequestPredicates.path("/static/{path}");
        assertTrue(oldStyle.test(request("/static/app.css")), "旧写法单段仍可命中");
        assertFalse(oldStyle.test(request("/static/css/app.css")),
                "旧写法对多段路径不命中 —— 这正是审计 M3 的功能缺陷");
    }
}