-- Hify 本地开发库：Agent 配置版本列 + 名称唯一键
-- 日期：2026-11-01
--
-- 手动执行脚本，不由应用启动或 Flyway 自动运行。
-- 已检查的目标：本地 hify-mysql，MySQL 8.4.13，数据库 hify。
-- 背景：
--   1. Agent 的模型绑定 / 工具绑定 / 知识库绑定都会被改，两个管理员同时改会互相覆盖 ——
--      用配置版本做乐观锁，命中 0 行即冲突（40900）。与 ai_model_provider /
--      ai_model / kb_knowledge_base / mcp_server 的做法一致。
--   2. ai_agent 是唯一还没有名称唯一键的具名配置实体（provider / model /
--      mcp_server / kb_knowledge_base 都有 uk_name）。名称在管理台是给人看的，
--      重名会让「我刚才建的那个」难找。
--
-- 无损边界：
--   1. 只 ALTER 补一列、一个唯一键；不删除任何已有表、列或记录。
--   2. 新列默认值 1，已有行的配置版本一律为 1，与「首次落库」等价。
--   3. 唯一键覆盖软删行 —— 删掉的 Agent 名称不能再用，报错要说清楚（含「包括已删除记录」）。
--   4. MySQL DDL 隐式提交，不能依赖 ROLLBACK 撤销；执行前必须备份。
--   5. 此脚本针对已检查的旧结构，仅执行一次，重跑前检查列与索引是否已存在。

-- 等待元数据锁最多10秒，避免被长事务无限阻塞。
SET SESSION lock_wait_timeout = 10;

-- 加唯一键前先确认没有重名；若有重复，此处应当停下来人工处理而不是让 ALTER 失败。
SELECT `name`, COUNT(*) AS cnt
  FROM `ai_agent`
 GROUP BY `name`
HAVING COUNT(*) > 1;

ALTER TABLE `ai_agent`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT 'Agent 配置版本，由业务层递增，用于乐观锁'
    AFTER `app_type`,
  ADD UNIQUE KEY `uk_name` (`name`);
