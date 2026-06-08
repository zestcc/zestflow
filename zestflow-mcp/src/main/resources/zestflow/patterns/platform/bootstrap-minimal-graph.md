# Pattern: bootstrap-minimal-graph

## 适用

Dev Seeder / DeclarationSync **占位链**（非 production 交付）。

## 最小 graph

```
START → mainTask → END
```

## 约束

- `config.lifecycle = bootstrap`
- 设计器可见连通拓扑（禁止 0 边空壳）
- **禁止**用 bootstrap 链向用户宣称功能已交付

## 功能交付

须 `compose_chain` + `lifecycle=production` + `validate_delivery`.
