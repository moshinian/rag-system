# RFC-0010 Chunk Inspector Workspace

- Status: Accepted
- Created: 2026-05-12
- Last Updated: 2026-05-12
- Owners: RAG Team

## Summary

本 RFC 记录文档详情页中 chunk 检视区域为什么从 `ChunkPreview` 演进为 `ChunkInspector`。核心结论是：当前 `Chunk 预览` 不应继续被理解为普通折叠阅读器，而应升级为 `ChunkInspector`，作为 Retrieval Workspace 的证据检视子视图。它的职责不是把很多 chunk 正文依次堆叠给用户阅读，而是让用户在稳定页面高度下，对单个 chunk 做高频、聚焦、可扩展的检视。

## Context

当前文档详情页已经承担了两类职责：

1. 观察单文档从上传到索引完成的处理过程。
2. 检视切块结果和向量化状态。

早期实现里，第二类需求用的是未受控 `Collapse`。这适合低密度内容阅读，但不适合高频 chunk 检视与 AI retrieval 调试场景，原因很直接：

1. `Collapse` 允许多项同时展开，页面会快速向下堆叠。
2. 长 chunk 正文直接内联后，页面高度失控。
3. 用户难以建立“当前到底在看哪一条证据”的稳定心智。
4. 后续若接入 `score`、`rerank`、`citation`、`trace` 等调试信息，折叠面板很快会失去可维护性。

因此，这个区域的本质已经不再是“预览几个 chunk”，而是在定义 Retrieval Workspace 中的证据检视方式。

## Decision

文档详情页中的 chunk 区域应采用 `ChunkInspector` 模式，而不是继续沿用多开折叠面板。

当前决策如下：

1. `ChunkInspector` 是 Retrieval Workspace 的证据检视子视图，而不是独立详情弹层。
2. 桌面端采用左侧摘要列表、右侧固定 Inspector 的双栏布局。
3. 任意时刻只聚焦一个 chunk。
4. 完整正文只在 Inspector 中展示，不再内联展开到列表区域。
5. 页面高度必须稳定，大量 chunk 也只能在局部滚动区里浏览。

这意味着 chunk 区域的交互模型将从：

1. “点开一个折叠项看正文”

演进为：

1. “在摘要列表里选择一条证据”
2. “在 Inspector 中查看完整上下文和调试信息”

## Historical Evolution

### Phase 1: 早期折叠预览

- 特征：文档详情页使用未受控 `Collapse`
- 优点：实现简单，能快速把 chunk 原文暴露出来
- 局限：
  - 支持多项同时展开
  - 页面高度随正文线性增长
  - 不适合高密度 chunk 检视

### Phase 2: 明确 Retrieval Workspace 语义

- 特征：前端和 RFC 讨论逐步收口为“证据检视子视图”
- 关键判断：
  - `Collapse` 更适合低密度内容阅读
  - chunk 检视更接近高频选择与聚焦式对照
  - 后续还要承载 `score / rerank / citation / trace`

### Phase 3: ChunkInspector 落地

- 特征：当前前端已采用 `ChunkInspector`
- 当前行为：
  - 桌面端双栏布局
  - 单选聚焦
  - 完整正文只在 Inspector 中展示
  - 移动端退化为 `Drawer`

## Implementation

前端实现应遵守以下结构：

1. 左侧摘要列表显示：
   - `Chunk #`
   - `title`
   - `embeddingStatus`
   - `offset`
   - `tokenCount`
   - 截断后的正文摘要
2. 右侧 Inspector 显示：
   - 当前选中的 chunk 基本信息
   - 完整正文
   - 可扩展的 metadata / retrieval debug 区域
3. 移动端可退化为 `Drawer`，但语义仍然是同一个 `ChunkInspector`，不是另一套交互模型。

当前落地实现位于：

1. [DocumentDetailPage](../../rag-frontend/src/pages/documents/detail.tsx)
2. [ChunkInspector](../../rag-frontend/src/components/cards/chunk-inspector.tsx)

当前实现边界还包括：

1. 选中态基于 `chunkIndex`，避免直接使用后端雪花 ID 做前端精确比较。
2. 列表项展示摘要，不在列表内联展开完整正文。
3. Inspector 预留 `Future Retrieval Signals` 区域，为后续字段扩展留位置。

## Future Evolution

`ChunkInspector` 当前聚焦于切块结果检视。

未来若接入：

1. `retrieval trace`
2. `citation mapping`
3. `rerank pipeline`
4. `evidence relationship`
5. `prompt grounding`

则可逐步演进为更完整的 AI Retrieval Workspace，而不推翻当前 Inspector 架构。

这也是本 RFC 的长期定位：它不只是在描述一处 Chunk UI 调整，而是在定义文档证据检视如何演进为 AI Retrieval Workspace。

## Consequences

正面影响：

1. 页面高度更稳定，不再因为展开长正文而无限拉长。
2. 用户可以明确知道当前正在检视哪一条证据。
3. 为后续检索调试信息接入保留了稳定容器。

代价与约束：

1. 前端组件需要从简单 `Collapse` 重构为状态化 Inspector。
2. 需要为桌面端和移动端分别定义一致语义下的不同承载方式。
3. 这次只解决证据检视结构，不引入新的检索计算逻辑。

## Validation

当前已有直接验证材料：

1. 文档详情页已切换为 `ChunkInspector` 实现，不再使用旧的 `ChunkPreviewList`。
2. 前端构建已通过，说明新的双栏 / `Drawer` 结构与当前类型系统兼容。
3. 已修正一次真实线上暴露的问题：后端雪花 ID 作为 JSON number 返回时会超过 JavaScript 安全整数范围，因此 Inspector 当前已改用 `chunkIndex` 做选中态和列表键，避免错选和多选异常。

## Non-Goals

本 RFC 不定义：

1. chunk 服务端搜索或过滤接口。
2. citation trace 的后端计算逻辑。
3. rerank pipeline 的实际排序算法。
4. Retrieval Workspace 的全局编排，只定义其中的证据检视子视图。

## Open Questions

1. 后续是否需要在 Inspector 左侧列表中增加轻量过滤或检索信号排序，而不是始终按 `chunkIndex` 顺序浏览。
2. 当 `retrieval trace / citation mapping` 真正接入后，Inspector 是否仍保持“单证据主视图”，还是需要引入关联证据联动区。
3. 如果未来文档详情页继续扩展为更完整的调试工作台，`ChunkInspector` 是否应该继续作为子视图存在，还是上升为更高层容器的一部分。

## References

1. [README.md](../../README.md)
2. [RFC Index](./README.md)
3. [plan.md](../work/rag-frontend/plan.md)
4. [DocumentDetailPage](../../rag-frontend/src/pages/documents/detail.tsx)
5. [ChunkInspector](../../rag-frontend/src/components/cards/chunk-inspector.tsx)
