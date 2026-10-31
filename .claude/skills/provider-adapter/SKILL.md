---
name: provider-adapter
description: Use when adding a new LLM vendor or protocol to Hify's hify-ai module — writing a ProviderAdapter, registering a new provider.type, or wiring a vendor's models/chat API into the 管理台
---

# 新增 LLM 供应商适配器

## 概述

Hify 的协议差异收敛在 `hify-ai/internal/spi/` 的策略模式里。调用方（`ProviderServiceImpl`、`ProviderHealthScheduler`、`ProviderController`）**永远按 `provider.type` 走工厂**，没有任何 if-else 分发。

**核心原则：适配器只管协议，注册点必须全部登记。** 漏掉一个注册点不会编译报错，症状是「保存报 400」「保存成功但连通性测试永远失败」「管理台下拉里选不到」——排查成本远高于写适配器本身。

## 什么时候用

- 接入新的 LLM 厂商**原生** API（不是 OpenAI 兼容层）
- 给现有协议加一个新变体（Azure OpenAI、Ollama 云端等）

**不适用**：只是新增一个 OpenAI 兼容端点（DeepSeek、Qwen、Kimi、vLLM、LM Studio…）。那不需要写适配器 —— `type=openai` 换个 base_url 就行，协议完全一致。

## 四步流程

### 1. 分析 API

先查清四件事，缺一项就会在第 4 步翻车：

| 要查的 | 注意事项 |
|---|---|
| 协议根地址 | 用户在管理台填什么。**不能带查询串**（`?key=xxx` 会被 `ProviderConfigSupport.baseUrl()` 拒绝），密钥走请求头 |
| 目录端点 | 路径、请求方法、鉴权头名称（如 `x-api-key` / `x-goog-api-key` / `Authorization: Bearer`） |
| 响应结构 | 模型数组在哪个字段、模型标识在哪个字段 |
| 标识是否要归一化 | Gemini 返回 `models/gemini-2.5-pro`，但调用路径要 `gemini-2.5-pro` —— 归一化放在适配器里，别把上游原样存进 `ai_model.model_name` |

### 2. 实现 Adapter

继承 `AbstractCatalogAdapter`（同包，包级私有基类），只覆写这几个钩子：

| 钩子 | 含义 |
|---|---|
| `type()` | 协议档位常量 |
| `catalogPath()` | 目录路径，拼在协议根地址之后 |
| `catalogField()` | 响应里模型数组的字段名 |
| `modelIdField()` | 数组元素里模型标识的字段名 |
| `authHeaders(provider)` | 鉴权请求头；不需要鉴权就不覆写 |

```java
@Component
public class GeminiAdapter extends AbstractCatalogAdapter {

    public GeminiAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override public String type() { return ProviderConfigSupport.GEMINI; }
    @Override protected String catalogPath() { return "/models"; }
    @Override protected String catalogField() { return "models"; }
    @Override protected String modelIdField() { return "name"; }

    /** 云端接口必须有 Key，缺失即配置不完整。 */
    @Override
    protected Map<String, String> authHeaders(ModelProviderDO provider) {
        String key = decryptKey(authConfig(provider).get(ProviderConfigSupport.API_KEY));
        if (key == null) {
            throw ProviderConfigSupport.invalid("Gemini 鉴权配置不完整");
        }
        return Map.of("x-goog-api-key", key);
    }
}
```

协议与已有实现只有路径/鉴权/字段名不同 → 直接继承对应适配器（如 `OpenAiCompatibleAdapter`），不要复制代码。只有需要标识归一化时才覆写 `listModels`。

**先写测试再写实现**（项目用 TDD）：在 `hify-ai/src/test/java/com/hify/ai/internal/spi/ProviderAdapterTest.java` 里加用例，用 `MockWebServer` 断言路径、请求头、缺 Key 被拒、非法响应不算健康。

### 3. 注册到 Factory —— 最容易漏的一步

