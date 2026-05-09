# RAG 前端系统开发计划

## Summary

基于 README.md、`rag-backend` 中的 Controller / Service / Entity / Flyway 实现，后端已经具备“上传原文档 + 异步索引 + 检索 + 问答 + 来源 + 历史”闭环。前端围绕“知识库 -> 文档 -> 异步索引 -> 检索/问答 -> 历史”设计为向导式工作台，主入口使用异步 `index` 任务，不把 `process` / `embed` 作为普通用户主流程。

## 1. 当前后端能力梳理

  - 统一响应结构：全部接口返回 ApiResponse<T>，字段为 code / message / data / requestId / timestamp；前端错误提示要
    显示 message，调试信息保留 requestId。
  - 知识库接口：
      - POST /api/knowledge-bases
      - GET /api/knowledge-bases?status&pageNo&pageSize
      - GET /api/knowledge-bases/{kbCode}
      - POST /api/knowledge-bases/{kbCode}/enable
      - POST /api/knowledge-bases/{kbCode}/disable
      - DELETE /api/knowledge-bases/{kbCode}
  - 文档接口：
      - POST /api/knowledge-bases/{kbCode}/documents/upload
      - GET /api/knowledge-bases/{kbCode}/documents?status&pageNo&pageSize
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/chunks
      - POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/process
      - POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/reprocess
      - POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/embed
      - POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks
      - POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry
      - 代码里还有 POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/disable，README 当前未列出，前端可以
        接入为“高级操作”。
  - 问答接口：
      - GET /api/knowledge-bases/{kbCode}/qa/readiness
      - POST /api/knowledge-bases/{kbCode}/qa/retrieve
      - POST /api/knowledge-bases/{kbCode}/qa/ask
      - GET /api/knowledge-bases/{kbCode}/qa/history?pageNo&pageSize
  - 健康接口：
      - GET /api/health
      - GET /api/health/redis-probe
  - 状态模型：
      - 知识库：ACTIVE / INACTIVE
      - 文档：UPLOADED / PARSING / PARSED / CHUNKING / INDEXED / FAILED / DISABLED
      - chunk：status=ACTIVE / DISABLED，embeddingStatus=PENDING / EMBEDDING / EMBEDDED / FAILED
      - 索引任务：status=QUEUED / RUNNING / SUCCEEDED / FAILED，taskStage=QUEUED / DOCUMENT_PROCESSING /
        DOCUMENT_EMBEDDING / COMPLETED，triggerSource=SUBMIT / MANUAL_RETRY / RECOVERY
  - 关键数据结构：
      - 知识库：kbCode / name / description / status / createdBy / createdAt
      - 文档：documentCode / displayName / fileType / mediaType / fileSize / status / source / tags / errorMessage
      - chunk：chunkIndex / title / content / tokenCount / startOffset / endOffset / metadataJson / embeddingStatus
      - 索引任务：taskId / status / taskStage / chunkCount / embeddedChunkCount / retryCount / maxRetryCount /
        errorMessage / startedAt / finishedAt / lastHeartbeatAt
      - 问答结果：question / answer / topK / chatModel / retrievalResults / sources
      - 历史记录：sessionCode / sessionName / messageCode / question / answer / latencyMs / retrievalResults /
        sources / createdAt
  - 已确认的业务约束：
      - 上传仅支持 md / txt / pdf
      - 默认上传上限 20MB
      - 检索默认 topK=5，最大 10
      - 列表分页默认 pageNo=1、pageSize=20，最大 100
      - qa/readiness 通过 knowledgeBaseStatus + indexedChunkCount + embeddedChunkCount 判断是否可问答
      - 删除知识库会级联删除文档、chunk、索引任务、问答历史和本地上传物料；若仍有运行中的索引任务则不允许删除
      - 当前问答历史是“每次问答新建一个 session”，不是多轮会话；历史页应按单问单答记录展示，不要先设计成连续聊天线
        程
      - 没有 SSE / WebSocket / 任务进度百分比接口；索引监控必须用轮询 indexing-tasks + document detail + chunks

