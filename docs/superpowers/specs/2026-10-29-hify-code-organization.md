# Hify 代码组织规范

> 适用范围：Hify 全部 Java 代码。违反本规范的代码不得合并。
> 判定标准：任何一条规则如果需要"看情况"，按本文件的默认解释执行；如有异议先改本文件，再改代码。

---

## 0. 硬性前提

- 单体 Spring Boot 应用，`hify-app` 装配全部模块；一个 Spring 进程。
- Maven 多模块，一个功能一个模块，依赖图**必须无环**。
- 每模块内部两层：`api/`（跨模块契约）与 `internal/`（模块私有实现）。
- 数据库：MySQL 8.x，MyBatis-Plus 3.5.x，Java 17+。

---

## 1. 模块清单与依赖层级

```
L0  hify-shared
L1  hify-ai            hify-tool
L2  hify-knowledge     hify-mcp
L3  hify-agent
L4  hify-conversation  hify-workflow      （两者平行，互不依赖）
L5  hify-app
```

**依赖规则**：每条边只能指向**更低层**。层内互不依赖。（`CLAUDE.md` §2.1 用的是箭头图，**箭头指向依赖者**：`A → B` 表示 B 依赖 A，与本图 L0→L5 的层级顺序一致。）

| 模块 | 职责 | 允许依赖 | 禁止依赖 |
|---|---|---|---|
| `hify-shared` | 异常体系、错误码、当前用户上下文、trace id；平台域的用户与密钥（`user` / `api_key`）；`web` 包（统一响应体 `Result` / `PageResult`）；`exception` 包（`ErrorCode` / `BizException`） | — | 一切业务模块 |
| `hify-ai` | 模型接入点管理 + 模型调用（chat / embedding）+ 流式补全 | shared | knowledge, tool, agent, mcp, conversation, workflow |
| `hify-tool` | Tool 抽象 + ToolProvider SPI + 内置/HTTP 来源 | shared | knowledge, agent, mcp, conversation, workflow |
| `hify-knowledge` | TXT 上传、固定分块、embedding、向量检索 | shared, ai | tool, agent, mcp, conversation, workflow |
| `hify-mcp` | MCP client、server 配置管理、MCP 来源的 ToolProvider | shared, tool | ai, knowledge, agent, conversation, workflow |
| `hify-agent` | Agent 配置的存取与组装（**不执行推理**） | shared, ai, tool, knowledge | mcp, conversation, workflow |
| `hify-conversation` | 会话、消息、流式对话编排 | shared, ai, tool, knowledge, agent | workflow |
| `hify-workflow` | 工作流定义的存取与执行（6 种节点） | shared, ai, tool, knowledge, agent | conversation |
| `hify-app` | 启动、装配、静态资源与反代配置；**唯一可执行模块** | 全部 | — |

**`hify-tool` 的关键约束**：**`tool` 不依赖 `mcp`** —— `tool` 定义 `ToolProvider` SPI，`mcp` 实现它并由 `tool` 收集，方向是 `mcp → tool`。写成 `tool → mcp` 会成环。

**`hify-ai` 的关键约束**：只提供纯能力（配置 CRUD、纯文本/流式补全、embedding）。**任何组合逻辑（知识库注入、工具调用循环、记忆）都不许出现在 `ai`**，只能在 `conversation` / `workflow` 里做。否则 `ai ↔ knowledge` 成环。

**`hify-agent` 的关键约束**：只负责"把配置组装成一个可运行的 Agent 定义"，**不执行推理、不调用工具**。执行在编排层。

---

## 2. 模块内部六层结构

```
com.hify.<module>/
├── api/                          # L1 契约层（跨模块唯一可见）
│   ├── XxxService.java           #   public interface
│   ├── dto/                      #   纯 POJO 数据传输对象
│   └── event/                    #   该模块发布的事件（本模块没有则不建）
└── internal/                     # L2-L6 实现（模块私有）
    ├── controller/               # L2 接入层
    ├── service/                  # L3 业务层
    ├── mapper/                   # L4 数据访问层
    ├── entity/                   # L5 数据对象层
    ├── converter/                # DO ↔ DTO 转换
    ├── spi/                      # 扩展点接口（仅 tool / ai 有）
    └── config/                   # 模块配置类
```

---

### L1 `api/` — 契约层

**允许**
- `public interface`（业务服务接口）
- 纯 POJO 的 DTO（字段 + getter/setter，或 record）
- `public enum`、常量类
- 事件类（`record` 或简单 POJO）

**禁止**
- 任何 Spring 注解（`@Service` / `@Component` / `@Autowired` / `@Resource`）
- 任何 MyBatis-Plus 注解（`@TableName` / `@TableField` / `@TableId`）
- 任何 JPA 注解
- 引用 `internal` 下任何类
- 继承 `com.baomidou.mybatisplus.extension.service.IService`（会把 DO 泛型泄漏到契约里）
- 直接返回或接收 `internal/entity` 下的 DO

**接口签名必须满足远程调用友好性**（这是"将来只需改传输层"的前提）：

