---
name: module-delivery
description: Use when building a new business module in Hify end to end — data model and schema, then Entity/Mapper/DTO/Service/Controller, then frontend API wiring and full acceptance
---

# Hify 业务模块交付流程

## 概述

Hify 的一个业务模块（`hify-ai`、`hify-tool`、`hify-agent`…）从零到可用，走同一条五阶段流水线：

> **咨询 → 数据模型 → 后端分层 → 前端对接 → 完整验收**

**核心原则：每一步都有明确产出物，每一步都先验证通过再进下一步。** 不要把后端分层和前端对接混着做 —— 契约没稳定就接前端，等于把接口返工的成本翻倍。

## 什么时候用

- 新增一个业务模块（`hify-<模块>`）
- 给现有模块加一整块新能力（比如 Provider 管理 + 健康检查）

**不适用**：单点修补（改一个字段、加一个校验）—— 直接改 + 跑相关测试即可，不必走全流程。

---

## 阶段 0 · 咨询模式梳理

用 brainstorming 技能把三件事问清楚，**不要直接写代码**：

| 要厘清 | 产出 |
|---|---|
| 供应商/依赖选型 | 「为什么选它，替代方案是什么，代价是什么」 |
| 数据模型 | 表拆分、字段语义、唯一性、生命周期 |
| 边界问题 | 什么属于本模块、什么属于别的模块、依赖方向 |

**产出物**：一份规格草稿（三部分都要写清「决定了什么 / 为什么 / 替代方案」）。

⏸ **等待用户确认**：选型结论、数据模型、模块边界。**用户确认前不要建表、不要写代码。**

> 边界是最容易漏的一环。Hify 的模块依赖是单向的（能力层 → 编排层），反向依赖会成环 —— `hify-ai` 里出现任何组合逻辑（知识库注入、工具循环、记忆）就会和 `hify-knowledge` 成环。

---

## 阶段 1 · 数据模型 → schema.sql

**产出物**：

1. `hify-app/src/main/resources/db/schema.sql` 的建表语句
2. `hify-app/src/main/resources/db/migrations/<日期>-<主题>-additive.sql` 增量迁移脚本（给已有库用）

**验证方式**：

```bash
# 1. 实体与 DDL 必须一致 —— 用离线结构测试守住
mvn -pl hify-app -am test -Dtest=AiSchemaMappingTest

# 2. 已有库要执行迁移脚本，并在执行前后核对数据
mysql -uhify -phify hify < hify-app/src/main/resources/db/migrations/xxx.sql
```

⚠️ **改 `schema.sql` ≠ 改了数据库。** `schema.sql` 只是「新建空库」的脚本，运行中的库不会自动变。漏掉迁移脚本的症状是接口 500 + 日志里 `Unknown column 'xxx' in 'field list'`。

**迁移脚本的三条规矩**（照 `2026-10-31-provider-health-additive.sql` 的写法）：

- 手动执行，不由应用启动或 Flyway 自动跑
- 写明「无损边界」：不删表、不删列、不动数据
- 执行前备份（`mysqldump`，权限 `0600`，且确认被 git 忽略）；执行后核对行数与数据内容

⏸ **等待用户确认**：是否允许对运行中的数据库执行迁移。

---

## 阶段 2 · 后端按 MVC 分层拆解

**顺序固定，每层做完先验证再进下一层**：

| 层 | 产出物 | 验证方式 |
|---|---|---|
| Entity + Mapper | `internal/entity/*DO.java`、`internal/mapper/*Mapper.java` | `mvn -pl <模块> -am test`（映射测试：表名、列、主键、JSON TypeHandler、逻辑删除标记） |
| DTO | `api/*Req.java`、`api/*Resp.java` | `mvn -pl <模块> -am compile` + 校验注解单测 |
| Service | `internal/service/*ServiceImpl.java` | 单元测试（Mockito mock Mapper，断言分支与错误码） |
| Controller | `internal/controller/*Controller.java` | `mvn verify` + `curl` 打真实接口 |

**Mapper 阶段**：实体继承 `shared.mybatis.BaseEntity`（主键 + 审计 + 逻辑删除）；JSON 列用 `JacksonTypeHandler` 且实体要 `autoResultMap = true`；健康快照这类运行态表不继承 `BaseEntity`（无 public_id / created_by / deleted）。

**Service 阶段**：业务规则、状态流转、`@Transactional` 全在这一层。**事务方法里禁止调模型、MCP、工具这些慢能力** —— 它们会长时间占着数据库连接。

**Controller 阶段**：只做「绑定参数 → 调 service → 返回 Result」，方法体 3–8 行；不碰 Mapper、不开事务、不返回 DO。返回统一 `Result`，分页直接返回 `PageResult`（别套两层）。

```bash
curl -sS -i 'http://localhost:8080/api/v1/<资源>?<参数>'
# 期望 {"code":0,"message":"ok","data":...}
# 业务失败期望对应错误码，如 40400 / 40900，HTTP 状态取错误码前三位
```

---

## 阶段 3 · 前端对接

**产出物**：

1. `src/types/<模块>.ts` —— 与后端 DTO 字段名完全一致
2. `src/api/<模块>.ts` —— 每个后端接口一个函数，只用 `utils/request.ts` 的 `get/post/put/del`
3. 页面改造 —— 把 mock 数据源换成真实 API

