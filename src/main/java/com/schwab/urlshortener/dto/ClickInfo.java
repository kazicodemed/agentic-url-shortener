package com.schwab.urlshortener.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Immutable snapshot of the request data analytics needs.
 *
 * Copied on the request thread before handing off to the async analytics
 * thread: the servlet container recycles HttpServletRequest objects once the
 * request completes, so the async thread must never touch the request itself.
 */
public record ClickInfo(String shortCode, String ipAddress, String userAgent, String referer) {

    public static ClickInfo from(String shortCode, HttpServletRequest request) {
        return new ClickInfo(
                shortCode,
                clientIp(request),
                request.getHeader("User-Agent"),
                request.getHeader("Referer"));
    }

    private static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
