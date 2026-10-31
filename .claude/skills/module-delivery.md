# module-delivery（指针文件）

真正的 Skill 在 **`module-delivery/SKILL.md`** —— Claude Code 只识别
`<目录>/SKILL.md` 形式，`.claude/skills/` 下的散装 `.md` 文件不会被加载。

- 用途：把 Hify 一个业务模块的完整交付流程固化成
  **咨询 → 数据模型 → 后端分层 → 前端对接 → 完整验收**，
  每阶段写明产出物、验证方式与「等待用户确认」的决策点，并附上实际踩过的坑。
- 触发：新增一个业务模块，或给现有模块加一整块新能力。
- 相关：`provider-adapter` Skill（新增 LLM 供应商协议时的注册点清单）。