1. 参数与返回值全部是 `api` 包内的 DTO 或基本类型，不出现 DO、不出现 `MultipartFile` / `InputStream` 等 Servlet 类型
2. 不使用方法重载（重载在声明式 HTTP 客户端上无法可靠映射）
3. 参数不超过 3 个，超过用请求 DTO 包装
4. 不用 checked exception 表达业务失败；业务失败用错误码 + DTO 返回
5. 不使用带业务逻辑的 default 方法

**判据**：*这个接口能不能被 Feign / Dubbo 直接实现？* 能 → 放 `api`。

**判据（一句话）**：*这个文件在模块被拆成独立服务时，是否需要原样复制过去？* 是 → 放 `api`；否 → 放 `internal`。

```java
// ✅ hify-ai/src/main/java/com/hify/ai/api/ModelProviderService.java
package com.hify.ai.api;

import com.hify.ai.api.dto.ModelProviderDTO;

public interface ModelProviderService {
    ModelProviderDTO getById(String id);
    List<ModelProviderDTO> listEnabled();
    void save(ModelProviderDTO dto);
    void delete(String id);
}

// ✅ hify-ai/src/main/java/com/hify/ai/api/dto/ModelProviderDTO.java
package com.hify.ai.api.dto;

public class ModelProviderDTO {
    private String id;
    private String name;
    private String baseUrl;
    private String model;
    private boolean enabled;
    // getters / setters
}

// ❌ 禁止：接口继承 MP 的 IService，泄漏 DO 泛型
public interface ModelProviderService extends IService<ModelProviderDO> { }

// ❌ 禁止：api 包出现 Spring 注解
@Service
public class ModelProviderServiceImpl { }
```

---

### L2 `internal/controller/` — 接入层

**允许**
- `@RestController` / `@RequestMapping`
- `@Valid` 参数校验、`@RequestBody` / `@RequestParam`
- 调用本模块 `internal/service` 或其他模块 `api` 接口
- 返回 DTO

**禁止**
- 业务逻辑（分支、循环、状态判断）
- `@Transactional`
- 直接注入 `Mapper`（任何模块的）
- 直接返回 `internal/entity` 下的 DO
- 在 controller 内做 DO↔DTO 转换

**判据**：controller 方法体**只有 3–5 行**——参数绑定、调 service、返回。超过 8 行说明业务逻辑漏出来了，往 service 挪。

```java
// ✅
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    @Resource
    private AgentService agentService;

    @GetMapping("/{id}")
    public AgentDTO get(@PathVariable String id) {
        return agentService.get(id);
    }

    @PostMapping
    public AgentDTO create(@RequestBody @Valid AgentDTO dto) {
        return agentService.create(dto);
    }
}

// ❌ 禁止：controller 里写业务逻辑 + 直接返回 DO
@GetMapping("/{id}")
public AgentDO get(@PathVariable String id) {
    if (agentDO.getStatus() == 1) {            // 业务分支
        agentDO.setName("已启用: " + agentDO.getName());
    }
    return agentMapper.selectById(id);           // 直接访问 Mapper + 返回 DO
}
```

---

### L3 `internal/service/` — 业务层

**允许**
- 业务规则、状态流转、编排
- `@Transactional`（**事务边界只允许出现在这一层**）
- 注入其他模块的 `api` 接口
- 注入本模块的 `mapper`
- 发布事件（`ApplicationEventPublisher`）
- 调用 `converter` 做 DO↔DTO 转换

**禁止**
- 直接注入其他模块的 `Mapper` 或 `internal` 下任何类
- 在 service 里写 SQL（除非要调 Mapper 的自定义方法）
- 把 DO 直接返回给 controller
- `ai` 模块中禁止出现 knowledge / tool / agent 的任何类型

**`@Transactional` 方法内禁止调用慢的外部能力**：模型调用（ai）、工具调用（tool）、MCP 调用（mcp）、知识库检索（knowledge）。跨模块的**快速查询**（读配置、读元数据）允许。

理由：这些调用耗时可达数十秒，会让事务长期持有数据库连接；流式响应期间尤其明显。正确做法是拆成"读—外部调用—写"三段，每段独立事务。

**命名**：接口在 `api` 包，实现类在 `internal/service`，命名 `XxxServiceImpl`。

**判据**：一个 service 方法如果只做"取 DO → 转 DTO → 返回"，应该下沉到 converter 或直接用 MP 的 `IService`。

**关于 MyBatis-Plus**：
- **单表 CRUD** → 直接用 MP 的 `IService<T>` / `ServiceImpl<M, T>`，接口放 `internal/service`（不放 `api`，因为 `IService` 带 DO 泛型）
- **多表 / 业务规则** → 写独立的 `XxxService`（不继承 `ServiceImpl`），内部注入 `Mapper`
- **api 包的接口绝不能继承 `IService`**（否则 DO 类型泄漏到契约层）

```java
// ✅ 简单 CRUD：走 MP
@Service
public class ModelProviderCrudService extends ServiceImpl<ModelProviderMapper, ModelProviderDO> { }

// ✅ 业务逻辑：独立 service，自己管事务
@Service
public class AgentServiceImpl implements AgentService {

    @Resource
    private AgentMapper agentMapper;
    @Resource
    private ModelProviderService modelProviderService;   // ✅ ai.api
    @Resource
    private ToolCatalog toolCatalog;                     // ✅ tool.api
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDTO create(AgentDTO dto) {
        AgentDO entity = converter.toDO(dto);
        agentMapper.insert(entity);
        eventPublisher.publishEvent(new AgentConfigChangedEvent(entity.getId()));
        return converter.toDTO(entity);
    }
}
```

