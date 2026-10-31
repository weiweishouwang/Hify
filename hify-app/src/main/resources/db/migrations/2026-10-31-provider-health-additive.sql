-- Hify 本地开发库：Provider / Model / ProviderHealth 增量迁移
-- 日期：2026-10-31
--
-- 手动执行脚本，不由应用启动或 Flyway 自动运行。
-- 已检查的目标：本地 hify-mysql，MySQL 8.4.13，数据库 hify。
-- 执行前 Provider、Model 均为空，健康表不存在；整个数据库已做备份。
--
-- 无损边界：
--   1. 只 ALTER 补列/调整模型标识排序规则，并 CREATE 缺失的健康表。
--   2. 不删除任何已有表、列或记录；旧凭据、超时、重试等配置列全部保留。
--   3. 旧 api_key_encrypted 的密文格式不能盲目转换为新 auth_config 封套。
--      其他数据库若有旧凭据记录，必须先审查并单独迁移凭据，不能直接套用。
--   4. MySQL DDL 隐式提交，不能依赖 ROLLBACK 撤销；执行前必须备份。
--   5. 此脚本针对已检查的旧结构，仅执行一次，重跑前检查列是否已存在。

-- 等待元数据锁最多10秒，避免被长事务无限阻塞。
SET SESSION lock_wait_timeout = 10;

ALTER TABLE `ai_model_provider`
  ADD COLUMN `auth_config` JSON NOT NULL DEFAULT (JSON_OBJECT())
    COMMENT '按协议存储鉴权配置；敏感值由业务层 AES-GCM 加密后写入，禁止明文密钥'
    AFTER `base_url`,
  ADD COLUMN `extra_params` JSON NULL
    COMMENT '接入点级上游扩展参数，不存放凭据'
    AFTER `auth_config`,
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '连接配置版本，由业务层递增，识别旧配置的健康观测'
    AFTER `enabled`;

ALTER TABLE `ai_model`
  ADD COLUMN `embedding_dimension` INT UNSIGNED NULL
    COMMENT '向量维度，非 embedding 模型或未知时为 NULL'
    AFTER `max_output_tokens`,
  ADD COLUMN `extra_params` JSON NULL
    COMMENT '模型级上游扩展参数，不存放凭据'
    AFTER `default_params`,
  ADD COLUMN `config_version` BIGINT UNSIGNED NOT NULL DEFAULT 1
    COMMENT '影响调用的模型配置版本，由业务层递增'
    AFTER `sort_order`,
  MODIFY COLUMN `model_name` VARCHAR(128) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT ''
    COMMENT '上游模型标识，区分大小写，原样发送';

CREATE TABLE `ai_model_provider_health` (
  `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '接入点主键，与配置表一对一，不生成独立自增 ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '可用性：UNKNOWN / UP / DOWN，与启停和熔断分开',
  `fail_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连续失败次数，成功清零，不等同于熔断滑动窗口计数',
  `latency_ms` BIGINT UNSIGNED NULL COMMENT '最近一次观测总耗时(毫秒)，不是首字耗时；未观测时为 NULL',
  `last_success_at` DATETIME(3) NULL COMMENT '最近成功时间(UTC)',
  `last_failure_at` DATETIME(3) NULL COMMENT '最近失败时间(UTC)',
  `error_code` VARCHAR(64) NULL COMMENT '规范化失败原因码，无当前错误时为 NULL',
  `error_message` VARCHAR(512) NULL COMMENT '脱敏错误摘要，不保存上游原始响应体，无当前错误时为 NULL',
  `last_observed_at` DATETIME(3) NULL COMMENT '最近有效观测的开始时间(UTC)，用于阻止乱序覆盖，未观测时为 NULL',
  `observation_source` VARCHAR(32) NULL COMMENT '观测来源：probe / invocation',
  `observed_model_id` BIGINT UNSIGNED NULL COMMENT '观测实际使用的模型 ai_model.id',
  `provider_config_version` BIGINT UNSIGNED NULL COMMENT '观测使用的接入点配置版本，未观测时为 NULL',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
    COMMENT '健康快照更新时间(UTC)，不更新配置表',
  PRIMARY KEY (`provider_id`),
  CONSTRAINT `fk_ai_provider_health_provider` FOREIGN KEY (`provider_id`) REFERENCES `ai_model_provider` (`id`),
  CONSTRAINT `fk_ai_provider_health_model` FOREIGN KEY (`observed_model_id`) REFERENCES `ai_model` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型接入点最近健康观测。不缓存，无逻辑删除、public_id 或创建者字段';

-- 为已有未删除接入点初始化 UNKNOWN；本次已检查的开发库中接入点数量为0。
INSERT INTO `ai_model_provider_health` (`provider_id`, `status`, `fail_count`)
SELECT p.`id`, 'UNKNOWN', 0
FROM `ai_model_provider` p
WHERE p.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ai_model_provider_health` h WHERE h.`provider_id` = p.`id`);
