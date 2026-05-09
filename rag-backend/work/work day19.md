# Day 19：切块参数对比实验

## 今日目标

今天不新增线上接口，重点做一件事：

**用同一批样本文档，对不同切块参数做可重复的对比实验。**

Day 18 已经把切块参数迁移到配置，Day 19 的价值就是把“可以调”推进到“知道该怎么调”。

## 为什么要做

如果没有实验记录，切块参数只能靠感觉调整。这样会带来几个问题：

1. chunk 太小，召回碎片化严重
2. chunk 太大，单块上下文变重，噪声会上升
3. overlap 过大时，重复信息会增多
4. README 和实际默认值缺少支撑理由

所以 Day 19 的目标不是直接追求最终最优参数，而是先建立一版**可重复、可比较、可记录**的实验方式。

## 今日完成

### 1. 新增实验测试

新增：

1. `ChunkingExperimentTest`
2. `work/samples/day19-chunking-sample.md`

实验测试会：

1. 解析同一批样本文档
2. 依次使用三组切块参数
3. 统计 chunk 数、平均长度、最短/最长 chunk、短 chunk 数、长 chunk 数
4. 输出一版实验报告

### 2. 对比参数组

本次实验使用三组参数：

1. `compact = maxChunkChars 480 / overlap 60 / minBreakSearchOffset 180`
2. `balanced = maxChunkChars 600 / overlap 80 / minBreakSearchOffset 240`
3. `wide = maxChunkChars 720 / overlap 120 / minBreakSearchOffset 300`

其中：

1. `balanced` 是当前默认值
2. `compact` 偏向更细粒度切块
3. `wide` 偏向更少 chunk 和更大单块上下文

### 3. 实验样本

本次实验使用样本：

1. `work/samples/day4-upload-sample.md`
2. `work/samples/day4-upload-sample.txt`
3. `work/samples/day4-upload-sample.pdf`
4. `work/samples/day19-chunking-sample.md`

前三个是已有基础样本，最后一个是本次新增的长 Markdown 样本，用来避免小文档下三组参数结果完全一样。

## 实验结果

### 分文档结果

| profile | sample | chunks | avg chars | min chars | max chars | short <120 | long >500 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| compact | markdown | 2 | 124.5 | 97 | 152 | 1 | 0 |
| compact | plain-text | 1 | 200.0 | 200 | 200 | 0 | 0 |
| compact | pdf | 1 | 99.0 | 99 | 99 | 1 | 0 |
| compact | long-markdown | 11 | 348.2 | 214 | 473 | 0 | 0 |
| balanced | markdown | 2 | 124.5 | 97 | 152 | 1 | 0 |
| balanced | plain-text | 1 | 200.0 | 200 | 200 | 0 | 0 |
| balanced | pdf | 1 | 99.0 | 99 | 99 | 1 | 0 |
| balanced | long-markdown | 10 | 387.0 | 203 | 572 | 0 | 4 |
| wide | markdown | 2 | 124.5 | 97 | 152 | 1 | 0 |
| wide | plain-text | 1 | 200.0 | 200 | 200 | 0 | 0 |
| wide | pdf | 1 | 99.0 | 99 | 99 | 1 | 0 |
| wide | long-markdown | 6 | 598.3 | 330 | 708 | 0 | 5 |

### 汇总结果

1. `compact` 总 chunk 数：`15`
2. `balanced` 总 chunk 数：`14`
3. `wide` 总 chunk 数：`10`

## 结果解读

这次实验得出的第一版结论是：

1. 对短样本文档来说，三组参数几乎没有差异，切块策略的效果主要在长文档上体现
2. `compact` 会显著增加长文档 chunk 数，但能把 chunk 长度控制得更稳，没有出现 `>500` 字符 chunk
3. `balanced` 比 `compact` 少 1 个 chunk，平均长度更高，但仍保持在一个相对可控的范围
4. `wide` 把长 Markdown 样本直接压缩到 `6` 个 chunk，单块长度明显变大，已经出现较多 `>500` 字符 chunk
5. 当前阶段如果优先考虑召回粒度和稳健性，`balanced` 仍然是更适合作为默认值的折中方案

## 今日结论

Day 19 结束后，当前已经具备：

1. 可以外置切块参数
2. 可以重复执行切块参数实验
3. 可以说明当前默认值为什么暂时保留为 `balanced`

这为 Day 20 的问答评测集提供了一个更清晰的前提：

**先固定一组默认切块参数，再观察问答效果，而不是把切块变量和问答变量混在一起。**

## 验证结果

今天完成的验证：

1. `mvn -q -Dtest=ChunkingExperimentTest test` 通过
2. 实验报告已输出，三组参数在长样本上已产生可区分结果

## 明日建议

Day 20 应该继续做：

1. 选 5 到 10 条问答评测问题
2. 给出每题预期关键词或预期来源文档
3. 固定当前默认切块参数后执行检索与问答
4. 记录命中率、来源稳定性和明显误答样例

## Week 3 节奏提醒

Week 3 还剩：

1. Day 20：问答评测集
2. Day 21：Week 3 验收与文档收口

Day 21 继续保留为收口日，不提前挤占。
