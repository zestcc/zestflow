---
name: frontend-table-standard
description: 前端所有 el-table 列表页的统一样式规范（show-overflow-tooltip + 紧凑操作栏）
metadata:
  type: reference
---

## 前端列表页表格标准（2026-05-30 确立）

所有 `el-table` 列表页必须遵守以下规范：

1. **所有内容列加 `show-overflow-tooltip`** — 编码、名称、描述、时间等任何可能超长的列，保证超长时显示 `...` + 悬浮气泡展示全文。标签（el-tag）、数字等定宽内容列可例外。

2. **操作列紧凑** — 固定宽度 `190px~200px`（3按钮）或 `220px~240px`（4-5按钮），按钮内边距使用 `padding: 2px 4px`，通过 `.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }` CSS 类实现。

3. **表头样式统一** — 所有 el-table 统一使用 `:header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"`。

4. **列宽策略** — 编码类列用固定 px 确保不换行，名称/描述类列用 min-width + show-overflow-tooltip，操作列固定 px + fixed="right"。
