package com.hify.shared.context;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link CurrentUserHolder} 的单元测试。 */
class CurrentUserHolderTest {

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("设置后可取回当前用户")
    void 设置与取回() {
        CurrentUser user = CurrentUser.ofUser(1L, "张三", true);
        CurrentUserHolder.set(user);

        assertThat(CurrentUserHolder.get()).isEqualTo(user);
        assertThat(CurrentUserHolder.currentUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("未设置时 get 返回 null")
    void 未设置返回null() {
        assertThat(CurrentUserHolder.get()).isNull();
        assertThat(CurrentUserHolder.currentUserId()).isNull();
    }

    @Test
    @DisplayName("未设置时 require 抛 UNAUTHORIZED")
    void 未设置时require抛异常() {
        assertThatThrownBy(CurrentUserHolder::require)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("设置后 require 返回该用户")
    void 设置后require返回用户() {
        CurrentUser user = CurrentUser.ofUser(1L, "张三", false);
        CurrentUserHolder.set(user);

        assertThat(CurrentUserHolder.require()).isEqualTo(user);
    }

    @Test
    @DisplayName("clear 后上下文被清空")
    void clear清空() {
        CurrentUserHolder.set(CurrentUser.ofUser(1L, "张三", false));
        CurrentUserHolder.clear();

        assertThat(CurrentUserHolder.get()).isNull();
    }

    @Test
    @DisplayName("线程之间不共享——这正是要求模块间显式传参的原因")
    void 线程间不共享() throws Exception {
        CurrentUserHolder.set(CurrentUser.ofUser(1L, "主线程", false));

        AtomicReference<CurrentUser> inOtherThread = new AtomicReference<>();
        Thread thread = new Thread(() -> inOtherThread.set(CurrentUserHolder.get()));
        thread.start();
        thread.join();

        assertThat(inOtherThread.get()).isNull();
        assertThat(CurrentUserHolder.get()).isNotNull();
    }
}
