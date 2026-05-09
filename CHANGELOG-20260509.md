# Changelog 2026-05-09

本次变更对应提交：`e037aac Restructure workspace and add frontend app`

## 1. 仓库结构调整

- 后端工程从仓库根目录下沉到 `rag-backend/`。
- 后端的周记、样本、评测与阶段文档迁移到 `rag-backend/work/`。
- 新增 `rag-frontend/`，前端工程正式进入仓库。
- 根目录新增聚合 `pom.xml`，用于统一工作区建模和提升 IDE 识别稳定性。
- `rag-system.code-workspace` 调整为只挂载仓库根目录，避免 Java 项目重复导入。

## 2. 前端新增与接入

- 新增 React + TypeScript + Vite 前端工程。
- 已接入知识库、文档、检索、问答、历史、健康检查等核心页面。
- 已封装统一 API Client、错误提示、`requestId` 展示、轮询逻辑和状态组件。
- 已验证 `npm run build` 可成功构建。

## 3. 后端修复

- 修复 `/api/health/redis-probe` 的前后端方法不一致问题，接口现在兼容 `GET` 和 `POST`。
- 补充 `HealthControllerTest`，覆盖 Redis 探针接口的兼容行为。
- 修复请求访问日志可能阻塞 HTTP 线程的问题：
  - `RequestIdFilter` 中的请求开始/结束日志改为异步提交。
  - 当日志输出阻塞时，请求线程不会再被控制台写操作拖死。

## 4. IDE 与工程配置修复

- 修复 VS Code Spring Boot Dashboard 无法稳定识别后端项目的问题：
  - 根目录补充聚合 `pom.xml`
  - 调整 VS Code workspace 结构
- 修复前端 `tsconfig` 的 TypeScript 诊断：
  - `moduleResolution` 从 `Node` 调整为 `Bundler`
  - 移除已弃用的 `baseUrl`
- 修复 `tsconfig` 外部 schema 无法访问导致的 JSON 诊断：
  - 改为本地 `tsconfig.schema.json`
  - 不再依赖 `schemastore.org`

## 5. README 与文档补充

- 根目录 `README.md` 已补充：
  - 更准确的系统级介绍
  - 更详细的工程结构说明
  - VS Code / Spring Boot Dashboard 使用说明
  - 更新后的文档路径
- `rag-frontend/work/frontend plan.md` 已补充：
  - 当前完成情况
  - 近期可补强工作
  - 中长期扩展方向

## 6. 综合联调结果

已完成一轮真实联调，覆盖：

- 健康检查
- Redis Probe
- 知识库创建、查询、启停
- 文档上传、详情、列表
- 同步 `process / reprocess / embed / chunks / disable`
- 异步 `index / indexing-tasks / retry`
- `qa/readiness`
- `qa/retrieve`
- `qa/ask`
- `qa/history`

联调结论：

- 主链路可用
- 前端开发服务可访问
- 前端生产构建通过
- 后端在日志阻塞风险修复后可稳定响应接口

## 7. 后续建议

- 增加浏览器级 UI 自动化，补齐前端点击链路验收。
- 继续做前端生产包拆分，降低主 bundle 体积。
- 为后端结构化日志补更长期的输出策略，例如独立 appender 或文件落盘配置。
- 如果后续继续单仓开发，建议把根目录 README 与阶段变更摘要持续分离维护。
