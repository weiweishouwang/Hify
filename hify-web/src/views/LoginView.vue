<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { ApiError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { newLocalId } from '@/utils/id'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const form = reactive({ username: '', password: '' })
const submitting = ref(false)

/** 模板里不能写 import.meta.env.DEV，要在脚本里取出来再用。 */
const isDev = import.meta.env.DEV

function redirectTarget(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/chat'
}

async function submit(): Promise<void> {
  if (!form.username || !form.password || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const result = await login({ username: form.username, password: form.password })
    user.login(result.token)
    user.setProfile({ id: result.id, username: result.username, admin: result.admin })
    router.push(redirectTarget())
  } catch (error) {
    // 业务错误已经由 request.ts 的响应拦截器提示过了，这里只兜底非 ApiError 的情况，
    // 免得弹两次 toast
    if (!(error instanceof ApiError)) {
      console.error('登录失败', error)
      ElMessage.error('登录失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

/**
 * 开发模式跳过登录。
 *
 * 只在 vite dev 下出现（import.meta.env.DEV），生产构建里这个按钮不存在。
 * 认证方案定下来之前，本地开发需要能进得去看界面。
 */
function enterAsDev(): void {
  user.login(`dev-${newLocalId()}`)
  router.push(redirectTarget())
}
</script>

<template>
  <div class="login">
    <el-card class="login__card" shadow="always">
      <h2 class="login__title">登录 Hify</h2>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="密码"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="login__submit" :loading="submitting" @click="submit">
          登录
        </el-button>
      </el-form>

      <el-divider v-if="isDev">开发模式</el-divider>
      <el-button v-if="isDev" class="login__submit" @click="enterAsDev">
        跳过登录进入（仅开发环境）
      </el-button>

      <p class="login__note">
        认证方式尚未确定（共享密码 / OIDC / LDAP，见 CLAUDE.md §五），
        <code>POST /api/v1/auth/login</code> 接口也还未实现。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
}

.login__card {
  width: 100%;
  max-width: 380px;
}

.login__title {
  margin: 0 0 20px;
  font-size: 20px;
}

.login__submit {
  width: 100%;
}

.login__note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--hify-text-muted);
}
</style>
