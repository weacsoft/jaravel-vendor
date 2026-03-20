package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.middleware.Middleware;

public class AuthMiddleware implements Middleware {

    @Override
    public Response handle(Request request, NextFunction next) {
        return null;
    }
}
