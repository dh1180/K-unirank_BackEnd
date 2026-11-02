package com.kunirank.api.config;

/** Documentation GETs only; never a bypass for API controllers. */
public final class DocumentationRoutes {
    private DocumentationRoutes() {}
    public static boolean isRead(String method,String path) {
        return ("GET".equals(method)||"HEAD".equals(method)) && (
            path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
            || path.equals("/v3/api-docs") || path.equals("/v3/api-docs.yaml")
            || path.startsWith("/v3/api-docs/"));
    }
}
