package com.weacsoft.jaravel.http.response;

import java.util.List;
import java.util.Map;

public interface Response {
    int getStatus();
    Map<String, List<String>> getHeaders();
    String getContent();
}
