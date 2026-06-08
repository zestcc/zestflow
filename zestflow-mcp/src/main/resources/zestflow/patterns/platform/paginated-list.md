# Pattern: paginated-list

## 适用

分页列表、搜索列表、管理后台表格。

## 默认拓扑

```
START → parsePageQuery → countRecords → fetchPageRecords → mapToVoList → END
```

## compose_chain

```json
{ "patternId": "paginated-list", "chainCode": "CHN_LIST_FEATURE" }
```
