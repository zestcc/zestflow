package com.zestflow.executor.expression;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Aviator 表达式静态安全校验（编译/执行前）。
 */
final class AviatorExpressionSecurity {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("(?i)\\bRuntime\\b"),
            Pattern.compile("(?i)\\bProcessBuilder\\b"),
            Pattern.compile("(?i)\\bClass\\.forName\\b"),
            Pattern.compile("(?i)\\bjava\\.lang\\."),
            Pattern.compile("(?i)\\bjavax\\."),
            Pattern.compile("(?i)\\bjakarta\\."),
            Pattern.compile("(?i)\\breflect\\."),
            Pattern.compile("(?i)\\bgetClass\\s*\\("),
            Pattern.compile("(?i)\\bThread\\b"),
            Pattern.compile("(?i)\\bSystem\\."),
            Pattern.compile("(?i)\\bFile\\b"),
            Pattern.compile("(?i)\\bFiles\\."),
            Pattern.compile("(?i)\\bSocket\\b"),
            Pattern.compile("(?i)\\bURLClassLoader\\b"),
            Pattern.compile("(?i)\\bClassLoader\\b")
    );

    private AviatorExpressionSecurity() {
    }

    static void validate(String expr, int maxLength) {
        if (expr == null || expr.isEmpty()) {
            return;
        }
        if (expr.length() > maxLength) {
            throw new ExpressionEvaluationException(
                    "表达式长度超过限制 length=" + expr.length() + " max=" + maxLength);
        }
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(expr).find()) {
                throw new ExpressionEvaluationException("表达式包含禁止的语法片段");
            }
        }
    }
}
