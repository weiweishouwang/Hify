-- Hify 本地开发库：知识库配置版本列
-- 日期：2026-11-01
--
-- 手动执行脚本，不由应用启动或 Flyway 自动运行。
-- 已检查的目标：本地 hify-mysql，MySQL 8.4.13，数据库 hify。
-- 背景：知识库的名称全局唯一，两个管理员同时改名会互相覆盖 ——
--        用配置版本做乐观锁，命中 0 行即冲突（40900）。与 ai_model_provider /
--        ai_model 的做法一致。
--
-- 无损边界：
--   1. 只 ALTER 补一列；不删除任何已有表、列或记录。
--   2. 新列默认值 1，已有行的配置版本一律为 1，与「首次落库」等价。
--   3. MySQL DDL 隐式提交，不能依赖 ROLLBACK 撤销；执行前必须备份。
--   4. 此脚本针对已检查的旧结构，仅执行一次，重跑前检查列是否已存在。

-- 等待元数据锁最多10秒，避免被长事务无限阻塞。
SET SESSION lock_wait_timeout = 10;

ALTER TABLE `kb_knowledge_base`
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '知识库配置版本，由业务层递增，用于乐观锁'
    AFTER `status`;
