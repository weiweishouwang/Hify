# Java 编码规范（20 条）

> 基于《阿里巴巴 Java 开发手册》提炼，聚焦命名、异常处理、日志、并发。
> **本节可直接复制进任意 Java 项目的 CLAUDE.md。** 规则用「必须 / 禁止 / 应该」表述，每条配正反例——AI 可据此直接产出符合规范的代码。
> 与项目自身约定冲突时，**以项目约定为准**（项目约定通常更严）。

---

## 一、命名（5 条）

**N1 · 统一命名风格**
类名 `UpperCamelCase`；方法名、变量名、参数名、成员变量 `lowerCamelCase`；常量 `全大写_下划线`；包名全小写、单数形式。
```java
// ✅
public class OrderService {
    private static final int MAX_RETRY = 3;
    public long calculateAmount(long price, int count) { return price * count; }
}
// ❌
public class orderService { static final int maxRetry = 3; }
```

**N2 · POJO 布尔字段不加 `is` 前缀**
Lombok 生成 `isDeleted()`，Jackson 序列化出的字段名是 `deleted` 而非 `isDeleted`，与前端约定和 MyBatis-Plus 的属性推导都会错位。
```java
// ✅
private boolean deleted;
// ❌
private boolean isDeleted;
```

**N3 · 杜绝望文不知义的缩写**
禁止 `AbsClass` / `func` / `msg` / `temp` 这类脱离上下文就无法理解的缩写；确需缩写时，**必须在注释中给出全称**。
```java
// ✅  AbstractDiscountRule（注释：折扣规则抽象基类）
// ❌  AbsDiscRule、calcAmt，且注释里也没写全称
```

**N4 · 禁止魔法值，一律抽常量**
状态码、业务类型、阈值、超时时间、默认值都必须命名成常量。
```java
// ✅
public static final int STATUS_ENABLED = 1;
public static final Duration LLM_DEFAULT_TIMEOUT = Duration.ofSeconds(30);
if (order.getStatus() == STATUS_ENABLED) { ... }
// ❌
if (order.getStatus() == 1) { ... }
```

**N5 · 按角色选后缀，同一项目内保持一致**
DO / DTO / BO / VO / POJO；抽象类 `Abstract` 或 `Base` 前缀；异常类 `Exception` 后缀；测试类 `Test` 后缀；枚举类 `Enum` 后缀、枚举成员全大写单词间用下划线。
```java
// ✅
public class AgentDTO { }
public class AgentDO { }
public class BaseException extends RuntimeException { }
public class AgentStatusEnum { public static final AgentStatusEnum ENABLED = new AgentStatusEnum(); }
// ❌ 同一项目里 DTO 和 Vo 混用
```

---

## 二、异常处理（5 条）

**E1 · 异常不用于流程控制**
能用 `if` 判断的分支不要用异常表达；异常只表示"出错了"。
```java
// ✅
if (user == null) {
    throw new BizException(ErrorCode.USER_NOT_FOUND);
}
// ❌
try {
    user = mapper.selectById(id);
} catch (Exception e) {
    throw new BizException("用户不存在");
}
```

**E2 · 禁止静默吞异常，必须保留原始堆栈**
`catch` 后至少要记录或重抛；重抛时**必须传 `cause`**，不能只传 `getMessage()`。稳定代码（不会出错的）catch 住并记录；不稳定代码（可能出错的）不要 catch，往外抛给最外层统一处理。
```java
// ✅
catch (BizException e) {
    throw e;                                   // 业务异常原样上抛
} catch (Exception e) {
    log.error("加载 Agent 失败, id={}", id, e);  // 记录时带堆栈
    throw new BizException(ErrorCode.AGENT_LOAD_FAILED, "加载 Agent 失败", e);
}
// ❌
catch (Exception e) { }                                        // 完全吞掉
catch (Exception e) { throw new RuntimeException(e.getMessage()); }  // 丢失堆栈
```

**E3 · 资源必须用 try-with-resources 关闭**
禁止在 `finally` 里手写 `close()` 而不判空、且把 `SQLException` 吞掉。
```java
// ✅
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    ...
}
// ❌
finally {
    if (conn != null) {
        try { conn.close(); } catch (SQLException e) { /* 吞掉 */ }
    }
}
```

**E4 · 自定义异常继承 `RuntimeException`，必须带错误码**
必须有 `serialVersionUID`；至少提供「仅错误码」和「带 message、cause」两组构造器；**业务失败必须能靠错误码程序化区分，不能只靠 message 文本**。
```java
// ✅
public class BizException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
// ❌
public class BizException extends Exception { }   // 检查异常，调用方被迫 catch
public class BizException extends RuntimeException { public BizException(String m) { super(m); } }  // 无错误码
```

**E5 · 事务回滚规则与慢调用**
`@Transactional` 默认**只回滚 `RuntimeException`**，抛检查异常需要显式 `@Transactional(rollbackFor = Exception.class)`；**事务方法内禁止调用模型、MCP、工具执行等慢能力**——它们会长时间持有数据库连接。跨模块的快速查询允许。
```java
// ✅
@Transactional(rollbackFor = Exception.class)
public void saveAgent(AgentDTO dto) { ... }   // 只做本地写

// ❌
@Transactional
public void chat(String question) {
    sessionMapper.insert(record);       // 事务开始
    llmGateway.stream(question)...;     // 30–120 秒，连接被一直占着
}
// ❌
@Transactional                    // 抛 IOException 时不会回滚
public void importDoc(InputStream in) throws IOException { ... }
```

