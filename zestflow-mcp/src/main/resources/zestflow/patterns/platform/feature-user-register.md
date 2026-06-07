# 用户注册链（平台 L1 模板）

## 推荐步骤（Chain-first）

| 顺序 | componentId | 类型 | 节点 | 说明 |
|------|-------------|------|------|------|
| 1 | parseRegisterRequest | LOADER | NORMAL | `@ZestParam` 解析 phone/password |
| 2 | validateRegisterParams | PARAM_VALIDATOR | NORMAL | 格式校验 |
| 3 | checkUserExists | PREDICATE | CONDITION | 已注册则短路 |
| 4 | createUser | EXECUTOR | NORMAL | 写库 |
| 5 | sendNotify | EXECUTOR | NORMAL | **优先复用** |
| 6 | parseRegisterResponse | PARSER | NORMAL | Mode1/2 HTTP 响应 |

## chainCtx 键

- 入参：`phone`, `password`
- 中间：`registerReq`, `userExists`
- 出参：`userId`

## 建议 chainCode

`CHN_USER_REGISTER`

## Playground

- Mode1：`POST /execute` + `{ chainCode, params }`
- Mode2：`POST /api/.../register` + params only
