package com.weacsoft.jaravel.http.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseBuilder {
    public static Response ok() {
        return new Response() {
            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public Map<String, List<String>> getHeaders() {
                return new HashMap<>();
            }

            @Override
            public String getContent() {
                return "ok";
            }
        };
    }
}
