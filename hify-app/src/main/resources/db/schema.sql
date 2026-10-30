-- Hify 建表脚本
--
-- 规范来源：CLAUDE.md §3.3（数据库规范）、§2.4（数据模型）
--           docs/superpowers/specs/2026-10-29-hify-database-spec.md（字段类型、索引清单、分区与验收清单）
--
-- 内容：MySQL 业务库 16 张表 + PostgreSQL 向量库 1 张表
--
-- 通用约定
--   引擎 InnoDB，字符集 utf8mb4 / utf8mb4_0900_ai_ci
--   主键 BIGINT UNSIGNED AUTO_INCREMENT
--   对外 ID 用 public_id CHAR(26)（ULID，Crockford Base32）
--   时间一律 DATETIME(3) 存 UTC，不用 TIMESTAMP
--   状态与类型用 VARCHAR(32) + 应用层常量，不用 ENUM
--   同模块关联建外键，跨模块只存 id 不建外键
--   外键不建级联删除（ON DELETE CASCADE），删子表由应用层显式处理
--   不用 FLOAT/DOUBLE，不用 SELECT *
--
-- 逻辑删除
--   实体表用 deleted TINYINT（0 未删除 / 1 已删除）。
--   事件流表（分区）不带该列——@TableLogic 会给每条 SQL 追加条件，破坏分区裁剪。
--   BaseDO 同样不带该列，需要软删的表在自己的 DO 上单独声明。
--
-- 事件流表
--   conv_message / conv_run / conv_message_citation / wf_run 四张表按月分区，
--   主键是 (id, created_at)，没有独立 public_id，没有外键。
--   分区从 2026-10 起铺 13 个月，末尾留 pmax 兜底——没有 pmax 超范围写入会直接失败。
--   分区滚动维护见 database-spec §3.3。
--
# 两处与 database-spec 的偏差（有意为之，理由见下）
#   1. 删掉 ai_model.idx_provider_id 与 ai_agent_version.idx_agent_id：
#      它们分别是 uk_provider_model / uk_agent_version 的前缀重复索引，
#      违反 database-spec §2.2，也会让 §7 验收清单的「无前缀冗余索引」不通过。
#   2. vec_chunk 的 kb_id / doc_id 用 BIGINT 而非 spec 里的 VARCHAR(64)：
#      要与 MySQL 侧的 kb_knowledge_base.id / kb_document.id 类型一致，
#      也符合 §3.3「ID 引用用 BIGINT，不用 VARCHAR」。
#
-- ai_agent 的多值索引怎么查
--   命中索引的写法是「值在左、JSON 数组在右」：
--     SELECT id FROM ai_agent WHERE 'tool_xxx' MEMBER OF (tool_ids->'$[*]');
--   实测（MySQL 8.4.13）：写成 tool_ids->'$[*]' MEMBER OF (JSON_QUOTE('tool_xxx'))
--   或 JSON_CONTAINS(tool_ids, JSON_QUOTE('tool_xxx')) 都是全表扫，
--   索引完全用不上。database-spec §2.3 的示例写法有误，已同步更正。
#
-- 执行
--   mysql -uhify -phify hify < hify-app/src/main/resources/db/schema.sql
-- 向量库在另一个数据库，DDL 在同目录的 schema-pg.sql（PostgreSQL 语法，
-- 混进本文件会让 mysql 客户端在最后一行报错，所以拆开）。