**验证方式**：

```bash
cd hify-web && npm test && npm run type-check && npm run build
```

- 组件测试需要 DOM 环境：`vitest` 默认 `environment: 'node'`，测组件要先装 `@vue/test-utils` + `happy-dom`，并在测试文件顶部加 `// @vitest-environment happy-dom`
- mock 换真接口时，**先对齐分页语义**：后端若用游标分页（`cursor`/`size`，返回 `items`/`hasNext`/`nextCursor`），前端表格也要换成游标模式，不能硬套页码 + total

---

## 阶段 4 · 完整验收

**产出物**：验收记录（curl 结果 + 浏览器截图/快照）。

**验证方式**：

1. **后端 curl**：逐个接口打一遍，包括正常路径与错误路径（不存在、重复、非法参数）
2. **浏览器全流程**：建一个真实数据 → 列表能看到 → 编辑 → 删除；确认请求真的打到了后端（看网络面板），不是前端假数据
3. **窄屏与空态**：表格在 < 1200px 的表现、空列表、加载失败态
4. **清理**：验收用的测试数据删掉，数据库回到验收前的行数

⏸ **等待用户确认**：验收通过、可以提交。

---

## 注意事项（这些坑都踩过）

### 数据库

- **改 `schema.sql` 不会改运行中的库。** 症状是 500 + `Unknown column`。必须写并执行增量迁移。
- **逻辑删除 + 唯一键会互相打脸**：`uk_name` 覆盖软删行，删掉的记录名称不能复用，再建同名会冲突。要么接受这个语义并在报错时说清楚，要么改唯一键设计。
- **有子记录时不要级联删**：`ai_model` 还在就不能删 Provider，要在业务层拦。

### 接口契约

- **分页语义必须先对齐。** CLAUDE.md §3.3 要求游标分页（`LIMIT N+1`、不用 `COUNT(*)`、不提供页码跳转）。通用表格组件原来只有页码模式，硬换 API 函数会拿不到 `total`。
- **健康/运行态数据不要放进配置缓存。** 配置缓存（`provider-cache`）存的是配置；健康快照每次实时查，否则页面显示的是 30 分钟前的状态。
- **缓存对象不能被就地修改**：在缓存外组装新的响应对象返回，不要改缓存里那个实例。
- **校验失败要返回 400 + `PARAM_ERROR`**，请求体 JSON 损坏也是 400 不是 500。

### 凭据与密钥

- **JSON TypeHandler 只做 JSON 转换，不加密。** 密钥要单独用 AES-GCM 加密后存进 JSON；加密密钥从环境变量注入，缺失时**拒绝写入**，不能明文回退。
- **密钥不能回显给前端**，编辑表单留空表示「保留」，需要显式 `KEEP / REPLACE / CLEAR` 三态，不要用空值隐式表达「清空」。
- **协议差异不要写 if-else 分发**，用策略模式 + 工厂 —— 见 `provider-adapter` Skill。

### 前端与 Element Plus

- **模板属性值里不能出现 `//`**：`placeholder="https://..."` 会被 vue-tsc 当注释，报「找不到 xxx」这种跟代码无关的假错。网址放 `<script setup>` 常量再 `:placeholder="hint"` 绑定。
- **Element Plus 的隐藏列会渲染一份副本**：`<el-table-column v-if>` 被移除的列，其插槽内容会以空行 `{}` 再渲染一次（`visibility: hidden`）。脚本按选择器点按钮可能点中这份副本，真实用户点不到 —— 自动化验证要按可见性过滤。
- **协议字面量在后端 6 处、前端 3 处**，新增协议要全部登记，漏一处症状各不相同。

### 并发与线程

- **阻塞式上游调用不能占 Tomcat 线程**：提交到 `llmExecutor`；`asyncExecutor` 的 `CallerRunsPolicy` 会让调度线程自己跑任务，定时任务要用 `AbortPolicy`。
- **多副本下定时任务会重复执行**：用 Redis 分布式锁 + 分钟窗口幂等键去重。
- **连续失败计数 ≠ 熔断滑动窗口**：前者给控制台看，后者决定是否熔断，别混用。

### 验证纪律

- **每层验证通过再进下一层**，不要攒到最后一起编译 —— 报错堆在一起时定位成本翻倍。
- **不要把验收用的测试数据留在库里**；删完核对行数。
- **跑真实接口时确认前端真的打了后端**：看网络面板，别让 mock 数据制造「功能正常」的假象。

---

## 快速参考

```bash
# 后端
mvn -pl <模块> -am test        # 单模块
mvn verify                    # 全量 + 依赖环 + ArchUnit 边界
mvn -pl hify-app -am spring-boot:run

# 前端
cd hify-web && npm test && npm run type-check && npm run build

# 数据库
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw'
```

| 位置 | 内容 |
|---|---|
| `hify-app/src/main/resources/db/schema.sql` | 建表脚本（新建空库用） |
| `hify-app/src/main/resources/db/migrations/` | 增量迁移（手动执行） |
| `hify-app/src/test/java/com/hify/arch/ModuleBoundaryTest.java` | 模块边界断言（api 包不得暴露 DO） |
| `hify-shared/src/main/java/com/hify/shared/web/Result.java` | 统一响应体 |
| `hify-shared/src/main/java/com/hify/shared/mybatis/BaseEntity.java` | 业务实体基类 |