## 2. 前端页面规划

  - 登录后首页 / 工作台
      - 解决“第一次进来不知道做什么”的问题
      - 展示知识库列表、全局说明、推荐下一步、最近问答入口
  - 知识库列表页
      - 解决知识库创建、切换、状态筛选、启停管理、删除管理
  - 知识库创建页 / 抽屉
      - 解决首次建立知识库，输入 kbCode / name / description / createdBy
  - 知识库概览页
      - 解决“当前库是否可问答、下一步该干什么”
      - 核心显示 qa/readiness、文档数量、最新索引任务、快捷入口
  - 文档接入页
      - 解决上传文件、填写展示名/标签/来源、立即发起索引
      - 这是向导第 1 步
  - 文档列表页
      - 解决查看当前库所有文档、筛选状态、进入详情、发起重试
  - 文档详情/索引监控页
      - 解决观察单文档从 UPLOADED 到 INDEXED 的全过程
      - 展示文档状态、最近任务、任务阶段、chunk 数、embedded 数、错误信息、重试按钮、chunk 预览
  - 检索调试页
      - 解决问答前验证召回质量，适合简历展示“RAG 可解释性”
      - 输入问题和 topK，返回命中 chunks、score、文档来源
  - 问答页
      - 解决用户真正完成一次 RAG 问答
      - 左侧输入问题，右侧答案卡片、来源卡片、检索命中、就绪提示
  - 来源查看抽屉/弹窗
      - 解决从答案回溯 chunk 内容、offset、文档名
  - 问答记录页
      - 解决按知识库查看历史问答、复看答案和来源
  - 系统健康页
      - 解决演示时确认后端依赖是否正常，适合作为开发/演示辅助页，不作为普通主导航

## 3. 用户操作链路设计

  1. 首次进入系统到工作台，默认看到“创建知识库”主按钮和 3 步引导提示。
  2. 创建知识库后跳转到知识库概览页，立即调用 qa/readiness；此时应显示“不可问答”与下一步提示“先上传并处理至少一篇文
     档”。
  3. 用户进入文档接入页上传文件，上传成功后立即提示“文档已归档，是否开始索引”；默认直接调用 index。
  4. 前端进入文档详情/索引监控页，轮询 document detail + indexing-tasks。
  5. 若任务 QUEUED/RUNNING，展示阶段型时间线：排队中 -> 解析切块中 -> 向量化中 -> 完成。
  6. 若任务失败，展示 errorMessage、最近阶段、重试次数、retry 按钮；若文档状态 FAILED，允许“重新索引”或“开发者模式
     下 process/embed 分步调试”。
  7. 索引成功后，概览页的 qa/readiness 变为可用；CTA 从“继续上传文档”切到“开始检索/问答”。
  8. 用户可先去检索调试页做一次 retrieve，确认召回质量，再进入问答页。
  9. 问答页调用 ask，返回答案、检索结果、来源；前端同步渲染来源列表和 chunk 证据。
  10. 用户点击来源卡片查看 chunk 原文与文档定位信息。
  11. 问答结束后，用户进入历史页查看刚才的问题、回答、来源和延迟。
  12. 若用户再次进入同一知识库，首页优先展示“上次进行到哪一步”和“继续问答 / 查看失败任务”。

