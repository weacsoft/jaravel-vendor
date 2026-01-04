package com.weacsoft.jaravel.controller;

import com.weacsoft.jaravel.http.request.Request;
import com.weacsoft.jaravel.http.response.Response;

public interface Controller {
    @FunctionalInterface
    interface Runner {
        Response handle(Request request);
    }
}
