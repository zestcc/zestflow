# Pattern: admin-decision

## 适用

审核通过/拒绝、工单决策、状态机迁移。

## 默认拓扑

```
START → loadAuditRecord → validateTransition → applyDecision → notifyResult → END
```

## compose_chain

```json
{ "patternId": "admin-decision", "chainCode": "CHN_AUDIT_DECIDE" }
```
