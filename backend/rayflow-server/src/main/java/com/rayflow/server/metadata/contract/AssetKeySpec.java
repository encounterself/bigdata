package com.rayflow.server.metadata.contract;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AssetKeySpec {

    public static final String FORMAT = "tenant-{tenantId}:{platform}:{namespace}.{name}";
    public static final String TENANT_PREFIX = "tenant-";
    public static final String SEPARATOR = ":";
    private static final Pattern PLATFORM = Pattern.compile("[A-Z][A-Z0-9_]*");

    private AssetKeySpec() {
    }

    public static String canonical(long tenantId, String platform, String namespace, String name) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        String normalizedPlatform = requireToken(platform, "platform").toUpperCase(Locale.ROOT);
        if (!PLATFORM.matcher(normalizedPlatform).matches()) {
            throw new IllegalArgumentException("platform must contain only uppercase letters, digits, and underscores");
        }
        return TENANT_PREFIX + tenantId + SEPARATOR + normalizedPlatform.toLowerCase(Locale.ROOT) + ":"
                + requireNamespace(namespace) + "." + requireName(name);
    }

    public static void requireTenant(String assetKey, long tenantId) {
        Objects.requireNonNull(assetKey, "assetKey");
        String prefix = TENANT_PREFIX + tenantId + SEPARATOR;
        if (!assetKey.startsWith(prefix)) {
            throw new IllegalArgumentException("assetKey does not belong to tenant " + tenantId);
        }
    }

    private static String requireToken(String value, String field) {
        if (value == null || value.isBlank() || value.indexOf(':') >= 0) {
            throw new IllegalArgumentException(field + " must be non-blank and must not contain ':'");
        }
        return value.trim();
    }

    private static String requireNamespace(String value) {
        return requireToken(value, "namespace");
    }

    private static String requireName(String value) {
        String name = requireToken(value, "name");
        if (name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("name must not contain '.'");
        }
        return name;
    }
}
