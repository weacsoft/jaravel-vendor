package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.contract.http.Middleware;
import com.weacsoft.jaravel.contract.http.Request;
import com.weacsoft.jaravel.contract.http.Response;
import com.weacsoft.jaravel.exception.UnauthorizedException;

public class AuthMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next) {
        if (Auth.check()) {
            return next.apply(request);
        } else {
            throw new UnauthorizedException("Unauthorized");
        }
    }
}
