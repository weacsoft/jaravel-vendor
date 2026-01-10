package com.weacsoft.jaravel.auth.middleware;

import com.weacsoft.jaravel.auth.Auth;
import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;
import com.weacsoft.jaravel.http.response.ResponseBuilder;
import com.weacsoft.jaravel.middleware.Middleware;

public class Guest implements Middleware {

    private final String guard;

    public Guest() {
        this.guard = null;
    }

    public Guest(String guard) {
        this.guard = guard;
    }

    @Override
    public Response handle(Request request, NextFunction next) {
        if (guard != null) {
            if (Auth.guest(guard)) {
                return next.apply(request);
            }
        } else {
            if (Auth.guest()) {
                return next.apply(request);
            }
        }

        return ResponseBuilder.redirect("/");
    }
}
