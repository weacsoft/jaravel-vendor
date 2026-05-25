package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.http.Middleware;
import com.weacsoft.jaravel.contract.http.Request;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.exception.UnauthorizedException;

/**
 * 认证中间件。
 *
 * <p>在请求处理前初始化认证状态，请求处理后销毁认证状态。
 * 未认证用户将被拦截并抛出 401 异常。</p>
 */
public class AuthMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next) {
        Auth.init();
        try {
            if (Auth.check()) {
                return next.apply(request);
            } else {
                throw new UnauthorizedException("Unauthorized");
            }
        } finally {
            Auth.destroy();
        }
    }
}
