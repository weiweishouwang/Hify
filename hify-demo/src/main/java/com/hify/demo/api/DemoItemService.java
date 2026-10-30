package com.hify.demo.api;

import com.hify.shared.web.PageResult;

/**
 * 演示条目的业务接口。
 *
 * <p>接口放在 {@code api} 包——这是模块对外唯一的入口，其他模块只能依赖这里，
 * 不得碰 {@code internal} 里的实现（CLAUDE.md §3.2，ArchUnit 会拦）。
 *
 * <p>方法签名「远程调用友好」：参数与返回值全是 DTO、没有重载、没有受检异常、
 * 不抛业务异常——所以将来拆成独立服务时，这层能原样变成 RPC 契约。
 */
public interface DemoItemService {

    /**
     * 分页查询。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，最大 100
     * @return 分页结果
     */
    PageResult<DemoItemResp> list(int page, int size);

    /**
     * 按 id 查询单条。
     *
     * @param id 主键
     * @return 演示条目
     */
    DemoItemResp get(Long id);

    /**
     * 新建。
     *
     * @param req 创建请求
     * @return 新建后的条目（含自动填充的主键与时间）
     */
    DemoItemResp create(DemoItemCreateReq req);

    /**
     * 整体更新。
     *
     * @param id  主键
     * @param req 更新请求
     * @return 更新后的条目
     */
    DemoItemResp update(Long id, DemoItemUpdateReq req);

    /**
     * 删除（逻辑删除）。
     *
     * @param id 主键
     */
    void delete(Long id);
}