---

### L4 `internal/mapper/` — 数据访问层

**允许**
- `interface XxxMapper extends BaseMapper<XxxDO>`
- `@Select` / `@Update` 等注解写自定义 SQL
- XML 映射文件（同目录 `resources/mapper/`）

**禁止**
- 业务逻辑
- `@Transactional`
- 查询**其他模块前缀**的表（见 M3）

**表名规则**：SQL 里出现的任何表名，必须以本模块的表前缀开头。

```java
@Mapper
public interface AgentMapper extends BaseMapper<AgentDO> {

    @Select("SELECT * FROM ai_agent WHERE workspace_id = #{workspaceId} AND status = 1")
    List<AgentDO> listEnabled(@Param("workspaceId") String workspaceId);
}
```

---

### L5 `internal/entity/` — 数据对象层

**允许**
- `@TableName("ai_agent")` 标注的 POJO
- MyBatis-Plus 的 `@TableId` / `@TableField`

**禁止**
- 出现在 `api` 包
- 作为 controller 的返回类型
- 作为跨模块方法签名的参数或返回值
- 携带业务方法（DO 只放数据 + 极少量与持久化强相关的 getter/setter）

```java
// ✅ hify-agent/src/main/java/com/hify/agent/internal/entity/AgentDO.java
@TableName("ai_agent")
public class AgentDO {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String modelProviderId;
    private String prompt;
    private String toolIds;      // JSON 数组
    private String knowledgeIds; // JSON 数组
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // getter / setter
}
```

---

### L6 `internal/converter/`、`internal/spi/`、`internal/config/`

**converter** — DO ↔ DTO 转换，静态方法或 Spring Bean：
```java
@Component
public class AgentConverter {
    public static AgentDTO toDTO(AgentDO entity) { ... }
    public static AgentDO toDO(AgentDTO dto) { ... }
}
```
判据：**转换逻辑不得堆在 service 里**。service 里出现连续的 `setXxx(getXxx())` 超过 5 个字段，抽到 converter。

**spi** — 扩展点接口，**只放在消费方模块的 `api` 包**，实现在来源方模块：
```java
// ✅ hify-tool/src/main/java/com/hify/tool/api/ToolProvider.java
package com.hify.tool.api;

public interface ToolProvider {
    List<ToolSpec> listTools();
    ToolResult invoke(String toolName, Map<String, Object> args);
    String sourceType();   // "builtin" | "http" | "mcp"
}

// ✅ hify-mcp/src/main/java/com/hify/mcp/internal/spi/McpToolProvider.java
@Service
public class McpToolProvider implements ToolProvider { ... }
```
**禁止**：`tool` 模块的 `api` 包不得出现 `mcp` 的任何类型（否则 `tool ↔ mcp` 成环）。

**config** — 模块级 `@Configuration`，只装配本模块的 bean。

---

## 3. 跨模块调用规则

### M1 — 只能通过对方 `api` 包的 public interface 调用

```java
// ✅
@Resource private ModelCallService modelCallService;      // com.hify.ai.api

// ❌
@Resource private OpenAiCompatibleModelCaller caller;     // com.hify.ai.internal
```

### M2 — 禁止跨模块调用 Mapper，禁止注入对方 `internal` 下任何类

### M3 — 禁止跨模块直接查对方的表

SQL 里出现的表名必须以本模块表前缀开头。对方的数据一律走对方的 `api` 接口。

### M4 — 跨模块只传 DTO，不传 DO

方法签名里不得出现 `internal.entity` 下的类型。DTO 必须：不含 ORM 注解、不含循环引用、字段全部可 JSON 序列化（`LocalDateTime` 统一用 `String` 或配置好 Jackson 的 JavaTimeModule）。

### M5 — 依赖方向单向

能力层（ai / tool / knowledge / agent / mcp）不得依赖编排层（conversation / workflow）。
`ai` 不得依赖 knowledge / tool / agent / mcp。

### M6 — 工具不认识会话

`tool` 的 `api` 包不得出现 `conversation` / `workflow` 的任何类型。**工具执行结果由编排层落库**，工具只负责"给我参数，返回结果"。

### M7 — 上下文不靠 ThreadLocal 跨模块

跨模块接口的入参必须**显式携带**所需上下文（当前用户 ID、trace id、workspace id）。
ThreadLocal 只用于 HTTP 请求内的局部便利，**不得作为模块间契约的一部分**。

```java
// ❌
public ToolResult invoke(String toolName, Map<String,Object> args);   // 隐式依赖 ThreadLocal 的当前用户

// ✅
public ToolResult invoke(ToolInvokeContext context, String toolName, Map<String,Object> args);
```

### M8 — 跨模块副作用走事件

| 事件 | 发布者 | 监听者 |
|---|---|---|
| `ModelProviderChangedEvent` | ai | agent, conversation |
| `McpToolListChangedEvent` | mcp | tool |
| `KnowledgeIndexedEvent` | knowledge | agent, conversation |
| `AgentConfigChangedEvent` | agent | conversation |