## 4. 组件拆分建议

  - AppShell：全局布局、顶部知识库切换、侧边导航
  - WizardStepper：向导步骤条，复用于概览页、接入页、详情页
  - StatusBadge：统一渲染 KB/文档/任务/chunk/embedding 状态
  - ReadinessCard：展示 questionAnsweringReady / nextStep / indexedChunkCount / embeddedChunkCount
  - UploadPanel：文件选择、校验、表单项、上传动作
  - DocumentTable：文档列表、状态筛选、操作列
  - IndexingTimeline：任务状态和阶段时间线，复用到列表行展开和详情页
  - RetryActionBar：失败说明、重试入口、调试入口
  - ChunkPreviewList：chunk 内容、token、offset、embeddingStatus
  - RetrievalResultList：召回结果、score、来源跳转
  - AnswerCard：答案正文、模型名、topK
  - SourceList：来源列表，支持抽屉打开原文
  - HistoryTable：问答历史列表
  - ApiErrorAlert：统一展示 message + requestId
  - PollingController：管理轮询生命周期，避免每个页面重复写定时器逻辑

## 5. API 对接清单

  - 工作台 / 知识库列表页：
      - GET /api/knowledge-bases
      - DELETE /api/knowledge-bases/{kbCode}
  - 创建知识库：
      - POST /api/knowledge-bases
  - 知识库概览页：
      - GET /api/knowledge-bases/{kbCode}
      - GET /api/knowledge-bases/{kbCode}/qa/readiness
      - GET /api/knowledge-bases/{kbCode}/documents?pageNo=1&pageSize=...
  - 文档接入页：
      - POST /api/knowledge-bases/{kbCode}/documents/upload
      - 上传成功后 POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/index
  - 文档列表页：
      - GET /api/knowledge-bases/{kbCode}/documents
  - 文档详情 / 索引监控页：
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks
      - GET /api/knowledge-bases/{kbCode}/documents/{documentCode}/chunks
      - 失败时 POST /api/knowledge-bases/{kbCode}/documents/{documentCode}/indexing-tasks/{taskId}/retry
      - 高级模式可选 POST /process、POST /reprocess、POST /embed
  - 检索调试页：
      - GET /api/knowledge-bases/{kbCode}/qa/readiness
      - POST /api/knowledge-bases/{kbCode}/qa/retrieve
  - 问答页：
      - GET /api/knowledge-bases/{kbCode}/qa/readiness
      - POST /api/knowledge-bases/{kbCode}/qa/ask
  - 问答历史页：
      - GET /api/knowledge-bases/{kbCode}/qa/history
  - 系统健康页：
      - GET /api/health
      - GET /api/health/redis-probe

## 6. 状态展示设计

  - 知识库状态：
      - ACTIVE 显示绿色“可用”
      - INACTIVE 显示灰色“已停用”，禁用上传/索引/问答按钮
  - 文档状态：
      - UPLOADED 显示“已上传，待处理”
      - PARSING 显示“解析中”
      - PARSED 显示“已解析，待切块”
      - CHUNKING 显示“切块中”
      - INDEXED 显示“已切块，可向量化/可索引”
      - FAILED 显示红色“处理失败”，直接暴露 errorMessage
      - DISABLED 显示灰色“已禁用”
  - 索引任务状态：
      - QUEUED 显示“排队中”
      - RUNNING + DOCUMENT_PROCESSING 显示“解析与切块中”
      - RUNNING + DOCUMENT_EMBEDDING 显示“向量写库中”
      - SUCCEEDED + COMPLETED 显示“索引完成”
      - FAILED 显示“索引失败，可重试”
  - chunk 向量状态：
      - PENDING 显示“待向量化”
      - EMBEDDING 显示“向量化中”
      - EMBEDDED 显示“已完成”
      - FAILED 显示“向量化失败”
  - 进度表达：
      - 文档主进度不用伪造百分比，优先显示阶段时间线
      - 向量阶段可基于 embeddedChunkCount / chunkCount 显示真实进度条
      - 若 chunkCount 未知，仅显示“处理中”而不展示百分比
  - 失败与重试：
      - 页面显式展示 errorMessage / retryCount / maxRetryCount / lastHeartbeatAt
      - 若 retryCount >= maxRetryCount，按钮文案改为“已达最大重试次数”
      - 对恢复任务 triggerSource=RECOVERY，在任务列表显示“系统恢复重试”
  - 轮询策略：
      - 对 QUEUED/RUNNING 任务每 3 秒轮询一次
      - 成功或失败后停止轮询
      - 页面失焦时降频到 10 秒，避免无意义请求
  - 空态提示：
      - qa/readiness.questionAnsweringReady=false 时，统一显示后端 nextStep
      - 没有文档、没有 chunk、没有历史时都给出下一步操作按钮

