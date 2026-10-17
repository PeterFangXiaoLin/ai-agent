# AI Space

基于 **Vue 3 + Vite + Axios + Tailwind CSS 4** 的 AI 应用工作台，包含应用广场、AI 恋爱大师、AI 超级智能体三个页面。

## 启动

需要 Node.js 20.19+ 或 22.12+。

```bash
npm install
npm run dev
```

浏览器访问 http://127.0.0.1:5173，后端默认运行在 http://localhost:8080。

```bash
npm test          # 流式解析测试
npm run build    # 生成 dist
npm run preview  # 本地预览生产构建
```

## 接口

| 应用 | GET 路径 | 参数 |
| --- | --- | --- |
| AI 恋爱大师 | `/api/ai/love_app/chat/sse` | `message`, `chatId` |
| AI 超级智能体 | `/api/ai/manus/chat` | `message` |

进入聊天页时自动生成 UUID 会话 ID。恋爱大师在同一会话中复用 `chatId`，新会话生成新 ID。超级智能体的 ID 用于本地记录归档；每次请求创建一个独立智能体，**不会自动继承之前消息的上下文**。

Manus 将执行过程与最终答复分开发送：执行时展开简短进度，答复出现后自动折叠进度；正文渲染整理后的 Markdown，成功生成的 PDF 显示下载卡片。旧版 `Step ...` 历史记录也可折叠查看。

| SSE 事件 | data 内容 | 展示方式 |
| --- | --- | --- |
| `progress` | `{ "id": "tool-1", "title": "检索相关资料", "status": "running" }` | 按 id 更新进度，状态为 running / completed / failed |
| `delta` | `{ "content": "答复片段" }` | 追加到答复正文 |
| `answer` | `{ "content": "完整 Markdown 答复" }` | 替换正文，避免与 delta 重复 |
| `artifact` | `{ "title": "计划.pdf", "url": "/ai/artifacts/pdf/plan.pdf" }` | 下载卡片，URL 相对于 API 基地址 |
| `done` | `[DONE]` | 任务正常结束 |
| `error` | 错误说明文本 | 错误提示与重试入口 |

工具原始 JSON 保留在后端模型上下文中。Manus 未收到 `done` 就断开连接时，会保留已收到的内容并显示未完成提示。

Axios 使用 Fetch adapter 和 `responseType: 'stream'` 读取 SSE。解析器支持 UTF-8 分片、LF/CRLF、跨网络块事件、多行 `data:`、心跳、错误事件和结束标记。主动停止、中断、HTTP 错误及 120 秒无数据均有对应提示。请求不会自动重发，以免重复执行智能体工具任务。

开发及 preview 模式通过 Vite 将 `/api` 转发到后端，无需额外配置开发跨域。自定义配置时，将 `.env.example` 复制为 `.env.local`：

```dotenv
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://localhost:8080
```

若设置 `VITE_API_BASE_URL=http://localhost:8080/api`，浏览器会直接跨域请求，后端须允许前端来源。生产部署 `dist` 时，Web 服务器也须配置 `/api` 反向代理并关闭 SSE 缓冲。例如 Nginx：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
}
```

项目使用 Hash 路由，静态服务器无需配置路由回退。

## 已实现

- 浅色工作台、响应式导航、应用分类筛选、快捷提问。
- 用户右侧 / AI 左侧聊天气泡、实时 SSE、折叠任务进度、最终答复和 PDF 下载卡片。
- Enter 发送、Shift+Enter 换行、中文输入法兼容、自适应输入框。
- 停止生成、失败重试、重新生成、复制回复、Markdown 导出。
- 净化后的 Markdown 渲染，防止 AI 输出 HTML 脚本注入。
- 本地历史记录、刷新恢复、历史搜索（Ctrl/Cmd+K）、删除确认。
- 阅读旧消息时保留滚动位置，可一键回到最新回复。
- 键盘可用的原生模态窗口、移动端导航、减少动态效果支持。

对话记录存于当前浏览器的 localStorage；发送消息会交由配置的 AI 后端处理。删除本地历史不会删除后端会话记忆。未实现登录或云端同步。

## 目录

```text
src/
  components/     图标、主页装饰图形、智能体进度
  data/apps.js    应用说明与快捷提问
  services/       Axios SSE 接口调用
  stores/         会话状态、生成生命周期、本地保存
  utils/          SSE 解析、智能体事件状态更新
  views/          应用广场与共用聊天页面
  App.vue         导航、搜索、帮助及弹窗
  style.css       Tailwind 入口与页面样式
tests/            SSE 解析边界测试
```
