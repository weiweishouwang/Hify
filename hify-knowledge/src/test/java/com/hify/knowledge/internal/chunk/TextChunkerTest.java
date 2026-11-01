package com.hify.knowledge.internal.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 固定分块的切分规则。 */
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    @DisplayName("按块大小切分，块数向上取整")
    void splitsBySize() {
        var chunks = chunker.split("0123456789", 4, 0);
        assertThat(chunks).containsExactly("0123", "4567", "89");
    }

    @Test
    @DisplayName("相邻块按重叠字符数交叠")
    void overlapsAdjacentChunks() {
        var chunks = chunker.split("0123456789", 4, 2);
        assertThat(chunks).containsExactly("0123", "2345", "4567", "6789");
    }

    @Test
    @DisplayName("块大小与重叠相等时也要推进，不死循环")
    void equalSizeAndOverlapStillAdvances() {
        var chunks = chunker.split("0123456789", 4, 4);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).isEqualTo("0123");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(4));
        assertThat(chunks.get(chunks.size() - 1)).endsWith("89");
    }

    @Test
    @DisplayName("空文本不产生分块")
    void emptyTextProducesNoChunks() {
        assertThat(chunker.split("", 500, 50)).isEmpty();
        assertThat(chunker.split(null, 500, 50)).isEmpty();
    }

    @Test
    @DisplayName("文本短于一块时只有一块")
    void shortTextProducesOneChunk() {
        assertThat(chunker.split("短", 500, 50)).containsExactly("短");
    }

    @Test
    @DisplayName("切分后的内容拼回去等于原文（去掉重叠部分）")
    void chunksCoverOriginal() {
        String text = "Hify 是一个内部 AI Agent 平台。知识库只支持 TXT 上传与固定分块。";
        var chunks = chunker.split(text, 10, 3);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).isEqualTo(text.substring(0, 10));
    }
}
