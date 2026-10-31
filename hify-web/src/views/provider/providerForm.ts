import type { ProviderCreatePayload, ProviderType, ProviderUpdatePayload } from '@/types/provider'

export interface ProviderForm extends Record<string, unknown> {
  name: string
  type: ProviderType
  apiKey: string
  baseUrl: string
  anthropicVersion: string
  enabled: boolean
}

export interface EditingProvider {
  type: ProviderType
  configVersion: number
  extraParams?: Record<string, unknown>
}

export function providerPayload(form: ProviderForm): ProviderCreatePayload
export function providerPayload(form: ProviderForm, original: EditingProvider): ProviderUpdatePayload
export function providerPayload(form: ProviderForm, original?: EditingProvider): ProviderCreatePayload | ProviderUpdatePayload {
  const authConfig: ProviderCreatePayload['authConfig'] = {}
  if (form.type !== 'ollama' && form.apiKey.trim()) authConfig.apiKey = form.apiKey.trim()
  if (form.type === 'anthropic') authConfig.anthropicVersion = form.anthropicVersion
  const base: ProviderCreatePayload = {
    name: form.name.trim(), type: form.type, baseUrl: form.baseUrl.trim(), authConfig,
    extraParams: original?.extraParams ?? {}, enabled: form.enabled,
  }
  if (!original) return base
  return {
    ...base, configVersion: original.configVersion,
    credentialAction: form.type === 'ollama' ? 'CLEAR' : authConfig.apiKey ? 'REPLACE' : form.type !== original.type ? 'CLEAR' : 'KEEP',
  }
}