## 7. 技术选型建议

  - 推荐：React + TypeScript + Vite + React Router + TanStack Query + Zustand + Ant Design
  - 选择 React 的理由：
      - 这个项目后续会扩到多轮对话、评测、权限、配置台，React 在“数据工作台 + AI 交互页”组合场景里更容易做模块化拆
        分
      - TanStack Query 很适合当前后端的“分页 + 轮询 + 状态刷新 + 缓存失效”模式
      - Zustand 足够轻，能承载当前知识库上下文、全局筛选、向导进度，不需要过重状态框架
      - Ant Design 对企业后台型页面成熟，能快速落地表格、步骤条、抽屉、表单、状态标签，和“简洁直观、不追求花哨
        UI”的目标一致
      - 简历展示上，React 技术栈的通用识别度更高，便于体现工程能力
  - 不选 Vue 的主要原因：
      - 不是因为 Vue 不合适，而是这类“状态管理 + 轮询任务 + AI 工作流页”在 React 生态里现成方案更多，落地速度和展示
        度更高

## 8. 分阶段开发计划

  - Day 1
      - 初始化 React + TS 工程、路由、QueryClient、全局布局
      - 封装 request 层，统一处理 ApiResponse、错误码、requestId
      - 完成知识库列表页、创建知识库、知识库切换
  - Day 2
      - 完成知识库概览页，接入 qa/readiness
      - 完成文档上传页和上传成功后的自动索引触发
      - 完成文档列表页
  - Day 3
      - 完成文档详情/索引监控页
      - 实现任务轮询、阶段时间线、失败重试、chunk 预览
      - 到这里形成“创建知识库 -> 上传 -> 索引完成”的前半闭环
  - Day 4
      - 完成检索调试页，展示 TopK 结果、score、来源
      - 完成问答页，打通 ask，展示答案、检索结果、sources
      - 到这里形成最小可用闭环 MVP
  - Day 5
      - 完成问答历史页
      - 完成来源抽屉、复制问题、重新提问、从历史回看来源
      - 做空态、错误态、loading、禁用态统一处理
  - Day 6
      - 增加高级操作入口：process / reprocess / embed / disable
      - 增加系统健康页和开发调试入口
      - 优化移动端适配，适合演示和简历截图
  - Day 7
      - 统一视觉规范、状态文案、埋点日志
      - 补齐前端测试、接口 mock、README 截图说明
      - 产出简历展示版项目说明和录屏脚本

## 9. 后续扩展预留

  - 多轮对话：当前后端是单问单 session，前端聊天区设计时把消息列表和输入框分层，后续可切换成真正会话模式
  - 混合检索：检索结果区保留“召回策略标签”和“排序分值”位
  - 评测中心：后续可增加问答样本管理、命中率展示、坏例回放
  - 权限体系：知识库上下文提前放进 tenant/user/role 容器，避免未来大改路由结构
  - 知识库配置：为 chunk 参数、topK、模型配置预留“设置”页
  - 任务中心：当前只有文档维度任务列表，后续可扩展为知识库级任务面板
  - 文档管理：预留批量上传、批量重试、批量禁用入口
  - 可观测性：预留 requestId、任务 ID、模型名展示，方便排障

