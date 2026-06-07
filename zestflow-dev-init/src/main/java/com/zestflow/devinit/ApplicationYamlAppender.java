package com.zestflow.devinit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 向已有 application.yml **增量追加** import（多文档 {@code ---}），绝不整文件覆盖。
 */
public final class ApplicationYamlAppender {

    static final String MARKER = "# zestflow-dev-init: import application-zestflow.yml";
    private static final Pattern INLINE_ZESTFLOW = Pattern.compile("(?m)^zestflow\\s*:");
    private static final String APPEND_BLOCK = "\n---\n"
            + MARKER + "\n"
            + "spring:\n"
            + "  config:\n"
            + "    import: optional:classpath:application-zestflow.yml\n";

    private ApplicationYamlAppender() {
    }

    /**
     * @return true 表示已追加
     */
    public static boolean appendZestflowImportIfNeeded(Path applicationYml) throws IOException {
        if (!Files.isRegularFile(applicationYml)) {
            return false;
        }
        String text = IoUtil.readFile(applicationYml);
        if (text.contains("application-zestflow.yml") || text.contains(MARKER)) {
            return false;
        }
        if (INLINE_ZESTFLOW.matcher(text).find()) {
            return false;
        }
        String merged = text.trim() + APPEND_BLOCK;
        IoUtil.writeFile(applicationYml, merged);
        return true;
    }
}
