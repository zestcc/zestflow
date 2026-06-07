package com.zestflow.devinit;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code --init-dev} 执行结果。
 */
public final class DevInitResult {

    private final List<String> created;
    private final List<String> skipped;
    private final Map<String, String> variables;
    private final List<String> warnings;

    public DevInitResult(List<String> created, List<String> skipped, Map<String, String> variables) {
        this(created, skipped, variables, Collections.<String>emptyList());
    }

    public DevInitResult(
            List<String> created,
            List<String> skipped,
            Map<String, String> variables,
            List<String> warnings) {
        this.created = created;
        this.skipped = skipped;
        this.variables = variables;
        this.warnings = warnings;
    }

    public List<String> created() {
        return created;
    }

    public List<String> skipped() {
        return skipped;
    }

    public Map<String, String> variables() {
        return variables;
    }

    public List<String> warnings() {
        return warnings;
    }
}
