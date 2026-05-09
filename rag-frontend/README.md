# Frontend

前端工作台位于 `rag-frontend/`，基于 `React + TypeScript + Vite + Ant Design + TanStack Query + Zustand`。

## 目标

提供一个面向企业知识库 RAG 的向导式体验：

1. 创建知识库
2. 上传文档
3. 发起异步索引
4. 观察解析 / 切块 / 向量化状态
5. 检索调试
6. 问答与来源回溯
7. 查看问答记录

## 本地启动

先确保后端服务运行在 `http://127.0.0.1:8080`。后端工程现在位于 `../rag-backend`，然后执行：

```bash
cd rag-frontend
npm install
npm run dev
```

Vite 已配置 `/api` 反向代理到本地后端。
