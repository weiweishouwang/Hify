package com.hify.demo.internal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.demo.internal.entity.DemoItemDO;

/**
 * 演示条目的 Mapper。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper} 就有单表 CRUD 了，演示模块不写
 * 自定义 SQL。需要写复杂查询时在这里加方法 + 配套的 XML（放在
 * {@code resources/mapper/} 下）。
 *
 * <p>Mapper 只能在 {@code internal} 里用，别的模块要取数必须走
 * {@code api} 包的接口（CLAUDE.md §3.2，ArchUnit 会拦）。
 */
public interface DemoItemMapper extends BaseMapper<DemoItemDO> {
}
