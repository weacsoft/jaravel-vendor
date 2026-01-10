package com.weacsoft.jaravel.auth.middleware;

import com.weacsoft.jaravel.auth.Auth;
import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.http.response.ResponseBuilder;
import com.weacsoft.jaravel.middleware.Middleware;

public class Authenticate implements Middleware {

    private final String guard;

    public Authenticate() {
        this.guard = null;
    }

    public Authenticate(String guard) {
        this.guard = guard;
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        if (guard != null) {
            if (Auth.check(guard)) {
                return next.apply(request);
            }
        } else {
            if (Auth.check()) {
                return next.apply(request);
            }
        }

        return ResponseBuilder.unauthorized("Unauthenticated");
    }
}
