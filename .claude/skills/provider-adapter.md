# provider-adapter（指针文件）

真正的 Skill 在 **`provider-adapter/SKILL.md`** —— Claude Code 只识别
`<目录>/SKILL.md` 形式，`.claude/skills/` 下的散装 `.md` 文件不会被加载。

- 用途：把「新增一个 LLM 供应商」的标准流程固化成
  **分析 API → 实现 Adapter → 注册到 Factory → 验证**，
  并列出后端 6 处、前端 3 处必须登记的协议字面量。
- 触发：接入新的 LLM 厂商原生 API，或给现有协议加变体（Azure OpenAI 等）。
- 不适用：只是新增一个 OpenAI 兼容端点（DeepSeek、Qwen、vLLM…）—— 改 base_url 即可。

阅读前请先看 `provider-adapter/SKILL.md`。