协议字面量散落在后端 6 处、前端 3 处。**逐项登记，漏一个症状不同**：

| # | 文件 | 改什么 | 漏了会怎样 |
|---|---|---|---|
| 1 | `internal/spi/ProviderAdapterFactory.java` | 构造注入 + `adapters.put(...)` | 工厂抛「不支持的接入点协议」 |
| 2 | `internal/service/ProviderConfigSupport.java` | 加常量 + **私有 `TYPES` 集合** | 创建/更新被拒「不支持的接入点协议」 |
| 3 | `internal/security/ProviderAuthConfig.java` | 凭据字段白名单 | **带 Key 保存被拒 → 连通性测试永远失败** |
| 4 | `api/ProviderCreateReq.java` | `PROTOCOL_PATTERN` + 报错文案 | HTTP 400 |
| 5 | `api/ProviderListQuery.java` | 报错文案（正则共用第 4 项的） | HTTP 400 |
| 6 | `internal/converter/ProviderConverter.java` | 回显的非敏感鉴权字段白名单 | 界面看不到该协议的公开参数 |
| 7 | `internal/entity/ModelProviderDO.java` | javadoc 注释 | 无（仅可读性） |
| 8 | `hify-app/.../db/schema.sql` | `type` 列 COMMENT | 无（仅可读性） |
| 9 | `hify-web/src/types/provider.ts` | `ProviderType` 联合类型 | 前端类型不匹配 |
| 10 | `hify-web/src/views/provider/ProviderList.vue` | 下拉选项、Base URL 提示、Key 必填校验、文案 | 管理台选不到该协议 |
| 11 | `hify-web/src/views/provider/providerForm.ts` | 仅当协议有特有字段才需要 | 该协议的额外参数发不出去 |

第 3 项最容易漏：它是「按协议列出的凭据字段白名单」，不在其中意味着提交的 `apiKey` 被判为非法字段。

**数据库不用改列** —— `type` 是 `VARCHAR(32)`，没有 ENUM / CHECK 约束（CLAUDE.md §3.3）。已有库要改列注释时另写迁移脚本，放 `hify-app/src/main/resources/db/migrations/`。

### 4. 验证

```bash
mvn -pl hify-ai -am test && mvn verify
cd hify-web && npm test && npm run build
```

必查的测试改动：

- `ProviderAdapterTest` —— 工厂是手工 `new` 的，**构造参数变了要同步改**，否则编译不过；里面断言「某协议不受支持」的负例，**只有当新协议恰好用了同一个名字时**（如新档位叫 `gemini`）才要把那条反过来，否则保留负例并新增一条正例断言
- `ProviderAuthConfigTest` —— 新协议的凭据白名单与加密封套
- `ProviderControllerTest` —— REST 边界的 type 校验
- 前端 `provider.test.ts` / `ProviderList.test.ts` —— 提交载荷的凭据动作

## 常见错误

| 错误 | 症状 | 正确做法 |
|---|---|---|
| 在 service 里加 if-else | 分发散落各处 | 一律 `factory.getAdapter(type)` |
| 漏改凭据白名单 | 保存报「包含当前协议不支持的字段」 | 改 `ProviderAuthConfig` |
| 漏改 `PROTOCOL_PATTERN` | HTTP 400 | 改 `ProviderCreateReq` |
| 把 URL 写进模板属性 | vue-tsc 报「找不到 xxx」假错 | URL 放脚本常量再绑定（CLAUDE.md §3.6） |
| 上游标识原样存进 `model_name` | 调用时 404 | 在适配器里归一化 |
| Key 必填校验写死协议名 | 切换协议时放过没填 Key 的表单 | 判断写成 `original.type === form.type` |

## 不要动的地方

`ProviderServiceImpl`、`ProviderController`、`ProviderHealthScheduler`、`ProviderConfigQueryService`、`ProviderConverter` 的转换逻辑、`hify-web/src/api/provider.ts` —— 全部协议无关，不要因为新增协议去改它们。
