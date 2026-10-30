package com.hify.demo.internal.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.demo.api.DemoItemCreateReq;
import com.hify.demo.api.DemoItemResp;
import com.hify.demo.api.DemoItemService;
import com.hify.demo.api.DemoItemUpdateReq;
import com.hify.demo.internal.converter.DemoItemConverter;
import com.hify.demo.internal.entity.DemoItemDO;
import com.hify.demo.internal.mapper.DemoItemMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.mybatis.PageHelper;
import com.hify.shared.web.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示条目的业务实现。
 *
 * <p>业务规则都在这一层：查不到就抛 {@link BizException}（统一错误码，不写死数字），
 * 增删改落库，DO → DTO 的转换交给 converter。
 *
 * <p>事务只包住数据库操作——**事务方法里绝不能调模型、MCP、工具这些慢能力**，
 * 它们会长时间占着数据库连接（CLAUDE.md §E5）。
 */
@Service
public class DemoItemServiceImpl implements DemoItemService {

    private final DemoItemMapper mapper;

    public DemoItemServiceImpl(DemoItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<DemoItemResp> list(int page, int size) {
        Page<DemoItemDO> pageRequest = PageHelper.toPage(page, size);
        IPage<DemoItemDO> result = mapper.selectPage(pageRequest,
                Wrappers.<DemoItemDO>query().orderByDesc("id"));
        return PageHelper.toPageResult(result, DemoItemConverter::toResp);
    }

    @Override
    public DemoItemResp get(Long id) {
        return DemoItemConverter.toResp(requireExisting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DemoItemResp create(DemoItemCreateReq req) {
        DemoItemDO entity = new DemoItemDO();
        entity.setName(req.getName());
        entity.setStatus(req.getStatus());
        mapper.insert(entity);
        // 主键与审计字段是 MyBatis-Plus 插入时回填的，这里直接转成响应
        return DemoItemConverter.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DemoItemResp update(Long id, DemoItemUpdateReq req) {
        DemoItemDO entity = requireExisting(id);
        entity.setName(req.getName());
        entity.setStatus(req.getStatus());
        mapper.updateById(entity);
        return DemoItemConverter.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 逻辑删除：MyBatis-Plus 的 @TableLogic 会把 deleteById 改写成
        // UPDATE SET deleted = 1，不会真的删行
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "演示条目不存在: " + id);
        }
    }

    /** 取一条存在的记录，取不到就抛 NOT_FOUND。 */
    private DemoItemDO requireExisting(Long id) {
        DemoItemDO entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "演示条目不存在: " + id);
        }
        return entity;
    }
}
