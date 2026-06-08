# Pattern: publish-workflow

## 适用

草稿发布、审核上架、状态流转（DRAFT → PUBLISHED）。

## 默认拓扑

```
START → validatePublish → loadDraft → transformToBook → persistPublished → indexOrNotify → END
```

## compose_chain

```json
{ "patternId": "publish-workflow", "chainCode": "CHN_MANUSCRIPT_PUBLISH" }
```
