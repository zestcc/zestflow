# Pattern: guest-gated-read

## 适用

试读/登录门禁读（章节正文、付费内容、会员资源）。

## 默认拓扑

```
START → loadBookMeta → checkPreviewOrAuth → loadChapterContent → END
```

## 失败路径

- 游客读非试读章节 → 403 + BusinessException
- 资源不存在 → 404

## compose_chain

```json
{ "patternId": "guest-gated-read", "chainCode": "CHN_CHAPTER_DETAIL" }
```
