package com.weacsoft.jaravel.route;

public class RouteService {

    public static String normalizeUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "/";
        }
        uri = uri
                .replaceAll("\\s+", "")
                .replaceAll("/+", "/")
                .replaceAll("^(?!/)", "/");
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    public static String normalizeNamesapce(String namespace) {
        if (namespace == null || namespace.trim().isEmpty()) {
            return "";
        }
        namespace = namespace
                .replaceAll("\\s+", "")
                .replaceAll("\\.+", ".")
                .replaceAll("^(?!\\.)", ".");
        namespace = namespace.trim().replaceAll("^\\.|\\.$", "");
        return namespace;
    }

    public static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        return name
                .replaceAll("\\s+", "")
                .replaceAll("\\.+", ".")
                .replaceAll("^(?!\\.)", ".");
    }
}
