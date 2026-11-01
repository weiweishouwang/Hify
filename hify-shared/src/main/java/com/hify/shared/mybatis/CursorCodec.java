package com.hify.shared.mybatis;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 游标分页的游标编解码。
 *
 * <p>游标是 {@code created_at|id} 的 Base64（URL 安全的无填充变体）。
 * 排序键恒为 {@code (created_at, id)}，用行值比较 {@code (a,b) < (x,y)}
 * 定位下一页 —— 不用 OFFSET，深分页不会退化（CLAUDE.md §3.3）。
 *
 * <p>放在 shared 是因为三个模块（接入点、模型、工具、知识库）都要用同一套游标语义 ——
 * 各写一份迟早会分叉。
 */
public final class CursorCodec {

    /** 游标最大长度，防止超长串进 Base64 解码。 */
    private static final int MAX_CURSOR_LENGTH = 512;

    private CursorCodec() {
    }

    /**
     * 编码一行作为游标。
     *
     * @param createdAt 排序用的创建时间
     * @param id        主键
     * @return 游标字符串
     */
    public static String encode(LocalDateTime createdAt, long id) {
        String value = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码游标。
     *
     * @param value 游标字符串
     * @return 游标指向的排序位置
     * @throws BizException 游标格式不合法时抛出
     */
    public static Cursor decode(String value) {
        try {
            if (value == null || value.length() > MAX_CURSOR_LENGTH) {
                throw new IllegalArgumentException("游标过长");
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                    .split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("游标不完整");
            }
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new IllegalArgumentException("游标主键无效");
            }
            return new Cursor(LocalDateTime.parse(parts[0]), id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "列表游标格式不合法", exception);
        }
    }

    /**
     * 游标指向的排序位置。
     *
     * @param createdAt 创建时间
     * @param id        主键
     */
    public record Cursor(LocalDateTime createdAt, long id) {
    }
}