---

## 三、日志（5 条）

**L1 · 必须用占位符，禁止字符串拼接**
拼接会无视日志级别无条件执行。
```java
// ✅
log.info("Agent [{}] 调用模型 [{}] 耗时 [{}]ms", agentId, model, cost);
// ❌
log.info("Agent [" + agentId + "] 调用模型");   // 即使 INFO 关闭也会拼接
```

**L2 · 必须打印完整堆栈**
异常对象作为最后一个参数传入，不要自己拼 `getMessage()`。
```java
// ✅
log.error("加载 Agent 失败, id={}", id, e);
// ❌
log.error("加载 Agent 失败: " + e.getMessage());   // 丢堆栈
log.error(e + "");                                  // 同样丢堆栈
```

**L3 · 禁止 `System.out` / `System.err` / `e.printStackTrace()`**
业务代码一律走 SLF4J 门面；`printStackTrace` 输出到 stderr，不进日志采集与告警系统。

**L4 · 敏感信息不进日志，用户输入不直接回显**
密码、API Key、token、身份证、完整手机号、密钥一律不入日志；用户输入引发的异常信息**只在服务端记录**，返回给客户端的是统一错误码与通用文案。
```java
// ✅
log.warn("模型调用失败, provider={}, apiKey={}", provider, mask(key));
// ❌
log.error("调用失败, apiKey=" + apiKey);
throw new BizException(ErrorCode.PARAM_INVALID, "参数 " + userInput + " 不合法");  // 直接回显用户输入
```

**L5 · 不要用 `if` 判断日志级别**
级别判断由日志框架自己做；用 `if` 包裹会跳过参数求值或绕开框架优化。
```java
// ✅
log.debug("检索结果={}", results);
// ❌
if (log.isDebugEnabled()) {
    log.debug("检索结果=" + expensiveToString(results));
}
```

---

## 四、并发（5 条）

**C1 · 禁止用 `Executors` 创建线程池**
`newFixedThreadPool` / `newSingleThreadExecutor` 用的是**无界队列**（任务堆积到 OOM）；`newCachedThreadPool` / `newScheduledThreadPool` 的最大线程数是 `Integer.MAX_VALUE`。
```java
// ✅
new ThreadPoolExecutor(
        8, 32, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256),
        new NamedThreadFactory("hify-llm"),
        new ThreadPoolExecutor.CallerRunsPolicy());
// ❌
Executors.newFixedThreadPool(10);
```

**C2 · 线程池必须显式指定全部 7 个参数，且线程必须命名**
核心线程数、最大线程数、空闲存活时间、时间单位、**有界**队列、**带业务名的**线程工厂、拒绝策略。
核心线程数按性质定：CPU 密集 ≈ `N+1`；IO 密集 ≈ `2N` 或更多（LLM / MCP 调用属 IO 密集）。
```java
// ✅
new ThreadPoolExecutor(8, 32, 60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256),
        new NamedThreadFactory("hify-llm"),          // 线程名带业务标识，jstack 可读
        new ThreadPoolExecutor.CallerRunsPolicy());
// ❌ 队列无界 / 线程无名 / 用默认拒绝策略
```

**C3 · `SimpleDateFormat` 非线程安全**
多线程共用会产出错乱日期。改用不可变的 `DateTimeFormatter` 或 `java.time`。
```java
// ✅
private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
// ❌
private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd");  // 多线程下数字错乱
```

**C4 · 共享可变状态必须同步；`volatile` 不保证原子性**
`volatile` 只保证可见性。`count++` 是复合操作，必须用 `AtomicInteger` / `LongAdder` 或加锁。锁必须在 `finally` 中释放。
```java
// ✅
private final AtomicLong requestCounter = new AtomicLong();
requestCounter.incrementAndGet();
// ❌
private volatile int count;
count++;                       // 竞态条件
lock.lock();
try { ... } finally { lock.unlock(); }
```

**C5 · 禁止 `new Thread()`；ThreadLocal 必须在 `finally` 清理**
异步逻辑走统一线程池。`ThreadLocal`（MDC traceId、当前用户）在线程池里会随线程复用串味，必须在任务结束时 `remove()`。
```java
// ✅
executor.submit(() -> {
    try {
        MDC.put("traceId", traceId);
        doWork();
    } finally {
        MDC.remove("traceId");     // 必须清理，否则线程复用会串 traceId
    }
});
// ❌
new Thread(() -> doWork()).start();
```

---

## 快速检查清单

新增或修改代码后逐条确认：

- [ ] 命名符合 N1–N5；布尔字段没有 `is` 前缀；没有魔法值
- [ ] 异常不用于流程控制；没有静默吞异常；重抛保留了 `cause`
- [ ] 资源用 try-with-resources；自定义异常继承 `RuntimeException` 且带错误码
- [ ] `@Transactional` 标了 `rollbackFor`；事务方法内没有慢调用
- [ ] 日志用占位符；异常带堆栈；没有 `System.out`；敏感信息已脱敏
- [ ] 线程池用 `ThreadPoolExecutor` 显式传 7 个参数，队列有界，线程有名字
- [ ] 没有 `SimpleDateFormat`；`volatile` 没有被当作原子操作；ThreadLocal 已清理
