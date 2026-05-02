# Embedding Service

本目录提供本地 `bge-small-zh-v1.5` embedding 服务，默认通过 HTTP 暴露 OpenAI-compatible 的 `/v1/embeddings` 接口。

## 默认行为

- 模型：`BAAI/bge-small-zh-v1.5`
- 端口：`8001`
- 设备：`cpu`
- 向量归一化：开启
- 支持通过 `EMBEDDING_MODEL_PATH` 指向本地模型目录
- 支持通过 `EMBEDDING_LOCAL_FILES_ONLY` 控制是否只从本地读取模型

## 启动方式

使用仓库根目录的 `docker-compose.yml`：

```bash
docker compose up -d embedding-service
```

如果运行环境无法访问 Hugging Face，需要提前把模型放到本地目录，再通过环境变量指定，例如：

```yaml
environment:
  EMBEDDING_MODEL_PATH: /models/bge-small-zh-v1.5
  EMBEDDING_LOCAL_FILES_ONLY: "true"
```

当前 `docker-compose.yml` 已经把仓库内的 `data/models` 挂载到容器的 `/models`。

## 健康检查

```bash
curl --noproxy '*' -s http://127.0.0.1:8001/health
```

在模型尚未下载成功时，`/health` 也会返回服务状态，并通过 `modelLoaded`、`modelError` 告诉你模型当前是否就绪。

## 请求示例

```bash
curl --noproxy '*' -s http://127.0.0.1:8001/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "bge-small-zh-v1.5",
    "input": "结算复核职责是什么？"
  }'
```

也支持批量输入：

```json
{
  "model": "bge-small-zh-v1.5",
  "input": [
    "第一段文本",
    "第二段文本"
  ]
}
```

## 返回结构

返回体会兼容 OpenAI embeddings 的最小字段集合，Java 服务当前只依赖：

- `data[].embedding`

## 说明

- 首次启动会下载 Hugging Face 模型，速度取决于网络环境。
- 如果当前环境无法访问外网，必须提前准备本地模型目录或预热 `data/huggingface` 缓存。
- 当前仓库的 `docker-compose.yml` 默认把 `EMBEDDING_LOCAL_FILES_ONLY` 设为 `true`，适合离线环境；如果你本机能联网并希望自动下载模型，可以改成 `false`。
- 模型缓存目录映射到仓库的 `data/huggingface`。
- 如果你的机器具备可用 GPU，可以把 `EMBEDDING_DEVICE` 改成对应设备值。
