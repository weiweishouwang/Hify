package com.hify.workflow.api;

import java.util.Map;

/**
 * 工作流节点。
 *
 * <p><b>六种节点共用一个类</b>，每种只有自己的那几���字段有意义 ——
 * JSON DSL 本来就是「一个大对象」，拆成六个类要配 Jackson 的多态序列化，
 * 收益不抵复杂度。校验时按 {@code type} 检查该类型的必填字段。
 */
public class WorkflowNode {

    /** 节点标识，在定义内唯一。 */
    private String id;

    /** 节点类型，见 {@link WorkflowNodeType}。 */
    private String type;

    /** 节点名，仅用于展示与报错。 */
    private String name;

    /** 输出变量名；下游节点用 {@code {{变量名}}} 引用。 */
    private String output;

    // ── llm 节点 ──

    /** 模型 publicId；不给就用定义顶层的默认模型。 */
    private String model;

    /** 提示词模板。 */
    private String prompt;

    /** 生成参数：temperature / max_tokens / top_p。 */
    private Map<String, Object> params;

    // ── agent 节点 ──

    /** Agent 的 publicId。 */
    private String agent;

    /** 传给 Agent 的输入文本模板。 */
    private String input;

    // ── tool 节点 ──

    /** 工具的 publicId。 */
    private String tool;

    /** 工具入参模板，值可以是 {@code {{变量名}}}。 */
    private Map<String, Object> args;

    // ── knowledge 节点 ──

    /** 知识库的 publicId。 */
    private String knowledgeBase;

    /** 检索文本模板。 */
    private String query;

    /** 检索条数。 */
    private Integer topK;

    // ── if 节点 ──

    /** 左值模板。 */
    private String left;

    /** 比较运算符，见 {@link WorkflowOperator}。 */
    private String operator;

    /** 右值模板。 */
    private String right;

    /** 条件成立时走的节点 id。 */
    private String then;

    /** 条件不成立时走的节点 id。 */
    private String otherwise;

    // ── output 节点 ──

    /** 产出值模板。 */
    private String value;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(String knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
    }

    public String getThen() {
        return then;
    }

    public void setThen(String then) {
        this.then = then;
    }

    public String getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(String otherwise) {
        this.otherwise = otherwise;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
