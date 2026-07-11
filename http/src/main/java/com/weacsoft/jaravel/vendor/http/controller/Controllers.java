package com.weacsoft.jaravel.vendor.http.controller;

import com.weacsoft.jaravel.vendor.http.controller.request.Request;
import com.weacsoft.jaravel.vendor.http.controller.response.Response;

public interface Controllers {
    @FunctionalInterface
    interface Runner {
        Response handle(Request request);
    }
}
