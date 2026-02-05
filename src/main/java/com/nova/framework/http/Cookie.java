package com.nova.framework.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP Cookie
 */
public record Cookie(
        String name,
        String value,
        String path,
        String domain,
        Integer maxAge,
        boolean secure,
        boolean httpOnly,
        String sameSite) {

    // Default values
    private static final String DEFAULT_PATH = "/";
    private static final String DEFAULT_SAME_SITE = "Lax";

    public Cookie(String name, String value) {
        this(name, value, DEFAULT_PATH, null, null, false, true, DEFAULT_SAME_SITE);
    }

    /**
     * Compact constructor with validation
     */
    public Cookie {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Cookie name cannot be null or empty");
        }
        // Validate cookie name (no special characters)
        if (name.contains("=") || name.contains(";") || name.contains(",") ||
                name.contains(" ") || name.contains("\t") || name.contains("\r") || name.contains("\n")) {
            throw new IllegalArgumentException("Cookie name contains invalid characters");
        }
        if (value == null) {
            value = "";
        }
        // Validate cookie value (no control characters)
        if (value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Cookie value contains invalid characters (CR/LF)");
        }
    }

    /**
     * Convert to Set-Cookie header value
     */
    public String toSetCookieHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=");

        // URL-encode the value for safety
        try {
            sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        } catch (Exception e) {
            sb.append(value);
        }

        if (path != null) {
            sb.append("; Path=").append(path);
        }
        if (domain != null) {
            sb.append("; Domain=").append(domain);
        }
        if (maxAge != null) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (sameSite != null) {
            sb.append("; SameSite=").append(sameSite);
        }

        return sb.toString();
    }

    /**
     * Cookie builder
     */
    public static Builder builder(String name, String value) {
        return new Builder(name, value);
    }

    public static class Builder {
        private final String name;
        private final String value;
        private String path = "/";
        private String domain = null;
        private Integer maxAge = null;
        private boolean secure = false;
        private boolean httpOnly = true;
        private String sameSite = "Lax";

        public Builder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder maxAge(int seconds) {
            this.maxAge = seconds;
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public Builder httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        public Builder sameSite(String sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        public Cookie build() {
            return new Cookie(name, value, path, domain, maxAge, secure, httpOnly, sameSite);
        }
    }
}
