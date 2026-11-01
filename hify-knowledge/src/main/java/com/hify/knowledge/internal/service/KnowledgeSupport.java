package com.hify.knowledge.internal.service;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 知识库与文档的常量、状态机与校验。 */
public final class KnowledgeSupport {

    /**
     * 向量维度。
     *
     * <p>{@code vec_chunk.embedding} 是 {@code vector(1536)}，建表时定死 ——
     * PG 不支持 ALTER 改 vector 维度，换维度必须重建整张表。
     */
    public static final int EMBEDDING_DIMENSION = 1536;

    /** 默认分块大小，单位字符。 */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认分块重叠，单位字符。 */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    /** 知识库状态：可用。 */
    public static final String KB_ACTIVE = "active";

    /** 知识库状态：停用。 */
    public static final String KB_DISABLED = "disabled";

    /** 文档状态：原文已落库，尚未提交索引。 */
    public static final String DOC_UPLOADED = "uploaded";

    /** 文档状态：正在分块、向量化、写 PG。 */
    public static final String DOC_INDEXING = "indexing";

    /** 文档状态：可检索。 */
    public static final String DOC_READY = "ready";

    /** 文档状态：索引失败。 */
    public static final String DOC_FAILED = "failed";

    /** 文档状态：已软删，等 PG 清完。 */
    public static final String DOC_DELETING = "deleting";

    /** 一期只支持 TXT。 */
    public static final String FILE_TYPE_TXT = "txt";

    /** 单个文档的大小上限：10 MB。 */
    public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    /** 默认返回条数。 */
    public static final int DEFAULT_TOP_K = 5;

    /** 返回条数上限。 */
    public static final int MAX_TOP_K = 20;

    private KnowledgeSupport() {
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 名称。 */
    public static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("名称不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("名称最长 128 字符");
        }
        return trimmed;
    }

    /**
     * 分块参数。
     *
     * @param chunkSize    块大小
     * @param chunkOverlap 重叠
     * @return 归一化后的 {@code [块大小, 重叠]}
     */
    public static int[] chunkParams(Integer chunkSize, Integer chunkOverlap) {
        int size = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
        int overlap = chunkOverlap == null ? DEFAULT_CHUNK_OVERLAP : chunkOverlap;
        if (size < 100 || size > 4000) {
            throw invalid("分块大小要在 100 到 4000 字符之间");
        }
        if (overlap < 0 || overlap >= size) {
            throw invalid("分块重叠必须小于分块大小");
        }
        return new int[] { size, overlap };
    }

    /** 状态：active / disabled。 */
    public static String kbStatus(String value) {
        String status = value == null || value.isBlank() ? KB_ACTIVE : value.trim();
        if (!KB_ACTIVE.equals(status) && !KB_DISABLED.equals(status)) {
            throw invalid("状态只支持 active 或 disabled");
        }
        return status;
    }

    /** 返回条数。 */
    public static int topK(Integer value) {
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        if (value < 1 || value > MAX_TOP_K) {
            throw invalid("返回条数要在 1 到 " + MAX_TOP_K + " 之间");
        }
        return value;
    }

    /** 内容的 SHA-256，用于去重。 */
    public static String contentHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "计算内容摘要失败", exception);
        }
    }
}
