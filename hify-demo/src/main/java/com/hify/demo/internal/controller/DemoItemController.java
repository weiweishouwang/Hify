package com.hify.demo.internal.controller;

import com.hify.demo.api.DemoItemCreateReq;
import com.hify.demo.api.DemoItemResp;
import com.hify.demo.api.DemoItemService;
import com.hify.demo.api.DemoItemUpdateReq;
import com.hify.shared.web.PageResult;
import com.hify.shared.web.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示条目的 HTTP 接口。
 *
 * <p>这一层只做三件事：绑定参数、校验、调 service、返回结果。
 * **不写业务逻辑、不开事务、不碰 Mapper**（CLAUDE.md §2.2）。
 *
 * <p>路径用 RESTful 风格：集合用复数名词，单条用 {@code /{id}}，
 * 增删改用对应动词方法（POST / PUT / DELETE）。
 */
@RestController
@RequestMapping("/api/v1/demo-items")
public class DemoItemController {

    private final DemoItemService demoItemService;

    public DemoItemController(DemoItemService demoItemService) {
        this.demoItemService = demoItemService;
    }

    /**
     * 分页列表。
     *
     * <p>返回 {@link PageResult} 本身，**不要再套一层 {@code Result}**——
     * {@code PageResult} 已经继承了 {@code Result}，套两遍响应里会出现两层
     * {@code code/message/data}（前端 {@code PageResult} 类型对不上）。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，最大 100
     * @return 分页结果
     */
    @GetMapping
    public PageResult<DemoItemResp> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return demoItemService.list(page, size);
    }

    /**
     * 查询单条。
     *
     * @param id 主键
     * @return 演示条目
     */
    @GetMapping("/{id}")
    public Result<DemoItemResp> get(@PathVariable Long id) {
        return Result.ok(demoItemService.get(id));
    }

    /**
     * 新建。
     *
     * @param req 创建请求，@Valid 校验不通过会由 GlobalExceptionHandler 统一转成错误响应
     * @return 新建后的条目
     */
    @PostMapping
    public Result<DemoItemResp> create(@Valid @RequestBody DemoItemCreateReq req) {
        return Result.ok(demoItemService.create(req));
    }

    /**
     * 整体更新。
     *
     * @param id  主键
     * @param req 更新请求
     * @return 更新后的条目
     */
    @PutMapping("/{id}")
    public Result<DemoItemResp> update(@PathVariable Long id,
                                       @Valid @RequestBody DemoItemUpdateReq req) {
        return Result.ok(demoItemService.update(id, req));
    }

    /**
     * 删除（逻辑删除）。
     *
     * @param id 主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        demoItemService.delete(id);
        return Result.ok();
    }
}
