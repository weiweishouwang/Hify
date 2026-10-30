package com.hify.demo.internal.converter;

import com.hify.demo.api.DemoItemResp;
import com.hify.demo.internal.entity.DemoItemDO;

/**
 * 演示条目的 DO ↔ DTO 转换。
 *
 * <p>转换逻辑单独放一个类，不堆在 service 里——service 混着写转换后会越长越难读
 * （CLAUDE.md §2.2）。
 */
public final class DemoItemConverter {

    private DemoItemConverter() {
    }

    /**
     * DO 转响应 DTO。
     *
     * @param entity 数据对象
     * @return 响应对象；入参为 null 时返回 null
     */
    public static DemoItemResp toResp(DemoItemDO entity) {
        if (entity == null) {
            return null;
        }
        DemoItemResp resp = new DemoItemResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setStatus(entity.getStatus());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
