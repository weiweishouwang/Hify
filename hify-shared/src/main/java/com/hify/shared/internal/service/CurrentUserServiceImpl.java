package com.hify.shared.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.context.CurrentUserHolder;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.internal.entity.UserDO;
import com.hify.shared.internal.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 当前调用者的解析。
 *
 * <p><b>一期没有登录接口</b>（CLAUDE.md §五：认证方式还没定），所以没有登录上下文时
 * 落到一个内置的<b>开发用户</b>上 —— 会话、消息这些数据总得有个归属，
 * 而 {@code conv_session.user_id} 是 NOT NULL 外键。
 *
 * <p>将来登录接上以后，只要登录拦截器往 {@link CurrentUserHolder} 里塞用户，
 * 这里一行都不用改。
 */
@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserServiceImpl.class);

    /** 没有登录态时使用的内置账号。 */
    public static final String DEVELOPMENT_USERNAME = "developer";

    private final UserMapper users;

    public CurrentUserServiceImpl(UserMapper users) {
        this.users = users;
    }

    @Override
    public Long currentUserId() {
        Long current = CurrentUserHolder.currentUserId();
        if (current != null) {
            return current;
        }
        return developmentUserId();
    }

    /**
     * 取（或建）开发用户的主键。
     *
     * <p>幂等：并发调用时可能同时插入，靠唯一约束兜住，撞了就再查一次。
     */
    private Long developmentUserId() {
        Long existing = users.findIdByUsername(DEVELOPMENT_USERNAME);
        if (existing != null) {
            return existing;
        }
        UserDO user = new UserDO();
        user.setPublicId(IdGenerator.nextUlid());
        user.setUsername(DEVELOPMENT_USERNAME);
        user.setNickname("开发用户");
        user.setIsAdmin(1);
        user.setStatus("active");
        try {
            users.insert(user);
            log.info("已创建内置开发用户 username={} id={}", DEVELOPMENT_USERNAME, user.getId());
            return user.getId();
        } catch (DuplicateKeyException exception) {
            Long owner = users.findIdByUsername(DEVELOPMENT_USERNAME);
            if (owner != null) {
                return owner;
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "创建内置开发用户失败", exception);
        }
    }

    /** 按登录名取用户；不存在时为 null。给将来的登录接口用。 */
    public UserDO findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return users.selectOne(new QueryWrapper<UserDO>().eq("username", username.trim()));
    }
}
