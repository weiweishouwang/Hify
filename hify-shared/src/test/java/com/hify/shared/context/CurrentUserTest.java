package com.hify.shared.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link CurrentUser} 与 {@link AuthType} 的单元测试。 */
class CurrentUserTest {

    @Test
    @DisplayName("网页登录的调用者")
    void 网页登录() {
        CurrentUser user = CurrentUser.ofUser(1L, "张三", true);

        assertThat(user.userId()).isEqualTo(1L);
        assertThat(user.username()).isEqualTo("张三");
        assertThat(user.admin()).isTrue();
        assertThat(user.authType()).isEqualTo(AuthType.USER);
        assertThat(user.isApiKey()).isFalse();
    }

    @Test
    @DisplayName("API Key 调用的调用者，归属用户不是管理员")
    void APIKey调用() {
        CurrentUser user = CurrentUser.ofApiKey(9L);

        assertThat(user.userId()).isEqualTo(9L);
        assertThat(user.authType()).isEqualTo(AuthType.API_KEY);
        assertThat(user.isApiKey()).isTrue();
        assertThat(user.admin()).isFalse();
    }

    @Test
    @DisplayName("用户名为空时回退为 userId")
    void 用户名回退() {
        assertThat(CurrentUser.ofUser(1L, null, false).username()).isEqualTo("1");
        assertThat(CurrentUser.ofUser(1L, "  ", false).username()).isEqualTo("1");
        assertThat(CurrentUser.ofUser(1L, "", false).username()).isEqualTo("1");
    }

    @Test
    @DisplayName("userId 与 authType 必填")
    void 必填校验() {
        assertThatThrownBy(() -> new CurrentUser(null, "张三", false, AuthType.USER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentUser(1L, "张三", false, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("是不可变值对象，相同内容相等")
    void 不可变且有值语义() {
        CurrentUser a = CurrentUser.ofUser(1L, "张三", true);
        CurrentUser b = CurrentUser.ofUser(1L, "张三", true);
        CurrentUser c = CurrentUser.ofUser(1L, "张三", false);

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
        assertThat(a.toString()).contains("1");
    }

    @Test
    @DisplayName("认证方式只有两种")
    void 认证方式取值() {
        assertThat(AuthType.values())
                .containsExactlyInAnyOrder(AuthType.USER, AuthType.API_KEY);
    }

    @Test
    @DisplayName("序列化后不含 isApiKey 派生出来的 apiKey 字段")
    void 序列化不含派生字段() throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(CurrentUser.ofApiKey(9L));

        assertThat(json).contains("userId").contains("authType");
        assertThat(json).doesNotContain("apiKey");
    }
}
