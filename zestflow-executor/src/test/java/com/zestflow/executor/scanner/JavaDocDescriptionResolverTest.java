package com.zestflow.executor.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaDocDescriptionResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void parseMethodJavaDoc_extractsDescriptionAndTags() {
        String source = """
                package demo;
                public class BookHandler {
                    /**
                     * 根据书籍ID查询详情
                     * @param bookId 书籍ID（必填）
                     * @return BookVO 书籍详情
                     */
                    public BookVO execute(Long bookId) { return null; }
                }
                """;
        String doc = JavaDocDescriptionResolver.parseMethodJavaDoc(source, "execute");
        assertTrue(doc.contains("根据书籍ID查询详情"));
        assertTrue(doc.contains("@param bookId"));
        assertTrue(doc.contains("@return BookVO"));
    }

    @Test
    void resolve_prefersAnnotationDescription() {
        String result = JavaDocDescriptionResolver.resolve(null, null, "注解描述优先");
        assertEquals("注解描述优先", result);
    }

    @Test
    void locateSourceFile_findsUnderSrcMainJava() throws Exception {
        Path found = JavaDocDescriptionResolver.locateSourceFile(JavaDocDescriptionResolver.class);
        assertTrue(found != null && Files.exists(found));
        assertTrue(found.toString().replace('\\', '/').contains("JavaDocDescriptionResolver.java"));
    }
}