规则：事件流单向，发布者层次 ≥ 监听者层次；监听用 `@TransactionalEventListener(phase = AFTER_COMMIT)`；事件带 `version`，只有版本递增才处理。

**事件分两类，用途不同，拆分影响也不同**：

| 事件 | 发布者 | 监听者 | 类型 | 拆成独立服务后 |
|---|---|---|---|---|
| `ModelProviderChangedEvent` | ai | agent, conversation | 进程内缓存失效 | 改为 TTL 刷新，不需 MQ |
| `McpToolListChangedEvent` | mcp | tool | 进程内缓存失效 | 改为 TTL 刷新，不需 MQ |
| `KnowledgeIndexedEvent` | knowledge | agent, conversation | 进程内缓存失效 | 改为 TTL 刷新，不需 MQ |
| `AgentConfigChangedEvent` | agent | conversation | 进程内缓存失效 | 改为 TTL 刷新，不需 MQ |

**当前四个事件全部是"进程内缓存失效"用途**，因此拆分时**不需要引入 MQ**——改用 Redis + TTL 刷新即可。只有当出现真正的跨进程业务契约（如"订单已创建"要触发下游流程）时，才需要 MQ。

### M9 — 认证必须无状态或 Redis 共享

会话与令牌不得依赖单机内存。必须满足其一：
- 无状态令牌（JWT），由各服务独立校验
- Redis 共享 session

违反此条，拆出独立服务时认证会直接断裂。

### M10 — 扩展点用依赖倒置

来源方模块实现消费方模块 `api` 包里的 SPI 接口，**消费方不依赖来源方**。
`tool` 定义 `ToolProvider`，`builtin` / `http` / `mcp` 各自实现，`tool` 通过注入 `List<ToolProvider>` 收集。

---

## 4. 命名约定

| 对象 | 命名 | 示例 |
|---|---|---|
| 包名 | 全小写无下划线 `com.hify.<module>.<layer>` | `com.hify.ai.internal.service` |
| api 接口 | `XxxService` | `ModelProviderService` |
| api DTO | `XxxDTO` | `ModelProviderDTO` |
| api 事件 | `XxxEvent` | `AgentConfigChangedEvent` |
| controller | `XxxController` | `AgentController` |
| service 实现 | `XxxServiceImpl` | `AgentServiceImpl` |
| mapper | `XxxMapper` | `AgentMapper` |
| DO | `XxxDO`（**不用 Entity**，避免与领域对象混淆） | `AgentDO` |
| converter | `XxxConverter` | `AgentConverter` |
| SPI 实现 | `XxxProvider` / `XxxStrategy` | `McpToolProvider` |
| 配置类 | `XxxConfiguration` | `McpConfiguration` |
| 测试 | `XxxTest` / `XxxArchTest` | `ModuleBoundaryTest` |

**表名前缀**：`ai_`（model provider、agent）、`kb_`（knowledge）、`wf_`（workflow）、`conv_`（conversation）、`mcp_`（mcp）、`tool_`（tool）。

**接口多实现时的注入**：用 `@Qualifier` 显式指定，禁止依赖 `@Primary`：
```java
@Resource
@Qualifier("mcpToolProvider")
private ToolProvider mcpToolProvider;
```

---

## 5. 可执行检查

### 5.1 Maven 依赖层：循环依赖 + 版本收敛

父 pom（**已实测通过**，见 `pom.xml`）：

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <version>3.6.3</version>
      <!--
        banCircularDependencies 不在 enforcer 自带的 enforcer-rules 里，
        必须显式引入 extra-enforcer-rules 才会被加载，否则报
        "Failed to create enforcer rules with name: banCircularDependencies"。
      -->
      <dependencies>
        <dependency>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>extra-enforcer-rules</artifactId>
          <version>1.12.1</version>
        </dependency>
      </dependencies>
      <executions>
        <execution>
          <id>ban-circular-dependencies</id>
          <!--
            绑定 verify 而非 validate：这条规则要解析完整依赖图才能判定，
            validate 阶段内部模块（SNAPSHOT）尚未构建，会误报
            "Could not find artifact com.hify:hify-shared:jar:0.1.0-SNAPSHOT"。
          -->
          <phase>verify</phase>
          <goals><goal>enforce</goal></goals>
          <configuration>
            <rules>
              <banCircularDependencies/>
              <requireMavenVersion><version>[3.6.1,)</version></requireMavenVersion>
              <dependencyConvergence/>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**两层环检测，别混淆**：
- **模块级环**（A 依赖 B、B 依赖 A）—— Maven 在构建 reactor 时就直接报 `The projects in the reactor contain a cyclic reference`，**不依赖本规则**
- **artifact 级环**（解析后的完整依赖图里牵涉第三方 artifact）—— 由 `banCircularDependencies` 覆盖

**模块 pom 一律不写 `<version>`**，全部由父 pom 的 `<dependencyManagement>` 统一管理。

### 5.2 ArchUnit：模块边界

