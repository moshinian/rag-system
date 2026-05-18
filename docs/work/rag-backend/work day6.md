# Day 6：联调与验证

## 当前结论

按 `week1.md` 的原始拆分口径，Day 6 不是继续补工程骨架，也不是直接跳去做 embedding。

**Day 6 的核心任务是把已经完成的上传、解析、切块、入库链路做一次真实联调，并确认结果是否可作为下一阶段检索链路的稳定输入。**

这一天的重点不是“再加很多新能力”，而是：

1. 跑通上传 -> 处理 -> chunk 入库全链路
2. 用真实样本文档验证解析结果
3. 检查 chunk 内容和边界是否合理
4. 检查元数据和状态流转是否完整
5. 记录当前实现的边界、问题和下一步修正点

结合当前仓库真实进度，进入 Day 6 时，已经具备的基础比最初计划更完整：

1. `md / txt / pdf` 第一版解析都已接入
2. 第一版固定窗口切块已落地
3. `document_chunk` 已可写入 PostgreSQL
4. `indexing_task` 独立处理结果记录已落地
5. README 架构图已补齐

所以 Day 6 的目标应该非常聚焦：

**不是继续扩功能，而是给 Day 5 的主链路做一次扎实验收。**

## 本次实际验证记录

今天已经完成过一轮真实联调，使用的是本地 `day6-kb` 知识库。

实际验证样本：

1. `work/samples/day4-upload-sample.md`
2. `work/samples/day4-upload-sample.txt`
3. `work/samples/day4-upload-sample.pdf`

实际处理结果：

1. Markdown 文档处理成功，`status = INDEXED`，`chunkCount = 2`，`parser = markdown`
2. txt 文档处理成功，`status = INDEXED`，`chunkCount = 1`，`parser = plain-text`
3. PDF 文档处理成功，`status = INDEXED`，`chunkCount = 1`，`parser = pdfbox`

数据库实际核验结果：

1. 三篇文档都已进入 `INDEXED`
2. `document_chunk` 共写入 `4` 条记录
3. `indexing_task` 共写入 `3` 条成功记录
4. `indexing_task.status` 均为 `SUCCEEDED`

## 本次联调发现的问题

今天联调里发现过一个真实字段问题：

1. Markdown 通过 `curl -F` 上传时，`multipart content-type` 会被带成 `application/octet-stream`
2. 原来的媒体类型解析逻辑会直接信任这个值
3. 导致 `.md` 文件被错误记录成通用二进制类型，而不是 `text/markdown`

这个问题今天已经修掉，并完成了复验：

1. 修正后会把通用二进制媒体类型回退到扩展名判断
2. 新上传的 Markdown 变体验证结果已经回到 `mediaType = text/markdown`

所以 Day 6 当前不是只做了“主链路能跑”的验证，也顺手修掉了一个联调里暴露出来的真实字段质量问题。

## Day 6 要验证什么

严格按主链路看，今天至少要验证下面几件事。

### 1. 上传到处理的全链路

至少要从接口层完整走一次：

1. 创建知识库
2. 上传 Markdown / txt / PDF
3. 调用 `/process`
4. 查看 `document` 状态变化
5. 查看 `document_chunk` 落库结果
6. 查看 `indexing_task` 执行结果

如果这条链路不能稳定跑通，那么 Day 5 的代码还不能算真正验收完成。

### 2. chunk 质量是否基本合理

当前切块策略是第一版固定窗口，不要求最优，但至少要满足：

1. chunk 内容不是空的
2. chunk 顺序正确
3. chunk 边界没有明显错乱
4. overlap 生效，没有把语义切得过碎
5. 标题、section 信息保留基本可用

Day 6 要接受一个现实：

第一版切块不需要“聪明”，但必须“稳定、可解释、便于后续调参”。

### 3. 元数据是否完整

至少要确认这些信息已经可追踪：

1. chunk 来自哪个知识库
2. chunk 来自哪篇文档
3. chunk 序号是什么
4. parser 是谁
5. chunk 使用了什么切块策略
6. 原文偏移量是否已记录
7. 文档处理任务是否已单独记录成功或失败

如果这些信息不完整，后面接 embedding 和召回时会很被动。

### 4. 状态流转是否清楚

当前至少要观察：

```text
UPLOADED -> PARSING -> PARSED -> CHUNKING -> INDEXED
                           \-> FAILED
                 \-> FAILED
```

以及：

```text
indexing_task: RUNNING -> SUCCEEDED / FAILED
```

Day 6 的价值之一，就是确认这套状态在联调时是否真的能帮你定位问题，而不是只停留在代码枚举上。

## Day 6 建议验证样本

建议今天至少选三类文档做验证：

1. `work/samples/day4-upload-sample.md`
2. `work/samples/day4-upload-sample.txt`
3. `work/samples/day4-upload-sample.pdf`

如果有余力，再补两类边界输入：

1. 很短的文本
2. 标题层级很多或段落特别长的 Markdown

这样更容易暴露当前第一版解析和切块的边界。

## Day 6 验证重点

今天最应该盯住的不是“接口返回了成功”这件事，而是下面这些更细的检查点：

1. `pdf` 能否稳定抽出可读文本，而不是空内容或乱码
2. `md` 标题拆分是否符合预期
3. `txt` 是否会被切得过碎
4. `chunk_count` 是否与文档长度大致匹配
5. `metadata_json` 是否完整且可解释
6. `indexing_task` 是否在成功和失败场景都能留下独立记录
7. 重处理时旧 chunk 是否会先清理再重写

这些检查点，决定了 Day 6 是“看起来能跑”，还是“真的可以进入 Day 7 和第 2 周”。

结合今天的实际结果，当前已经确认：

1. `md / txt / pdf` 三类样本都能走完整处理链路
2. chunk 顺序、标题和基础元数据是可读的
3. `indexing_task` 独立记录已经真实落库
4. Markdown `mediaType` 问题已经在联调中被识别并修正

## Day 6 完成后的理想状态

如果 Day 6 做扎实，到结束时应该达到：

1. 三类样本文档都完成过实际联调
2. 上传、处理、chunk 查询链路都已跑通
3. `document_chunk` 内容和顺序已做过抽查
4. `indexing_task` 成功记录已可查询
5. 当前第一版解析与切块的缺点已经被明确记下来
6. 可以正式进入 Day 7 文档沉淀，或直接进入第 2 周 embedding 设计

当前离这个理想状态已经非常接近，主要剩余的是：

1. 补更系统的联调记录沉淀
2. 决定是否要继续补失败场景和重处理场景验证

## 当前建议

按日拆分口径，Day 6 最合理的执行顺序应该是：

1. 用样本文档跑通完整接口链路
2. 查 `document`、`document_chunk`、`indexing_task`
3. 记录 chunk 质量问题和状态问题
4. 修正必要字段或文档口径
5. 沉淀联调记录，给 Day 7 收尾

## Day 6 最终判断

**Day 6 是第 1 周从“代码已实现”走向“主链已验收”的一天。**

结合今天的真实验证结果，当前可以认为：

**Day 6 的主体工作已经完成，主链路已经具备进入 Day 7 或第 2 周 embedding 设计的条件。**