## 10. 推荐前端目录结构

  frontend/
  ├── public/
  ├── src/
  │   ├── app/
  │   │   ├── router.tsx
  │   │   ├── providers.tsx
  │   │   └── store.ts
  │   ├── api/
  │   │   ├── client.ts
  │   │   ├── knowledge-base.ts
  │   │   ├── document.ts
  │   │   ├── qa.ts
  │   │   └── health.ts
  │   ├── features/
  │   │   ├── knowledge-base/
  │   │   ├── document/
  │   │   ├── indexing/
  │   │   ├── retrieval/
  │   │   ├── qa/
  │   │   └── health/
  │   ├── pages/
  │   │   ├── dashboard/
  │   │   ├── knowledge-bases/
  │   │   ├── documents/
  │   │   ├── retrieval/
  │   │   ├── qa/
  │   │   └── history/
  │   ├── components/
  │   │   ├── app-shell/
  │   │   ├── status/
  │   │   ├── wizard/
  │   │   ├── tables/
  │   │   ├── cards/
  │   │   ├── feedback/
  │   │   └── source-viewer/
  │   ├── hooks/
  │   │   ├── use-current-kb.ts
  │   │   ├── use-polling-task.ts
  │   │   └── use-api-error.ts
  │   ├── types/
  │   │   ├── api.ts
  │   │   ├── knowledge-base.ts
  │   │   ├── document.ts
  │   │   └── qa.ts
  │   ├── utils/
  │   │   ├── status.ts
  │   │   ├── format.ts
  │   │   └── guards.ts
  │   ├── styles/
  │   │   ├── tokens.css
  │   │   └── global.css
  │   └── main.tsx
  └── vite.config.ts

## 11. 当前完成情况

- 已完成 React + TypeScript + Vite 基础工程、路由、全局 Provider、状态存储与统一 API Client。
- 已完成知识库列表、知识库概览、文档上传、文档列表、文档详情、检索调试、问答、历史、系统健康页。
- 已完成 `ApiResponse` 统一解包、错误提示、`requestId` 展示和常用轮询封装。
- 已完成知识库上下文切换、问答就绪态展示、异步索引任务轮询、chunk 预览、来源展示。
- 已完成前端构建校验，当前 `npm run build` 可成功产出生产包。
- 已完成与后端真实接口的一轮综合联调，覆盖健康、知识库、文档、同步处理、异步索引、检索、问答、历史等主链路。

## 12. 近期仍可补强的工作

- 补浏览器级 UI 自动化，覆盖“创建知识库 -> 上传 -> 索引 -> 问答”的点击链路。
- 对前端大包做拆分，优先把表格页、问答页、健康页做路由级代码分割。
- 把开发者模式和普通用户模式再分层，减少 `process / embed / disable` 这类高级操作的误触。
- 为 `qa/readiness`、索引任务、检索结果增加更明确的空态和异常态说明。
- 增加上传前校验、重复文档提示、知识库禁用态的页面级拦截。
- 为历史页增加来源回看、重新提问、跳回检索页的快捷操作。
- 补一页前端开发说明，包含路由结构、数据流、组件职责和常见调试入口。

## 13. 中长期可扩展方向

- 会话化问答：从当前单问单答记录扩展到多轮对话视图。
- 评测中心：对接问答样本集、检索命中率和回归用例。
- 知识库设置页：暴露 chunk 参数、默认 `topK`、模型信息和健康状态。
- 文档批量操作：批量上传、批量索引、批量重试、批量禁用。
- 任务中心：从文档维度扩展到知识库级全局任务面板。
- 可观测性：更系统地展示 `requestId`、任务 ID、模型名、耗时和失败阶段。

## Assumptions

  - 主用户流默认走 upload -> index -> qa；process / embed 仅作为高级调试入口。
  - 前端采用轮询而非实时推送，因为后端当前没有 SSE/WebSocket。
  - 历史页按“单问单答记录”设计，不先实现多轮会话树。
  - 暂不做登录鉴权、多人协作、文件删除、文档重新启用，因为后端当前未提供完整接口。
  - 页面文案优先使用后端真实字段和状态，不额外发明前端私有状态名，避免和服务端脱节。
