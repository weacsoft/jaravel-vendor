package com.weacsoft.jaravel.vendor.controller;

import com.weacsoft.jaravel.vendor.http.request.Request;
import com.weacsoft.jaravel.vendor.http.response.Response;

public interface Controllers {
    @FunctionalInterface
    interface Runner {
        Response handle(Request request);
    }
}