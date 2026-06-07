# ZestFlow 链定义规范

## ChainDefinition 结构

链 JSON（`chainData`）核心字段：

| 字段 | 说明 |
|------|------|
| `code` | 链编码（可选，校验时可占位） |
| `version` | 版本号 |
| `nodes` | 节点数组 |
| `edges` | 边数组 |
| `config` | 全局配置（可选） |

## 节点（node）

| 字段 | 说明 |
|------|------|
| `id` | 画布节点 id（图内唯一） |
| `componentId` | **必须**为已注册元件 id |
| `type` | 与 componentType 对齐 |
| `config` | 节点级参数 |

## 边（edge）

| 字段 | 说明 |
|------|------|
| `source` / `target` | 节点 id |
| `condition` | Aviator 表达式（可选） |

## 工作流

1. 读取 `zestflow://schema/chain-definition`  
2. 仅使用 `list_components` 返回的 id  
3. 生成 JSON 后 **必须** `validate_chain`  
4. 人工在设计器保存 → 发布 → reload

## 与 Admin Orchestration Copilot 的关系

- Admin 内 Copilot：0 代码编排、设计器 diff  
- MCP Dev Copilot：写 Java 元件 + 本地链 JSON 草稿校验  
- 二者互补，LLM 分别在 Admin / IDE 侧
