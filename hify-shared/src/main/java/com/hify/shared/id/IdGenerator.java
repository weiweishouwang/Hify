package com.hify.shared.id;

import java.security.SecureRandom;

/**
 * ULID 生成器。
 *
 * <p>ULID = 48 位毫秒时间戳 + 80 位随机数，用 Crockford Base32 编码成 26 个字符。
 * 与 UUID 相比有两个关键优势：
 * <ul>
 *   <li><b>字典序等于时间序</b>——按字符串排序就是按创建时间排序，游标分页可以直接用它</li>
 *   <li><b>紧凑</b>——26 字符（UUID 是 36），且索引更短</li>
 * </ul>
 *
 * <p>同一毫秒内多次调用时递增随机部分，保证严格单调（ULID 规范推荐的做法）。
 * 随机部分溢出时把时间戳推进 1 毫秒。系统时钟回拨时沿用上一次的时间戳，
 * 避免出现"后生成的 ID 排在前面"。
 *
 * <p>本类有静态可变状态（同一毫秒内的随机部分与时间戳），因此所有写入都在
 * {@link #LOCK} 上同步（见 CLAUDE.md §3.1 C4）。
 */
public final class IdGenerator {

    /** Crockford Base32 编码表，去掉了易混淆的 I、L、O、U 四个字母。 */
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /**
     * 反查表：由 {@link #ENCODING} 生成，保证编解码用同一张表。
     *
     * <p>不能用 {@code c - 'A' + 10} 之类的算式——Crockford 表跳过了 I、L、O、U，
     * 跳过的字母会让它之后所有字母的下标整体前移。
     */
    private static final int[] DECODING = new int[128];

    static {
        java.util.Arrays.fill(DECODING, -1);
        for (int i = 0; i < ENCODING.length; i++) {
            DECODING[ENCODING[i]] = i;
        }
    }

    /** ULID 字符串长度：26 字符 × 5 位 = 130 位。 */
    public static final int ULID_LENGTH = 26;

    /** 时间戳占用的字符数：10 × 5 = 50 位，其中高 2 位恒为 0，低 48 位是毫秒时间戳。 */
    private static final int TIMESTAMP_CHARS = 10;

    /** 随机部分占用的字节数：10 字节 = 80 位。 */
    private static final int RANDOMNESS_BYTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Object LOCK = new Object();

    /** 上一次生成时使用的时间戳，-1 表示尚未生成过。 */
    private static long lastTimestamp = -1L;

    /** 上一次生成时使用的随机部分。 */
    private static byte[] lastRandomness = new byte[RANDOMNESS_BYTES];

    private IdGenerator() {
    }

    /**
     * 生成下一个 ULID。
     *
     * @return 26 位 Crockford Base32 字符串
     */
    public static String nextUlid() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            // 时钟回拨时沿用上一次的时间戳，保证 ID 不倒退
            long timestamp = Math.max(now, lastTimestamp);
            byte[] randomness;

            if (timestamp > lastTimestamp) {
                // 新的一毫秒，随机部分重新生成
                randomness = new byte[RANDOMNESS_BYTES];
                RANDOM.nextBytes(randomness);
            } else {
                // 同一毫秒，递增随机部分以保证单调
                byte[] incremented = increment(lastRandomness);
                if (incremented == null) {
                    // 随机部分溢出，时间戳推进 1 毫秒
                    timestamp = lastTimestamp + 1;
                    randomness = new byte[RANDOMNESS_BYTES];
                    RANDOM.nextBytes(randomness);
                } else {
                    randomness = incremented;
                }
            }

            lastTimestamp = timestamp;
            lastRandomness = randomness;
            return encode(timestamp, randomness);
        }
    }

    /**
     * 解析 ULID 中的毫秒时间戳。
     *
     * <p>前 10 个字符是 50 位，其中高 2 位恒为 0、低 48 位是时间戳，因此解码后
     * 无需再移位。
     *
     * @param ulid ULID 字符串
     * @return 毫秒时间戳
     * @throws IllegalArgumentException ULID 格式非法时抛出
     */
    public static long decodeTimestamp(String ulid) {
        if (ulid == null || ulid.length() != ULID_LENGTH) {
            throw new IllegalArgumentException("ULID 长度必须为 " + ULID_LENGTH + "，实际: " + ulid);
        }
        // 全部 26 个字符都要合法，虽然只有前 10 个参与时间戳解码
        for (int i = 0; i < ULID_LENGTH; i++) {
            if (decodeChar(ulid.charAt(i)) < 0) {
                throw new IllegalArgumentException("ULID 含非法字符: " + ulid);
            }
        }
        long value = 0;
        for (int i = 0; i < TIMESTAMP_CHARS; i++) {
            value = (value << 5) | decodeChar(ulid.charAt(i));
        }
        return value;
    }

    /** 把时间戳与随机部分编码成 26 个字符。 */
    private static String encode(long timestamp, byte[] randomness) {
        char[] out = new char[ULID_LENGTH];

        // 前 10 个字符：50 位 = [2 个 0][48 位时间戳]，最高位在最左
        long ts = timestamp;
        for (int i = TIMESTAMP_CHARS - 1; i >= 0; i--) {
            out[i] = ENCODING[(int) (ts & 0x1F)];
            ts >>>= 5;
        }

        // 后 16 个字符：80 位随机数，最高位同样在最左（与时间戳部分保持一致的位序）
        int bitPos = 0;
        for (int i = TIMESTAMP_CHARS; i < ULID_LENGTH; i++) {
            int value = 0;
            for (int b = 0; b < 5; b++) {
                value = (value << 1) | bitAt(randomness, bitPos++);
            }
            out[i] = ENCODING[value];
        }

        return new String(out);
    }

    /** 大端递增；溢出返回 null。 */
    private static byte[] increment(byte[] value) {
        byte[] result = value.clone();
        for (int i = result.length - 1; i >= 0; i--) {
            int next = (result[i] & 0xFF) + 1;
            if (next > 0xFF) {
                result[i] = 0;
            } else {
                result[i] = (byte) next;
                return result;
            }
        }
        return null;
    }

    /** 取字节数组的第 index 位（高位在前），越界返回 0。 */
    private static int bitAt(byte[] bytes, int index) {
        if (index >= bytes.length * 8) {
            return 0;
        }
        return (bytes[index >> 3] >> (7 - (index & 7))) & 1;
    }

    /** Crockford Base32 字符解码；非法字符返回 -1。 */
    private static int decodeChar(char c) {
        return c < DECODING.length ? DECODING[c] : -1;
    }
}
