package com.hify.knowledge.internal.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 向量字面量的拼装 —— PG 的 {@code vector} 类型靠它传参。 */
class VecChunkRepositoryTest {

    @Test
    @DisplayName("向量转成 pgvector 的字面量形式")
    void formatsVectorLiteral() {
        assertThat(VecChunkRepository.toVectorLiteral(new float[] {1f, 2.5f, -3f}))
                .isEqualTo("[1.0,2.5,-3.0]");
    }

    @Test
    @DisplayName("空向量直接拒绝")
    void rejectsEmptyVector() {
        assertThatThrownBy(() -> VecChunkRepository.toVectorLiteral(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VecChunkRepository.toVectorLiteral(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("科学计数法也能被 pgvector 解析")
    void formatsSmallNumbers() {
        assertThat(VecChunkRepository.toVectorLiteral(new float[] {1e-5f, 2e5f}))
                .isEqualTo("[1.0E-5,200000.0]");
    }
}
