package com.hify.shared.mybatis;

/**
 * 已被 {@link BaseEntity} 取代。
 *
 * <p>本类原本承载主键与审计字段；现在这些字段都在 {@link BaseEntity} 里
 * （并补上了逻辑删除标记 {@code deleted}），所以它只剩一个空壳。
 *
 * <p>保留它只是为了兼容既有引用，<b>新的业务实体统一继承 {@link BaseEntity}</b>。
 * 确认没有引用之后可以直接删掉本文件。
 *
 * @deprecated 用 {@link BaseEntity}
 */
@Deprecated(since = "0.1.0", forRemoval = true)
public class BaseDO extends BaseEntity {
}
