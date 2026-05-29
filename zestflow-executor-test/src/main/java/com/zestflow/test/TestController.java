package com.zestflow.test;

import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.executor.engine.ChainInstance;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final ComponentScanner componentScanner;
    private final ChainExecutionEngine chainExecutionEngine;

    @GetMapping("/components")
    public Map<String, Object> listComponents() {
        return Map.of(
                "count", componentScanner.componentCount(),
                "ids", componentScanner.getComponentIds()
        );
    }

    @PostMapping("/execute/{chainCode}")
    public Object executeChain(@PathVariable String chainCode,
                                @RequestBody(required = false) Map<String, Object> params) {
        return chainExecutionEngine.execute(chainCode,
                params != null ? params : Map.of());
    }

    @GetMapping("/running")
    public List<ChainInstance> listRunning() {
        return chainExecutionEngine.listRunning(null);
    }
}
