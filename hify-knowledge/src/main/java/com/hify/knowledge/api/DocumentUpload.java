package com.hify.knowledge.api;

import java.nio.charset.StandardCharsets;

/**
 * 上传文档的内容。
 *
 * <p><b>刻意不带 {@code MultipartFile}</b> —— 契约层不出现框架类型，
 * 跨模块签名要「远程调用友好」（CLAUDE.md §3.2）。HTTP 入口负责把
 * {@code MultipartFile} 转成它。
 *
 * @param fileName    文件名
 * @param contentType MIME 类型
 * @param content     文件字节
 */
public record DocumentUpload(String fileName, String contentType, byte[] content) {

    public DocumentUpload {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
    }

    /** 文件名去掉路径部分，只留末段。 */
    public String safeFileName() {
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** 截断到 255 字符的展示名。 */
    public String displayName() {
        String safe = safeFileName();
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    /**
     * 判断是不是 TXT —— 一期只支持 TXT。
     *
     * <p><b>只看扩展名，不看 MIME</b>：浏览器对 .txt 常发 {@code application/octet-stream}，
     * 对 .pdf 也可能发 {@code text/plain} —— 拿 MIME 当判据会放进二进制文件。
     * 扩展名是用户看得见、也真正决定格式的那个。
     */
    public boolean isTxt() {
        return safeFileName().toLowerCase().endsWith(".txt");
    }

    /** 按 UTF-8 读取文本内容。 */
    public String text() {
        return new String(content, StandardCharsets.UTF_8);
    }

    /** 空内容判断。 */
    public boolean isEmpty() {
        return content.length == 0;
    }
}