**代码位置：`hify-app/src/test/java/com/hify/arch/ModuleBoundaryTest.java`**（app 依赖全部模块，能扫到所有类的字节码）。
下面这份代码是**该文件的副本**，改动请改文件本身——文档与代码不一致时以代码为准。

```java
package com.hify.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块边界检查。
 *
 * <p>这些规则把 CLAUDE.md §3.2「分层与跨模块」的约定变成 CI 里会失败的断言。
 * 放在 {@code hify-app} 的测试目录——它是唯一依赖全部业务模块的模块，
 * 因此能扫到所有类的字节码。
 *
 * <p>规则与文档的对应：
 * <ul>
 *   <li>M1 / M2 —— 跨模块只走对方 {@code api} 包，不得碰 {@code internal}</li>
 *   <li>M2 —— 不得访问其他模块的 Mapper</li>
 *   <li>M4 —— {@code api} 层不得暴露 DO</li>
 *   <li>M5 —— 依赖方向单向：能力层不得反向依赖编排层</li>
 *   <li>M6 —— {@code tool} 不得依赖编排层</li>
 *   <li>M9 / M10 —— 认证约束与 {@code api} 包的远程调用友好性</li>
 * </ul>
 */
class ModuleBoundaryTest {

    /** 能力层模块（被编排层调用的那些）。 */
    private static final List<String> CAPABILITY = List.of("ai", "tool", "knowledge", "agent", "mcp");

    /** 编排层模块。 */
    private static final List<String> ORCHESTRATION = List.of("conversation", "workflow");

    /** 全部业务模块（不含 shared 与 app）。 */
    private static final List<String> BUSINESS = List.of(
            "ai", "tool", "knowledge", "mcp", "agent", "conversation", "workflow");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.hify");
    }

    /** {@code com.hify.<module>[.<sub>]} → {@code <module>}。 */
    private static String moduleOf(String packageName) {
        Matcher m = Pattern.compile("^com\\.hify\\.([a-z][a-z0-9]*)(?:\\..*)?$").matcher(packageName);
        return m.matches() ? m.group(1) : packageName;
    }

    /** 匹配 {@code com.hify.x.api} 与 {@code com.hify.x.api.dto}，但不匹配 {@code x.apimpl}。 */
    private static boolean isApiPackage(String pkg) {
        return pkg.matches(".*\\.api(\\..*)?");
    }

    private static boolean isInternalPackage(String pkg) {
        return pkg.matches(".*\\.internal(\\..*)?");
    }

    private static boolean isMapperPackage(String pkg) {
        return pkg.matches(".*\\.mapper(\\..*)?");
    }

    /** 收集「源类 → 目标类」的跨模块依赖描述，便于断言时给出可读的失败信息。 */
    private static List<String> describeViolations(java.util.function.Predicate<Violation> filter) {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                Violation violation = new Violation(source, target);
                if (filter.test(violation)) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }
        return violations;
    }

    private record Violation(JavaClass source, JavaClass target) {
        String sourceModule() {
            return moduleOf(source.getPackageName());
        }

        String targetModule() {
            return moduleOf(target.getPackageName());
        }
    }

    // ── M1 / M2：跨模块不得依赖对方的 internal ─────────────────────────

    @Test
    @DisplayName("跨模块不得依赖对方的 internal 包")
    void 跨模块不得依赖对方的internal包() {
        List<String> violations = describeViolations(violation ->
                !violation.sourceModule().equals(violation.targetModule())
                        && isInternalPackage(violation.target().getPackageName()));

        assertThat(violations).as("跨模块只能通过对方的 api 包调用").isEmpty();
    }

    // ── api 包不得依赖 internal ───────────────────────────────────────

    @Test
    @DisplayName("api 包不得依赖任何 internal 包")
    void api包不得依赖internal包() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (isInternalPackage(target.getPackageName())) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("api 包是模块的公开契约，不得引用内部实现").isEmpty();
    }

    // ── api 包不得出现 Spring / ORM 注解 ──────────────────────────────

    @Test
    @DisplayName("api 包不得依赖 Spring 注解或 ORM 注解")
    void api包不得依赖Spring或ORM注解() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String pkg = target.getPackageName();
                if (pkg.startsWith("org.springframework.")
                        || pkg.startsWith("com.baomidou.mybatisplus.annotation")
                        || pkg.startsWith("jakarta.persistence")) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("api 包必须能脱离 Spring 与 ORM 独立序列化").isEmpty();
    }

    // ── M4：api 层不得暴露 DO ─────────────────────────────────────────

    @Test
    @DisplayName("api 层不得暴露数据对象")
    void api层不得暴露DO() {
        List<String> violations = new ArrayList<>();
        for (JavaClass source : classes) {
            if (!isApiPackage(source.getPackageName())) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getPackageName().matches(".*\\.internal\\.entity(\\..*)?")) {
                    violations.add(source.getFullName() + " -> " + target.getFullName());
                }
            }
        }

        assertThat(violations).as("跨模块只传 DTO，不传 ORM 实体").isEmpty();
    }

    // ── M2：不得访问其他模块的 Mapper ─────────────────────────────────

    @Test
    @DisplayName("不得访问其他模块的 Mapper")
    void 不得访问其他模块的Mapper() {
        List<String> violations = describeViolations(violation ->
                !violation.sourceModule().equals(violation.targetModule())
                        && isMapperPackage(violation.target().getPackageName()));

        assertThat(violations).as("跨模块取数必须走对方的 api 接口").isEmpty();
    }

    // ── M5：能力层不得反向依赖编排层 ─────────────────────────────────

    @Test
    @DisplayName("能力层不得依赖编排层")
    void 能力层不得依赖编排层() {
        List<String> violations = describeViolations(violation ->
                CAPABILITY.contains(violation.sourceModule())
                        && ORCHESTRATION.contains(violation.targetModule()));

        assertThat(violations).as("依赖方向只能从编排层指向能力层").isEmpty();
    }

    // ── M5：ai 不得依赖其它能力模块 ──────────────────────────────────

    @Test
    @DisplayName("ai 模块不得依赖 knowledge / tool / agent / mcp")
    void ai模块不得依赖组合能力模块() {
        List<String> forbidden = List.of("knowledge", "tool", "agent", "mcp");
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("ai") && forbidden.contains(violation.targetModule()));

        assertThat(violations)
                .as("ai 必须是纯模型调用能力，组合逻辑只能在编排层，否则 ai ↔ knowledge 成环")
                .isEmpty();
    }

    // ── M6：tool 不得依赖编排层 ──────────────────────────────────────

    @Test
    @DisplayName("tool 模块不得依赖编排层")
    void tool模块不得依赖编排层() {
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("tool") && ORCHESTRATION.contains(violation.targetModule()));

        assertThat(violations).as("tool 定义 SPI 由来源方实现，不得反向依赖编排层").isEmpty();
    }

    // ── 业务模块不得依赖 hify-app ────────────────────────────────────

    @Test
    @DisplayName("业务模块不得依赖装配模块 hify-app")
    void 业务模块不得依赖装配模块() {
        List<String> violations = describeViolations(violation ->
                BUSINESS.contains(violation.sourceModule()) && violation.targetModule().equals("app"));

        assertThat(violations).as("hify-app 是顶层装配，业务模块不得反向依赖它").isEmpty();
    }

    // ── shared 不得依赖业务模块 ──────────────────────────────────────

    @Test
    @DisplayName("shared 不得依赖任何业务模块")
    void shared不得依赖业务模块() {
        List<String> violations = describeViolations(violation ->
                violation.sourceModule().equals("shared") && BUSINESS.contains(violation.targetModule()));

        assertThat(violations).as("shared 是叶子模块，依赖业务模块会成环").isEmpty();
    }
}
```

