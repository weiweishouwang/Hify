package com.hify.knowledge.internal.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定分块：按字符数切分。
 *
 * <p>一期只做固定分块（CLAUDE.md §1.2 明确「只支持 TXT 上传 + 固定分块」）——
 * 不按段落或句子边界切。代价是可能把句子切碎，检索质量打折；这是降级定位的既定代价。
 */
@Component
public class TextChunker {

    /**
     * 把文本切成带重叠的块。
     *
     * @param text     原文
     * @param size     块大小，单位字符
     * @param overlap  相邻块的重叠字符数
     * @return 块的正文列表，顺序即原文顺序
     */
    public List<String> split(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty() || size <= 0) {
            return chunks;
        }
        int step = Math.max(1, size - Math.max(0, overlap));
        int length = text.length();
        for (int start = 0; start < length; start += step) {
            int end = Math.min(length, start + size);
            chunks.add(text.substring(start, end));
            if (end >= length) {
                break;
            }
        }
        return chunks;
    }
}
