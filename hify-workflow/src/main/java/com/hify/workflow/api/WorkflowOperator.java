package com.hify.workflow.api;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/**
 * If-Else 节点的比较运算符。
 *
 * <p><b>显式的三段式，不引表达式语言</b> —— 条件判断是工作流里最容易出错的地方，
 * 读 JSON 就知道会怎么判，比表达式字符串好排查得多。
 */
public enum WorkflowOperator {

    /** 相等。 */
    EQUALS("equals"),

    /** 不相等。 */
    NOT_EQUALS("not_equals"),

    /** 包含（左侧是字符串时按子串判，否则按集合包含判）。 */
    CONTAINS("contains"),

    /** 不包含。 */
    NOT_CONTAINS("not_contains"),

    /** 为空（null 或空串）。 */
    IS_EMPTY("is_empty"),

    /** 不为空。 */
    NOT_EMPTY("not_empty"),

    /** 大于（两侧都按数字解析）。 */
    GT("gt"),

    /** 小于（两侧都按数字解析）。 */
    LT("lt");

    private final String code;

    WorkflowOperator(String code) {
        this.code = code;
    }

    /** 存库与传输用的字面量。 */
    public String code() {
        return code;
    }

    /**
     * 按字面量解析运算符。
     *
     * @param code 运算符字面量
     * @return 对应的枚举
     * @throws BizException 未知运算符时抛出
     */
    public static WorkflowOperator fromCode(String code) {
        for (WorkflowOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的条件运算符: " + code);
    }
}
