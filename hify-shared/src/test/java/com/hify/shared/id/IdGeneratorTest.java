package com.hify.shared.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link IdGenerator} 的单元测试。 */
class IdGeneratorTest {

    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    @DisplayName("生成 26 位字符串")
    void 长度固定() {
        assertThat(IdGenerator.nextUlid()).hasSize(IdGenerator.ULID_LENGTH);
    }

    @Test
    @DisplayName("只使用 Crockford Base32 字符集")
    void 字符集合法() {
        String ulid = IdGenerator.nextUlid();

        for (char c : ulid.toCharArray()) {
            assertThat(CROCKFORD_BASE32.indexOf(c)).as("非法字符: %c", c).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("大量生成不重复")
    void 不重复() {
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            generated.add(IdGenerator.nextUlid());
        }

        assertThat(generated).hasSize(10_000);
    }

    @Test
    @DisplayName("时间戳可解码，且接近生成时刻")
    void 时间戳可解码() {
        long before = System.currentTimeMillis();
        String ulid = IdGenerator.nextUlid();
        long after = System.currentTimeMillis();

        long decoded = IdGenerator.decodeTimestamp(ulid);

        assertThat(decoded).isBetween(before, after);
    }

    @Test
    @DisplayName("连续生成的 ID 字典序严格递增（同一毫秒内也成立）")
    void 字典序单调递增() {
        String previous = IdGenerator.nextUlid();

        for (int i = 0; i < 1000; i++) {
            String current = IdGenerator.nextUlid();
            assertThat(current).as("第 %d 个 ID 未递增", i).isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    @DisplayName("时间戳部分相同时靠随机部分保证有序")
    void 同一毫秒内仍然递增() {
        String first = IdGenerator.nextUlid();
        String second = IdGenerator.nextUlid();

        assertThat(IdGenerator.decodeTimestamp(first))
                .isEqualTo(IdGenerator.decodeTimestamp(second));
        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("解码非法格式时抛异常")
    void 非法格式报错() {
        assertThatThrownBy(() -> IdGenerator.decodeTimestamp(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdGenerator.decodeTimestamp("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdGenerator.decodeTimestamp("0000000000000000000000000I"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