CREATE TABLE `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `username`     VARCHAR(64)  NOT NULL DEFAULT ''                    COMMENT '登录名，OIDC/LDAP 下仅作展示，不唯一',
  `external_id`  VARCHAR(128) NULL                                   COMMENT '外部身份标识（OIDC sub / LDAP dn），共享密码登录时为空',
  `nickname`     VARCHAR(64)  NOT NULL DEFAULT ''                    COMMENT '显示名',
  `email`        VARCHAR(128) NULL                                   COMMENT '邮箱',
  `password_hash` VARCHAR(255) NULL                                 COMMENT '口令哈希（仅共享密码登录需要），argon2/bcrypt',
  `is_admin`     TINYINT       NOT NULL DEFAULT 0                    COMMENT '是否管理员：0 否 / 1 是',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'active'              COMMENT '状态：active / disabled',
  `last_login_at` DATETIME(3)  NULL                                  COMMENT '最近登录时间(UTC)',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_external_id` (`external_id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户。共享密码 / OIDC / LDAP 三种登录方式共用这张表';

CREATE TABLE `api_key` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`        VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '用途备注',
  `key_hash`    CHAR(64)     NOT NULL                                COMMENT '密钥的 SHA-256 十六进制，不存明文',
  `prefix`      VARCHAR(16)  NOT NULL                                COMMENT '密钥前 8 位，用于界面识别',
  `user_id`     BIGINT UNSIGNED NOT NULL                              COMMENT '所属用户 user.id',
  `scopes`      JSON         NULL                                    COMMENT '允许的权限范围，如 ["agent:read"]',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'active'              COMMENT '状态：active / disabled / revoked',
  `last_used_at` DATETIME(3) NULL                                    COMMENT '最近使用时间(UTC)',
  `expire_at`   DATETIME(3)  NULL                                    COMMENT '过期时间(UTC)，NULL 表示不过期',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_key_hash` (`key_hash`),
  UNIQUE KEY `uk_prefix` (`prefix`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_expire_at` (`expire_at`),
  CONSTRAINT `fk_api_key_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='API Key。程序化调用用，保留调用者身份做审计';

CREATE TABLE `ai_model_provider` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`        VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '接入点名称',
  `type`        VARCHAR(32)  NOT NULL DEFAULT 'openai'               COMMENT '适配器类型：openai / anthropic / gemini / ollama',
  `base_url`    VARCHAR(512) NOT NULL DEFAULT ''                     COMMENT '端点地址，如 https://api.openai.com/v1',
  `api_key_encrypted` VARCHAR(1024) NULL                            COMMENT '加密后的凭据（AES-GCM，密文 base64）',
  `enabled`     TINYINT       NOT NULL DEFAULT 1                     COMMENT '是否启用：0 否 / 1 是',
  `max_connections` INT UNSIGNED NOT NULL DEFAULT 48                 COMMENT 'WebClient 连接池上限，云端 48、Ollama 6（受显存限制）',
  `timeout_config` JSON      NULL                                   COMMENT '超时覆盖：connect/ttfb/overall/streamIdle 毫秒',
  `retry_config`   JSON      NULL                                   COMMENT '重试覆盖：最大次数、退避基数、上限',
  `breaker_config` JSON      NULL                                   COMMENT '熔断覆盖：失败率阈值、慢调用阈值、窗口',
  `fallback_chain` JSON      NULL                                   COMMENT '降级链：备选接入点与模型的顺序',
  `extra_config`   JSON      NULL                                   COMMENT '其他透传参数',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_type_enabled` (`type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型接入点。一个端点 + 一套凭据 + 一组调用策略';

CREATE TABLE `ai_model` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `provider_id`   BIGINT UNSIGNED NOT NULL                            COMMENT '所属接入点 ai_model_provider.id',
  `model_name`    VARCHAR(128) NOT NULL DEFAULT ''                    COMMENT '模型名，调用时用的那个（如 gpt-4o-mini）',
  `display_name`  VARCHAR(128) NOT NULL DEFAULT ''                    COMMENT '展示名',
  `model_type`    VARCHAR(32)  NOT NULL DEFAULT 'chat'                COMMENT '类型：chat / embedding / rerank',
  `capabilities`  JSON         NULL                                  COMMENT '能力标记：tool_call / vision / stream',
  `context_window` INT UNSIGNED NULL                                 COMMENT '上下文窗口 token 数',
  `max_output_tokens` INT UNSIGNED NULL                              COMMENT '单次最大输出 token 数',
  `default_params` JSON        NULL                                  COMMENT '默认调用参数：temperature / top_p 等',
  `enabled`       TINYINT       NOT NULL DEFAULT 1                    COMMENT '是否启用：0 否 / 1 是',
  `sort_order`    INT UNSIGNED NOT NULL DEFAULT 0                    COMMENT '排序，小的在前',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_provider_model` (`provider_id`, `model_name`),
  CONSTRAINT `fk_ai_model_provider` FOREIGN KEY (`provider_id`) REFERENCES `ai_model_provider` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型。挂在接入点下，同名模型在不同账号下可用性可能不同，故与接入点分表';

CREATE TABLE `tool_definition` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `source_type` VARCHAR(32)  NOT NULL DEFAULT 'builtin'             COMMENT '来源：builtin / http / mcp',
  `tool_key`    VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '工具标识，同来源内唯一',
  `name`        VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '工具名',
  `description` TEXT         NULL                                   COMMENT '工具描述，给模型看的',
  `schema`      JSON         NULL                                   COMMENT '入参 JSON Schema',
  `config`      JSON         NULL                                   COMMENT '来源配置：http 的地址与请求头 / mcp 的 server 引用',
  `enabled`     TINYINT       NOT NULL DEFAULT 1                     COMMENT '是否启用：0 否 / 1 是',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_tool_key` (`source_type`, `tool_key`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工具定义。内置工具代码注册，自定义 HTTP 工具与 MCP 工具落库';

CREATE TABLE `mcp_server` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`         VARCHAR(128) NOT NULL DEFAULT ''                    COMMENT '名称',
  `transport`    VARCHAR(32)  NOT NULL DEFAULT 'http'                COMMENT '传输方式：stdio / http',
  `command`      TEXT         NULL                                   COMMENT 'stdio 启动命令',
  `args`         JSON         NULL                                   COMMENT 'stdio 命令参数',
  `env`          JSON         NULL                                   COMMENT 'stdio 环境变量',
  `url`          VARCHAR(512) NULL                                   COMMENT 'http 传输的服务地址',
  `headers`      JSON         NULL                                   COMMENT 'http 传输的请求头',
  `enabled`      TINYINT       NOT NULL DEFAULT 1                    COMMENT '是否启用：0 否 / 1 是',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'unknown'             COMMENT '连通状态：unknown / online / offline',
  `last_checked_at` DATETIME(3) NULL                                 COMMENT '最近一次探测时间(UTC)',
  `tool_cache`   JSON         NULL                                   COMMENT 'tools/list 的结果缓存',
  `tool_cache_at` DATETIME(3) NULL                                  COMMENT '缓存时间(UTC)',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MCP Server 配置。仅管理员可见可改';

CREATE TABLE `kb_knowledge_base` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`            VARCHAR(128) NOT NULL DEFAULT ''                 COMMENT '名称',
  `description`     VARCHAR(512) NOT NULL DEFAULT ''                 COMMENT '描述',
  `embedding_model` VARCHAR(128) NULL                                COMMENT '向量化用的模型标识',
  `embedding_dim`   INT UNSIGNED NULL                                COMMENT '向量维度，必须与 vec_chunk.embedding 的维度一致',
  `chunk_size`      INT UNSIGNED NOT NULL DEFAULT 500                COMMENT '分块大小（字符）',
  `chunk_overlap`   INT UNSIGNED NOT NULL DEFAULT 50                 COMMENT '分块重叠（字符）',
  `doc_count`       INT UNSIGNED NOT NULL DEFAULT 0                  COMMENT '文档数（冗余计数）',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'active'           COMMENT '状态：active / disabled',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='知识库。分段正文与向量在 PG 的 vec_chunk，这里只存元信息';

CREATE TABLE `kb_document` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `kb_id`        BIGINT UNSIGNED NOT NULL                            COMMENT '所属知识库 kb_knowledge_base.id',
  `name`         VARCHAR(255) NOT NULL DEFAULT ''                    COMMENT '文件名',
  `file_type`    VARCHAR(32)  NOT NULL DEFAULT 'txt'                 COMMENT '文件类型，一期只支持 txt',
  `file_size`    BIGINT UNSIGNED NOT NULL DEFAULT 0                  COMMENT '字节数',
  `content_hash` CHAR(64)     NULL                                   COMMENT '内容 SHA-256，用于去重',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'uploaded'            COMMENT '状态机：uploaded / indexing / ready / failed / deleting',
  `chunk_count`  INT UNSIGNED NOT NULL DEFAULT 0                    COMMENT '分段数',
  `error_message` VARCHAR(1024) NULL                                 COMMENT '失败原因',
  `indexed_at`   DATETIME(3)  NULL                                   COMMENT '索引完成时间(UTC)',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  KEY `idx_kb_status` (`kb_id`, `status`),
  KEY `idx_kb_created` (`kb_id`, `created_at`),
  CONSTRAINT `fk_kb_document_kb` FOREIGN KEY (`kb_id`) REFERENCES `kb_knowledge_base` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='知识库文档。删除顺序固定：MySQL 软删 → 异步删 PG → MySQL 硬删';

CREATE TABLE `ai_agent` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`            VARCHAR(128) NOT NULL DEFAULT ''                 COMMENT '名称',
  `description`     VARCHAR(512) NOT NULL DEFAULT ''                 COMMENT '描述',
  `model_id`        BIGINT UNSIGNED NOT NULL                          COMMENT '主模型 ai_model.id（跨模块，只存 id）',
  `prompt_template` MEDIUMTEXT   NULL                                COMMENT '系统提示词',
  `tool_ids`        JSON         NOT NULL DEFAULT (JSON_ARRAY())     COMMENT '绑定的工具 ID 数组，多值索引',
  `knowledge_ids`   JSON         NOT NULL DEFAULT (JSON_ARRAY())     COMMENT '绑定的知识库 ID 数组，多值索引',
  `param_config`    JSON         NULL                                COMMENT '参数区：temperature / max_tokens 等',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'draft'             COMMENT '状态：draft / published / disabled',
  `app_type`        VARCHAR(32)  NOT NULL DEFAULT 'chat'              COMMENT '形态：一期只做 Chatflow 语义',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tool_ids` ((CAST(`tool_ids`->'$[*]' AS CHAR(64) ARRAY))),
  KEY `idx_knowledge_ids` ((CAST(`knowledge_ids`->'$[*]' AS CHAR(64) ARRAY)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 配置。保存即生效，一期不做发布快照';

CREATE TABLE `ai_agent_version` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `agent_id`     BIGINT UNSIGNED NOT NULL                            COMMENT '所属 Agent ai_agent.id',
  `version`      INT UNSIGNED NOT NULL DEFAULT 1                    COMMENT '版本号，从 1 递增',
  `snapshot`     JSON         NOT NULL                               COMMENT '发布时的完整配置快照',
  `published_at` DATETIME(3)  NULL                                   COMMENT '发布时间(UTC)',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  CONSTRAINT `fk_ai_agent_version_agent` FOREIGN KEY (`agent_id`) REFERENCES `ai_agent` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 发布快照。一期可不做（配置保存即生效），真需要时是纯新增表';

CREATE TABLE `wf_definition` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `name`        VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '名称',
  `description` VARCHAR(512) NOT NULL DEFAULT ''                     COMMENT '描述',
  `definition`  JSON         NOT NULL                                COMMENT '工作流 JSON：节点与连线',
  `version`     INT UNSIGNED NOT NULL DEFAULT 1                      COMMENT '版本号',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'draft'                COMMENT '状态：draft / published / disabled',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工作流定义。JSON 配置，一期没有可视化画布';

CREATE TABLE `conv_session` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `public_id`  CHAR(26)        NOT NULL                             COMMENT '对外 ID，ULID(Crockford Base32)',
  `user_id`         BIGINT UNSIGNED NOT NULL                         COMMENT '发起人 user.id',
  `agent_id`        BIGINT UNSIGNED NULL                             COMMENT '使用的 Agent ai_agent.id（跨模块，只存 id）',
  `api_key_id`      BIGINT UNSIGNED NULL                             COMMENT 'API 调用来源 api_key.id（跨模块，只存 id）',
  `title`           VARCHAR(255) NOT NULL DEFAULT ''                 COMMENT '会话标题',
  `message_count`   INT UNSIGNED NOT NULL DEFAULT 0                  COMMENT '消息数（冗余计数）',
  `last_message_at` DATETIME(3)  NULL                                COMMENT '最后一条消息时间(UTC)',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'active'           COMMENT '状态：active / archived',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_id` (`public_id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_agent_id` (`agent_id`),
  CONSTRAINT `fk_conv_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会话。有对外分享链接故需 public_id 全局唯一，因此不分区';

CREATE TABLE `conv_message` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `session_id`    BIGINT UNSIGNED NOT NULL                           COMMENT '所属会话 conv_session.id（逻辑引用，分区表不建外键）',
  `role`          VARCHAR(32)  NOT NULL DEFAULT 'user'              COMMENT '角色：user / assistant / system / tool',
  `content`       MEDIUMTEXT   NOT NULL                              COMMENT '消息正文',
  `content_type`  VARCHAR(32)  NOT NULL DEFAULT 'text'             COMMENT '正文类型：text / markdown / json',
  `model_id`      BIGINT UNSIGNED NULL                               COMMENT '生成该消息的模型 ai_model.id（逻辑引用）',
  `provider_id`   BIGINT UNSIGNED NULL                               COMMENT '生成该消息的接入点 ai_model_provider.id（逻辑引用）',
  `token_count`   INT UNSIGNED NOT NULL DEFAULT 0                   COMMENT '本条消息的 token 数',
  `finish_reason` VARCHAR(32)  NULL                                  COMMENT '结束原因：stop / length / tool_calls / error',
  `latency_ms`    INT UNSIGNED NULL                                  COMMENT '生成耗时（毫秒）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_session_seq` (`session_id`, `created_at`, `id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话消息。增长快，从建表起就按月分区'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION `p202610` VALUES LESS THAN ('2026-11-01'),
  PARTITION `p202611` VALUES LESS THAN ('2026-12-01'),
  PARTITION `p202612` VALUES LESS THAN ('2027-01-01'),
  PARTITION `p202701` VALUES LESS THAN ('2027-02-01'),
  PARTITION `p202702` VALUES LESS THAN ('2027-03-01'),
  PARTITION `p202703` VALUES LESS THAN ('2027-04-01'),
  PARTITION `p202704` VALUES LESS THAN ('2027-05-01'),
  PARTITION `p202705` VALUES LESS THAN ('2027-06-01'),
  PARTITION `p202706` VALUES LESS THAN ('2027-07-01'),
  PARTITION `p202707` VALUES LESS THAN ('2027-08-01'),
  PARTITION `p202708` VALUES LESS THAN ('2027-09-01'),
  PARTITION `p202709` VALUES LESS THAN ('2027-10-01'),
  PARTITION `p202710` VALUES LESS THAN ('2027-11-01'),
  PARTITION `pmax`    VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE `conv_run` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `session_id`       BIGINT UNSIGNED NOT NULL                        COMMENT '所属会话 conv_session.id（逻辑引用）',
  `agent_id`         BIGINT UNSIGNED NULL                            COMMENT '使用的 Agent ai_agent.id（逻辑引用）',
  `model_id`         BIGINT UNSIGNED NULL                            COMMENT '使用的模型 ai_model.id（逻辑引用）',
  `provider_id`      BIGINT UNSIGNED NULL                            COMMENT '使用的接入点 ai_model_provider.id（逻辑引用）',
  `status`           VARCHAR(32)  NOT NULL DEFAULT 'running'         COMMENT '状态：running / succeeded / failed',
  `input_text`       MEDIUMTEXT   NULL                               COMMENT '本轮输入',
  `output_text`      MEDIUMTEXT   NULL                               COMMENT '本轮输出',
  `tool_call_count`  INT UNSIGNED NOT NULL DEFAULT 0                 COMMENT '工具调用次数',
  `prompt_tokens`    INT UNSIGNED NOT NULL DEFAULT 0                 COMMENT '输入 token',
  `completion_tokens` INT UNSIGNED NOT NULL DEFAULT 0                COMMENT '输出 token',
  `total_tokens`     INT UNSIGNED NOT NULL DEFAULT 0                 COMMENT '总 token',
  `error_code`       INT          NULL                               COMMENT '后端错误码',
  `error_message`    VARCHAR(1024) NULL                              COMMENT '失败原因',
  `latency_ms`       INT UNSIGNED NULL                               COMMENT '总耗时（毫秒）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_session_created` (`session_id`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='每轮对话的运行记录。一次问答一行'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION `p202610` VALUES LESS THAN ('2026-11-01'),
  PARTITION `p202611` VALUES LESS THAN ('2026-12-01'),
  PARTITION `p202612` VALUES LESS THAN ('2027-01-01'),
  PARTITION `p202701` VALUES LESS THAN ('2027-02-01'),
  PARTITION `p202702` VALUES LESS THAN ('2027-03-01'),
  PARTITION `p202703` VALUES LESS THAN ('2027-04-01'),
  PARTITION `p202704` VALUES LESS THAN ('2027-05-01'),
  PARTITION `p202705` VALUES LESS THAN ('2027-06-01'),
  PARTITION `p202706` VALUES LESS THAN ('2027-07-01'),
  PARTITION `p202707` VALUES LESS THAN ('2027-08-01'),
  PARTITION `p202708` VALUES LESS THAN ('2027-09-01'),
  PARTITION `p202709` VALUES LESS THAN ('2027-10-01'),
  PARTITION `p202710` VALUES LESS THAN ('2027-11-01'),
  PARTITION `pmax`    VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE `conv_message_citation` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `session_id`  BIGINT UNSIGNED NOT NULL                             COMMENT '所属会话 conv_session.id（逻辑引用）',
  `message_id`  BIGINT UNSIGNED NOT NULL                             COMMENT '所属消息 conv_message.id（逻辑引用）',
  `chunk_id`    BIGINT UNSIGNED NOT NULL                             COMMENT '引用的分段 vec_chunk.id（跨库逻辑引用）',
  `doc_id`      BIGINT UNSIGNED NOT NULL                             COMMENT '冗余：文档 kb_document.id，chunk 重建后仍能定位',
  `chunk_index` INT UNSIGNED NOT NULL DEFAULT 0                     COMMENT '冗余：分段序号',
  `kb_id`       BIGINT UNSIGNED NOT NULL                             COMMENT '冗余：所属知识库 kb_knowledge_base.id',
  `quote_text`  TEXT         NOT NULL                                COMMENT '引用文本快照，不依赖 PG 是否还在',
  `score`       DECIMAL(10,6) NULL                                   COMMENT '相似度得分',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_chunk_id` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='消息引用。引用文本快照存本地，知识库重建索引后历史溯源仍可读'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION `p202610` VALUES LESS THAN ('2026-11-01'),
  PARTITION `p202611` VALUES LESS THAN ('2026-12-01'),
  PARTITION `p202612` VALUES LESS THAN ('2027-01-01'),
  PARTITION `p202701` VALUES LESS THAN ('2027-02-01'),
  PARTITION `p202702` VALUES LESS THAN ('2027-03-01'),
  PARTITION `p202703` VALUES LESS THAN ('2027-04-01'),
  PARTITION `p202704` VALUES LESS THAN ('2027-05-01'),
  PARTITION `p202705` VALUES LESS THAN ('2027-06-01'),
  PARTITION `p202706` VALUES LESS THAN ('2027-07-01'),
  PARTITION `p202707` VALUES LESS THAN ('2027-08-01'),
  PARTITION `p202708` VALUES LESS THAN ('2027-09-01'),
  PARTITION `p202709` VALUES LESS THAN ('2027-10-01'),
  PARTITION `p202710` VALUES LESS THAN ('2027-11-01'),
  PARTITION `pmax`    VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE `wf_run` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `definition_id` BIGINT UNSIGNED NOT NULL                           COMMENT '工作流 wf_definition.id（逻辑引用）',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'running'            COMMENT '状态：running / succeeded / failed',
  `input`         JSON         NULL                                 COMMENT '入参',
  `output`        JSON         NULL                                 COMMENT '出参',
  `step_count`    INT UNSIGNED NOT NULL DEFAULT 0                   COMMENT '执行节点数',
  `error_message` VARCHAR(1024) NULL                                 COMMENT '失败原因',
  `latency_ms`    INT UNSIGNED NULL                                  COMMENT '总耗时（毫秒）',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_definition_created` (`definition_id`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工作流运行记录'
PARTITION BY RANGE COLUMNS(`created_at`) (
  PARTITION `p202610` VALUES LESS THAN ('2026-11-01'),
  PARTITION `p202611` VALUES LESS THAN ('2026-12-01'),
  PARTITION `p202612` VALUES LESS THAN ('2027-01-01'),
  PARTITION `p202701` VALUES LESS THAN ('2027-02-01'),
  PARTITION `p202702` VALUES LESS THAN ('2027-03-01'),
  PARTITION `p202703` VALUES LESS THAN ('2027-04-01'),
  PARTITION `p202704` VALUES LESS THAN ('2027-05-01'),
  PARTITION `p202705` VALUES LESS THAN ('2027-06-01'),
  PARTITION `p202706` VALUES LESS THAN ('2027-07-01'),
  PARTITION `p202707` VALUES LESS THAN ('2027-08-01'),
  PARTITION `p202708` VALUES LESS THAN ('2027-09-01'),
  PARTITION `p202709` VALUES LESS THAN ('2027-10-01'),
  PARTITION `p202710` VALUES LESS THAN ('2027-11-01'),
  PARTITION `pmax`    VALUES LESS THAN (MAXVALUE)
);

-- ══ 演示表 ══════════════════════════════════════════════════════════
-- CRUD 演示用表（hify-demo 模块）。演示结束删掉整个 hify-demo 目录时，
-- 这张表也一并删掉即可。它没有 public_id——演示接口直接用自增 id。
CREATE TABLE `demo_item` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT              COMMENT '主键',
  `name`       VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT '名称',
  `status`     INT          NOT NULL DEFAULT 0                     COMMENT '状态：0 草稿 / 1 启用 / 2 停用',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  `created_by` BIGINT UNSIGNED NULL                                 COMMENT '创建者 user.id',
  `deleted`    TINYINT         NOT NULL DEFAULT 0                  COMMENT '逻辑删除：0 未删除 / 1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='CRUD 演示条目。演示用表，不是业务表';

