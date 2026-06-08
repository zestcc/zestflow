# Pattern: auth-owned-write

## 适用

作者/用户修改**自己拥有**的资源（上架信息、个人资料、私有配置）。

## 默认拓扑

```
START → validateRequest → loadResource → authorizeOwner → mutateResource → syncSideEffect → END
```

## compose_chain

```json
{ "patternId": "auth-owned-write", "chainCode": "CHN_YOUR_FEATURE" }
```

## 元件职责

| 节点 | 职责 |
|------|------|
| validateRequest | 参数格式与必填 |
| loadResource | 加载目标实体 |
| authorizeOwner | 比对 currentUserId 与 ownerId |
| mutateResource | 写库 |
| syncSideEffect | 同步关联表/缓存/索引 |
