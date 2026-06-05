package com.zestflow.executor.chain;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用当前声明的 chain_key 集合 — 用于删除保护和注册上报。
 */
public class ChainDeclarationRegistry {

    private final Set<String> declaredKeys = ConcurrentHashMap.newKeySet();

    public void replaceAll(Collection<ChainDeclarationMeta> declarations) {
        declaredKeys.clear();
        if (declarations != null) {
            for (ChainDeclarationMeta meta : declarations) {
                if (meta.getChainKey() != null && !meta.getChainKey().isBlank()) {
                    declaredKeys.add(meta.getChainKey().trim());
                }
            }
        }
    }

    public boolean isDeclared(String chainKey) {
        return chainKey != null && declaredKeys.contains(chainKey.trim());
    }

    public Set<String> getDeclaredKeys() {
        return Collections.unmodifiableSet(declaredKeys);
    }
}
