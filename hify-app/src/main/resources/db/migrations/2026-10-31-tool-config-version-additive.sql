-- Hify 本地开发库：tool_definition 增量迁移（配置版本 + 来源索引）
-- 日期：2026-10-31
--
-- 手动执行脚本，不由应用启动或 Flyway 自动运行。
-- 已检查的目标：本地 hify-mysql，MySQL 8.4.13，数据库 hify。
-- 背景：2026-10-31-hify-tool-module-design.md 决定 tool_definition 采用「统一目录」，
--        三种来源（builtin / http / mcp）都落库，工具启用状态与配置版本都要能查能改。
--
-- 无损边界：
--   1. 只 ALTER 补一列、补一个索引，并把保留字列改名；不删除任何已有表、列或记录。
--   2. 不改任何已有行的取值——config_version 由默认值 1 兜底，
--      已有行的 config_version 一律为 1，与「首次落库」等价。
--   3. 不动 uk_tool_key / uk_public_id；工具名唯一性由服务层校验，不加数据库唯一键
--      （内置与 MCP 的 name 由代码 / 上游决定，加唯一键会与目录同步的 upsert 打架）。
--   4. `schema` 是 MySQL 8 的保留字：MyBatis-Plus 生成的 SQL 不加反引号，
--      保留原名会直接语法错误。改名为 input_schema，RENAME COLUMN 保留列定义与数据。
--   5. MySQL DDL 隐式提交，不能依赖 ROLLBACK 撤销；执行前必须备份。
--   6. 此脚本针对已检查的旧结构，仅执行一次，重跑前检查列是否已存在。

-- 等待元数据锁最多10秒，避免被长事务无限阻塞。
SET SESSION lock_wait_timeout = 10;

ALTER TABLE `tool_definition`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '工具配置版本，由业务层递增，用于乐观锁'
    AFTER `enabled`;

ALTER TABLE `tool_definition`
  RENAME COLUMN `schema` TO `input_schema`;

CREATE INDEX `idx_source_type_enabled` ON `tool_definition` (`source_type`, `enabled`);
