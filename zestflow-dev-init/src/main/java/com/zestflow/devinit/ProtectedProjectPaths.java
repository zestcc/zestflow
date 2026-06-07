package com.zestflow.devinit;

import java.nio.file.Path;
import java.util.Locale;

/**
 * init-dev 与 AI 均不得覆盖的受保护路径（即使 {@code --force}）。
 */
public final class ProtectedProjectPaths {

    private ProtectedProjectPaths() {
    }

    public static boolean isProtected(Path projectRoot, Path target) {
        if (projectRoot == null || target == null) {
            return false;
        }
        Path normalized = target.toAbsolutePath().normalize();
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            return false;
        }
        String relative = root.relativize(normalized).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (relative.equals("pom.xml") || (relative.endsWith("/pom.xml") && !relative.contains("/src/"))) {
            return true;
        }
        if (!relative.contains("/src/main/resources/")) {
            return false;
        }
        if (relative.endsWith("/application.yml")
                || relative.endsWith("/application-local.yml")
                || relative.endsWith("/application-prod.yml")) {
            return true;
        }
        return relative.contains("/application-") && relative.endsWith(".yml")
                && !relative.contains(".example.");
    }

    public static boolean isProtectedRelative(String relativeTarget) {
        if (Strings.isBlank(relativeTarget)) {
            return false;
        }
        String path = relativeTarget.replace('\\', '/').toLowerCase(Locale.ROOT);
        if ("pom.xml".equals(path) || path.endsWith("/pom.xml")) {
            return !path.contains("/src/");
        }
        return path.contains("/src/main/resources/application.yml")
                || path.contains("/src/main/resources/application-local.yml")
                || path.contains("/src/main/resources/application-prod.yml");
    }
}