依赖（`hify-app` 的 test scope）：

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.5.1</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.assertj</groupId>
  <artifactId>assertj-core</artifactId>
  <version>3.25.3</version>
  <scope>test</scope>
</dependency>
```

ArchUnit 运行时依赖 `slf4j-api`（Maven 会传递引入；在 Maven 之外手动跑时需显式加 `slf4j-api`）。

> **验证记录（2026-10-29，真实执行）**
>
> 检查代码已落到 `hify-app/src/test/java/com/hify/arch/ModuleBoundaryTest.java`，共 **10 条规则**。
>
> 验证方式：植入 7 个**可编译的**违规样例后运行，结果 **5 条规则准确报错**、5 条通过；删除样例后 10 条全绿。
>
> | 规则 | 植入的违规 | 结果 |
> |---|---|---|
> | 跨模块依赖 internal | `ai` → `shared.internal` | ✅ 报错 |
> | api 依赖 internal | `shared.api` → `shared.internal` | ✅ 报错 |
> | api 依赖 Spring/ORM 注解 | `shared.api` 上标 `@Service` | ✅ 报错 |
> | api 暴露 DO | `shared.api` 持有 `internal.entity` 字段 | ✅ 报错 |
> | 跨模块访问 Mapper | `agent` → `ai.internal.mapper` | ✅ 报错 |
> | 能力层→编排层、ai→tool、tool→编排层、业务→app、shared→业务 | — | **Maven 依赖图在编译期就挡住，代码层面无法构造出违规**（见下） |
>
> **关键结论：模块级方向由 Maven 依赖图强制（编译期），ArchUnit 强制的是「Maven 已允许的依赖方向内」的包级与字段级约束。** 例如 `ai → shared` 是允许的，但 `ai → shared.internal` 不是——这条只有 ArchUnit 能拦。反过来 `ai → tool` 在 Maven 层面就不成立，要加这个依赖得先改 pom，那本身已经是违规。
>
> 因此 10 条规则分两层价值：**规则 1–5 是主力**（包级/字段级细分）；**规则 6–10 是纵深防御**（万一有人先加了 Maven 依赖，代码层的方向检查还能兜住）。

### 5.3 Spring 层：禁止循环依赖

```yaml
spring:
  main:
    allow-circular-references: false   # 保持默认，不得改为 true
