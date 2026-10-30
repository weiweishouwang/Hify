<script setup lang="ts" generic="Form extends Record<string, unknown>">
import { computed, nextTick, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

/** 弹窗的两种模式。 */
export type DialogMode = 'create' | 'edit'

const props = withDefaults(
  defineProps<{
    /** 弹窗标题。 */
    title: string
    /** 弹窗宽度。 */
    width?: string | number
    /** 表单校验规则。 */
    rules?: FormRules
    /** 新增时的初始值；编辑时会被传入的数据覆盖。 */
    initialValues?: Partial<Form>
    /** 表单标签宽度。 */
    labelWidth?: string | number
    /** 提交按钮文字。 */
    submitText?: string
  }>(),
  {
    width: '560px',
    labelWidth: '96px',
    submitText: '确定',
  },
)

/** 显示 / 隐藏。 */
const visible = defineModel<boolean>({ required: true })

/**
 * 提交中的状态。
 *
 * **由父组件驱动**：父组件用 `useRequest` 的 loading 传进来，
 * 这样「调接口 → loading → 关闭弹窗」是一条链，组件不用自己管 Promise。
 */
const submitting = defineModel<boolean>('loading', { default: false })

const emit = defineEmits<{
  /** 校验通过后触发，表单数据在这里面。API 调用由父组件处理。 */
  submit: [form: Form]
}>()

const formRef = ref<FormInstance>()
const formData = ref<Form>({} as Form)
const mode = ref<DialogMode>('create')

/** 当前是编辑模式还是新增模式。 */
const isEdit = computed(() => mode.value === 'edit')

/**
 * 打开弹窗。
 *
 * 传 data 是编辑模式（表单预填），不传是新增模式。
 *
 * @param data 编辑时带上的初始数据
 */
function open(data?: Partial<Form>): void {
  mode.value = data === undefined ? 'create' : 'edit'
  formData.value = { ...(props.initialValues ?? {}), ...(data ?? {}) } as Form
  visible.value = true
  // 打开后清掉上一次残留的校验红字
  void nextTick(() => formRef.value?.clearValidate())
}

/** 关闭后把表单重置回空白，下次打开不会带着上次的值。 */
function handleClosed(): void {
  formRef.value?.resetFields()
  formRef.value?.clearValidate()
  formData.value = {} as Form
  mode.value = 'create'
}

/** 校验通过才提交，校验不过不发事件。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  emit('submit', formData.value)
}

defineExpose({ open, isEdit, formData, mode })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    append-to-body
    class="hify-form-dialog"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      @submit.prevent="handleSubmit"
    >
      <!-- 表单字段由父组件在默认插槽里给 -->
      <slot :form="formData" :mode="mode" />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ submitText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hify-form-dialog :deep(.el-dialog__body) {
  padding-top: var(--hify-space-2);
}
</style>
