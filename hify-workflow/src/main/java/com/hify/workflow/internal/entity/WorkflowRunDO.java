package com.hify.workflow.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流运行记录的数据对象。
 *
 * <p><b>不继承 {@link com.hify.shared.mybatis.BaseEntity}</b>：事件流表，
 * 主键是 {@code (id, created_at)} 复合主键，也没有 {@code deleted} 列 ——
 * 继承基类会让 MyBatis-Plus 给每条 SQL 追加 {@code deleted = 0}，直接语法错误。
 */
@TableName(value = "wf_run", autoResultMap = true)
public class WorkflowRunDO {

    /** 自增主键；与 created_at 组成复合主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工作流 wf_definition.id。 */
    @TableField("definition_id")
    private Long definitionId;

    /** 状态：running / succeeded / failed。 */
    @TableField("status")
    private String status;

    /** 入参。 */
    @TableField(value = "input", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> input;

    /** 出参。 */
    @TableField(value = "output", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> output;

    /** 执行了几个节点。 */
    @TableField("step_count")
    private Integer stepCount;

    /** 失败原因。 */
    @TableField("error_message")
    private String errorMessage;

    /** 总耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 创建时间(UTC) —— 分区键。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public Integer getStepCount() {
        return stepCount;
    }

    public void setStepCount(Integer stepCount) {
        this.stepCount = stepCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