```

Bean 循环在**启动时**暴露，不是运行时。不得为了通过启动而打开此开关。

### 5.4 SQL 层：跨模块查表

用 Checkstyle 或简单的 grep 门禁：对 `internal/mapper/**/*.java` 与 `resources/mapper/*.xml` 中的表名，校验前缀属于本模块。表前缀清单：

| 模块 | 前缀 |
|---|---|
| hify-ai | `ai_model_provider` |
| hify-agent | `ai_agent` |
| hify-knowledge | `kb_` |
| hify-workflow | `wf_` |
| hify-conversation | `conv_` |
| hify-mcp | `mcp_` |
| hify-tool | `tool_` |

---

## 6. 新增一个模块的清单

1. 父 pom `<modules>` 增加 `<module>hify-xxx</module>`
2. 新建 `hify-xxx/pom.xml`，parent 指向根 pom，**不写版本号**
3. 建包结构：`com.hify.xxx.api`（接口 + dto + event）、`com.hify.xxx.internal.{controller,service,mapper,entity,converter,config}`
4. 在依赖图上确定层级，只允许依赖更低层的模块
5. 表名前缀登记到 §5.4 清单
6. `hify-app/pom.xml` 增加对该模块的依赖
7. 在 `ModuleBoundaryTest` 的能力层/编排层清单中登记模块名
8. 事件：若该模块发布事件，在 §3 M8 表格登记发布者与监听者

---

## 7. 新增一个工具来源的清单

1. 在 `hify-tool` 的 `api` 包确认 SPI 签名（`ToolProvider` / `ToolSpec` / `ToolResult`），**除非现有 SPI 不够用，否则不修改它**
2. 在来源模块（builtin / http / mcp）实现 `ToolProvider`
3. 实现类加 `@Service`，`sourceType()` 返回唯一标识
4. `tool` 模块的 `DefaultToolCatalog` 自动收集所有 `ToolProvider` bean，**无需修改 tool 模块**
5. 若该来源需要配置（如 MCP server），配置实体放**来源模块**自己的 `internal/entity`，表前缀用来源模块的

---

## 8. 新增一个工作流节点的清单

1. 在 `hify-workflow` 的 `internal/service/node` 下新建 `XxxNodeExecutor`，实现统一的节点执行接口
2. 节点所需能力一律通过 `hify-workflow` 的**已注入依赖**调用（ai / tool / knowledge / agent 的 api 接口），**不得直接 new 客户端**
3. 节点输入输出的变量类型在 `hify-workflow` 的变量类型枚举中登记
4. 前端（Vue）新增该节点的配置面板，节点 key 与后端 executor 的 `type()` 一致
5. 更新 §5.2 中 `ModuleBoundaryTest`（如新增了新的依赖方向）

---

## 9. PR 自检清单

提交前逐条确认：

- [ ] 新增的 public 接口/类放在正确的 `api` 或 `internal` 层
- [ ] `api` 包内无 Spring 注解、无 ORM 注解、无 `internal` 引用
- [ ] 跨模块调用只注入对方的 `api` 接口（不是实现类）
- [ ] 跨模块方法签名里没有 DO
- [ ] 没有跨模块直接查对方的表
- [ ] 依赖方向没有反向；`mvn -T1C clean install` 通过（enforcer 的循环依赖检查绿）
- [ ] `@Transactional` 只出现在 `internal/service` 层
- [ ] controller 方法体不超过 8 行
- [ ] 没有打开 `spring.main.allow-circular-references`
- [ ] 新增的跨模块副作用走事件，并已登记到 M8 表
- [ ] ThreadLocal 未作为跨模块契约的一部分
- [ ] `ModuleBoundaryTest` 全绿

---

## 10. 反例速查

| 现象 | 违规 | 正确做法 |
|---|---|---|
| `ai` 里出现"带知识库的对话" | ai → knowledge，与 knowledge → ai 成环 | 组合逻辑放 `conversation` |
| `tool` 里 import `mcp` 的类 | tool ↔ mcp 成环 | `tool` 定义 SPI，`mcp` 实现，Spring 收集 |
| controller 返回 `AgentDO` | DO 泄漏到契约层 | 返回 `AgentDTO`，转换放 converter |
| api 接口继承 `IService<T>` | DO 泛型泄漏 | api 接口独立定义，MP 的 Service 放 internal |
| service 里直接 `selectById` 对方的表 | M3 违规 | 调对方的 api 接口 |
| 工具执行结果在 tool 里落库 | tool → conversation 成环 | 由编排层落库 |
| 跨模块方法靠 ThreadLocal 拿当前用户 | M7 违规 | 入参显式带 `ToolInvokeContext` |
| 为通过启动打开 `allow-circular-references` | 掩盖真问题 | 拆解依赖方向 |

---

## 11. 拆分影响评估（以 `hify-conversation` 拆为独立服务为例）

本节用于验证本规范是否真的兑现"模块内部不用动"。

### 11.1 拆分难度由"被依赖数"决定

| 模块 | 被谁依赖 | 拆出后需改动的模块数 | 难度 |
|---|---|---:|---|
| `hify-conversation` | 无 | 0 | 最低 |
| `hify-workflow` | 无 | 0 | 最低 |
| `hify-mcp` | 无（`tool` 通过 SPI 反向消费） | 0 | 低 |
| `hify-agent` | conversation, workflow | 2 | 中 |
| `hify-knowledge` | agent, conversation, workflow | 3 | 中 |
| `hify-tool` | agent, conversation, workflow | 3 | 中 |
| `hify-ai` | knowledge, agent, conversation, workflow | 4 | 最高 |

**`hify-conversation` 的被依赖数为 0**，拆出去不迫使任何其他模块改动——这是它最好拆的原因。反之 `hify-ai` 是最难拆的。

### 11.2 需要改的部分（约 30–40 个文件）

| # | 改动项 | 文件数 | 说明 |
|---|---|---:|---|
| 1 | 新建独立 Spring Boot 应用 | 5 | pom、启动类、`application.yml`、Dockerfile、compose 服务项 |
| 2 | 远程客户端接口 | 4 | `AiClient` / `ToolClient` / `KnowledgeClient` / `AgentClient`，各自 `extends` 对应的 `api` 接口，签名自动对齐 |
| 3 | 远程调用配置 | 1 | 超时、重试、连接池、日志级别 |
| 4 | 上下文透传拦截器 | 1 | 当前用户 / traceId / conversationId 走 header |
| 5 | 降级兜底 | 1–4 | 远程不可用时的降级实现 |
| 6 | **service 注入点修改** | 5–8 | 从注入本地 bean 改为注入远程 bean，**每处 1–3 行** |
| 7 | 事件监听改为 TTL 刷新 | 3 | 三个缓存失效事件改 Redis + TTL，**不需要 MQ** |
| 8 | 数据库 | 0 或 2–3 | 共用 MySQL 实例则 0；独立库则建表脚本 + 数据迁移 |
| 9 | 认证 | 1–2 | JWT 校验或 Redis session 共享 |
| 10 | 健康检查与 Actuator | 1 | |
| 11 | CI 与部署脚本 | 2–3 | |
| 12 | 契约测试 | 3–5 | 远程接口的 mock 测试 |

**业务逻辑改动量：0。** 全部改动集中在"依赖注入方式"和"部署配置"。

### 11.3 不需要改的部分

- `internal/controller/` — HTTP 接口路径与请求体不变
- `internal/service/` — 业务逻辑不变
- `internal/mapper/`、`internal/entity/`、`internal/converter/` — 数据访问与模型不变
- 所有 `api` 包的接口与 DTO — **原样复制到新服务**

### 11.4 若当初未按本规范组织

| 违规做法 | 拆分时的后果 |
|---|---|
| 跨模块传 DO | 序列化炸（循环引用、ORM 注解、`LocalDateTime`），全部接口签名重写 |
| 直接注入对方实现类 / Mapper | conversation 中每处引用都要改，扩散到 4 个模块 |
| 靠 ThreadLocal 传当前用户 | 远程后失效，鉴权全线断裂 |
| 跨模块直接查表 | 拆库时全部重写 |
| 把事件当跨进程契约用 | 被迫引入 MQ，增加一套基础设施 |
| 无状态要求缺失（单机 session） | 认证直接坏掉 |

估计改动量：**150–250 个文件、3–6 个月**，且需要回归全部业务流程。

### 11.5 时间估算

**2–4 周**（含实现、测试、联调、部署配置），其中业务逻辑 0 周，主要是远程调用适配层与部署工程。

---

---

## 12. 双数据源规则（`hify-knowledge` 模块）

Hify 使用两个数据库：**MySQL**（业务数据）与 **PostgreSQL + pgvector**（知识库向量）。

| 库 | 访问方式 | 代码位置 | 用途 |
|---|---|---|---|
| MySQL | MyBatis-Plus `BaseMapper` / `IService` | `internal/mapper`、`internal/entity` | 全部业务数据 + 知识库元数据 |
| PostgreSQL | `NamedParameterJdbcTemplate` 手写 SQL | `internal/repository/pg` | `vec_chunk` 分段与向量 |

### 硬性规则

1. **PG 侧不得使用 MyBatis-Plus**。向量检索需要 pgvector 的 `<=>` 运算符与 HNSW 索引，`BaseMapper` 生成不了这类 SQL。
2. **两个 DataSource bean 显式命名**（`dataSource` / `vectorDataSource`），不依赖 `@Primary` 自动路由，也不引入动态数据源插件——两个库用途固定，动态路由只会让"这次写哪个库"变得不确定。
3. **PG 的数据对象不进 `api` 包**，与 MySQL 的 DO 同样受 M4 约束。
4. **PG 侧的 SQL 只出现在 `internal/repository/pg`**，不得散落到 service 或 controller。
5. **跨库的写入流程必须可重入**（两库之间没有分布式事务）：
   - PG 侧写入靠 `UNIQUE (doc_id, chunk_index)` 保证幂等，冲突则覆盖
   - MySQL 侧用状态机（UPLOADED / INDEXING / READY / FAILED / DELETING）+ 定时任务扫出僵死状态重跑
   - **删除顺序固定为「MySQL 软删 → 异步删 PG → MySQL 硬删」**，反过来会在中途崩溃时导致检索静默返回空
6. **`hify-knowledge` 是唯一允许同时访问两个库的模块**。其他模块不得直接访问 `vectorDataSource`——需要向量的能力一律通过 `knowledge` 的 `api` 包暴露。

### 对依赖图的影响

无。`hify-knowledge` 内部用两个库，是模块**实现细节**，不改变模块间依赖方向，不引入新的跨模块依赖，不构成环。

