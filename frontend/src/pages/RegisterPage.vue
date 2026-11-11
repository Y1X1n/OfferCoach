<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  ChartColumn,
  FileText,
  Lock,
  Moon,
  RefreshCw,
  Sun,
} from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Alert from '@/components/ui/Alert.vue'
import Card from '@/components/ui/Card.vue'
import { useAppStore } from '@/stores/app'
import { useTheme } from '@/composables/useTheme'
import { toast } from '@/composables/useToast'
import {
  getCaptcha,
  getRegistrationPolicy,
  type CaptchaChallenge,
  type RegistrationPolicy,
} from '@/api/auth'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
type Field =
  | 'email'
  | 'nickname'
  | 'password'
  | 'confirm'
  | 'verifyCode'
  | 'captchaAnswer'
  | 'inviteCode'
const FIELD_ORDER: Field[] = [
  'email',
  'nickname',
  'password',
  'confirm',
  'verifyCode',
  'captchaAnswer',
  'inviteCode',
]

const router = useRouter()
const route = useRoute()
const store = useAppStore()
const { isDark, toggle: toggleTheme } = useTheme()

const form = reactive({
  email: '',
  nickname: '',
  password: '',
  confirm: '',
  verifyCode: '',
  captchaAnswer: '',
  inviteCode: '',
})
const policy = ref<RegistrationPolicy | null>(null)
const captcha = ref<CaptchaChallenge | null>(null)
const captchaLoading = ref(false)
const fieldErrors = ref<Partial<Record<Field, string>>>({})
const formError = ref('')
const loading = ref(false)

const requireVerifyCode = computed(() => policy.value?.requireVerifyCode ?? false)
const requireCaptcha = computed(() => policy.value?.requireCaptcha ?? false)
const registrationOpen = computed(() => policy.value?.registrationOpen ?? true)
const inviteReward = computed(() =>
  policy.value
    ? `注册即得 ${policy.value.inviteRewardInviteeResume} 次简历解析，好友得 ${policy.value.inviteRewardInviterInterview} 次模拟面试`
    : '',
)

const highlights = [
  {
    icon: FileText,
    title: '简历结构化诊断',
    description: '自动解析经历、项目与技能，标出需要补强证据的地方。',
  },
  {
    icon: ChartColumn,
    title: '五维评分报告',
    description: '每轮面试都有分数、原文证据与下一步改进动作。',
  },
  {
    icon: Lock,
    title: '数据由账号隔离',
    description: '简历、面试与报告保存在服务端，退出登录后需要重新验证身份。',
  },
]

watch(
  () => [
    form.email,
    form.nickname,
    form.password,
    form.confirm,
    form.verifyCode,
    form.captchaAnswer,
    form.inviteCode,
  ],
  () => {
    formError.value = ''
    if (Object.keys(fieldErrors.value).length > 0) fieldErrors.value = {}
  },
)

onMounted(async () => {
  const invite = typeof route.query.invite === 'string' ? route.query.invite.trim() : ''
  if (invite) form.inviteCode = invite.slice(0, 16)
  try {
    policy.value = await getRegistrationPolicy()
    if (policy.value.requireCaptcha) await refreshCaptcha()
  } catch (error) {
    formError.value = readError(error, '注册策略加载失败，请刷新页面重试')
  }
})

async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  form.captchaAnswer = ''
  try {
    captcha.value = await getCaptcha()
  } catch (error) {
    captcha.value = null
    formError.value = readError(error, '验证码加载失败，请点击刷新重试')
  } finally {
    captchaLoading.value = false
  }
}

const passwordStrength = computed(() => {
  const value = form.password
  if (!value) return { score: 0, label: '', tone: 'bg-border' }
  const mixed = /[A-Za-z]/.test(value) && /\d/.test(value)
  const extra = value.length >= 12 || /[^A-Za-z0-9]/.test(value)
  const score = (value.length >= 8 ? 1 : 0) + (mixed ? 1 : 0) + (extra ? 1 : 0)
  if (score >= 3) return { score, label: '较强', tone: 'bg-success' }
  if (score === 2) return { score, label: '一般', tone: 'bg-warning' }
  return { score, label: '偏弱', tone: 'bg-danger' }
})

function readError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function validate(): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {}
  const email = form.email.trim()
  const nickname = form.nickname.trim()
  if (!email) errors.email = '请输入邮箱'
  else if (!EMAIL_RE.test(email)) errors.email = '邮箱格式不正确，例如 name@example.com'
  if (!nickname) errors.nickname = '请输入昵称'
  else if (nickname.length < 2 || nickname.length > 40) errors.nickname = '昵称长度需为 2–40 个字符'
  if (!form.password) errors.password = '请设置密码'
  else if (form.password.length < 8) errors.password = '密码至少 8 位'
  else if (!/[A-Za-z]/.test(form.password) || !/\d/.test(form.password))
    errors.password = '密码需同时包含字母和数字'
  if (!form.confirm) errors.confirm = '请再次输入密码'
  else if (form.confirm !== form.password) errors.confirm = '两次输入的密码不一致'
  if (requireVerifyCode.value) {
    if (!form.verifyCode.trim()) errors.verifyCode = '请输入验证码或邀请码'
    else if (form.verifyCode.trim().length > 64) errors.verifyCode = '验证码或邀请码过长'
  }
  if (requireCaptcha.value) {
    if (!captcha.value) errors.captchaAnswer = '验证码未加载，请点击刷新'
    else if (!form.captchaAnswer.trim()) errors.captchaAnswer = '请填写验证码答案'
    else if (!/^\d+$/.test(form.captchaAnswer.trim())) errors.captchaAnswer = '验证码答案应为数字'
  }
  if (form.inviteCode.trim() && form.inviteCode.trim().length < 6) {
    errors.inviteCode = '邀请码格式不正确'
  }
  return errors
}

async function handleSubmit() {
  if (loading.value) return
  formError.value = ''
  if (!registrationOpen.value) {
    formError.value = '注册暂未开放，请稍后再试'
    return
  }
  const errors = validate()
  fieldErrors.value = errors
  const first = FIELD_ORDER.find((field) => errors[field])
  if (first) {
    await nextTick()
    document.getElementById(`register-${first}`)?.focus()
    return
  }
  loading.value = true
  try {
    await store.register({
      email: form.email.trim(),
      nickname: form.nickname.trim(),
      password: form.password,
      ...(form.verifyCode.trim() ? { verifyCode: form.verifyCode.trim() } : {}),
      ...(form.inviteCode.trim() ? { inviteCode: form.inviteCode.trim().toUpperCase() } : {}),
      ...(requireCaptcha.value && captcha.value
        ? { captchaId: captcha.value.id, captchaAnswer: form.captchaAnswer.trim() }
        : {}),
    })
    toast('注册成功', 'success', '已为你自动登录，正在进入工作台。')
    await router.push('/')
  } catch (error) {
    formError.value = readError(error, '注册失败，请稍后重试')
    if (requireCaptcha.value) await refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg">
    <div class="grid min-h-screen lg:grid-cols-2">
      <section
        class="relative hidden overflow-hidden border-r border-border bg-surface-2 lg:flex lg:flex-col"
      >
        <div class="relative flex h-full flex-col justify-between gap-12 p-10 xl:p-14">
          <div class="flex items-center gap-2.5">
            <span
              class="flex h-7 w-7 items-center justify-center rounded-sm bg-text text-sm font-semibold leading-none text-surface"
              aria-hidden="true"
            >
              O
            </span>
            <div>
              <p class="text-sm font-medium tracking-tight">OfferCoach</p>
              <p class="text-xs text-text-muted">面试练习与复盘</p>
            </div>
          </div>

          <div class="max-w-md">
            <h1 class="text-3xl font-medium leading-tight tracking-tight text-text xl:text-4xl">
              从第一份简历，<br />开始认真准备
            </h1>
            <p class="mt-4 text-sm leading-relaxed text-text-2">
              注册即可获得免费模拟额度，把模糊的自我感觉换成具体的分数与改进建议。
            </p>
            <ul class="mt-8 space-y-4">
              <li v-for="item in highlights" :key="item.title" class="flex gap-3">
                <component
                  :is="item.icon"
                  class="mt-1 h-4 w-4 shrink-0 text-muted"
                  aria-hidden="true"
                />
                <div>
                  <p class="text-sm text-text">{{ item.title }}</p>
                  <p class="mt-0.5 text-xs leading-relaxed text-text-muted">
                    {{ item.description }}
                  </p>
                </div>
              </li>
            </ul>
          </div>

          <div class="grid max-w-md grid-cols-2 gap-3">
            <div class="rounded-lg border border-border bg-surface/80 p-4">
              <p class="tabular text-2xl font-semibold text-text">
                {{ policy?.dailyInterviewLimit ?? 3 }} 次
              </p>
              <p class="mt-1 text-xs text-text-muted">每日模拟面试上限</p>
            </div>
            <div class="rounded-lg border border-border bg-surface/80 p-4">
              <p class="tabular text-2xl font-semibold text-text">5 维</p>
              <p class="mt-1 text-xs text-text-muted">评分维度 · 逐题拆解</p>
            </div>
          </div>
        </div>
      </section>

      <section class="flex min-h-screen flex-col">
        <header class="flex items-center justify-between gap-4 px-5 py-5 sm:px-8 lg:px-10">
          <div class="flex items-center gap-2.5 lg:hidden">
            <span
              class="flex h-7 w-7 items-center justify-center rounded-sm bg-text text-sm font-semibold leading-none text-surface"
              aria-hidden="true"
            >
              O
            </span>
            <div class="leading-tight">
              <p class="text-sm font-medium">OfferCoach</p>
              <p class="text-xs text-text-muted">面试练习与复盘</p>
            </div>
          </div>
          <button
            type="button"
            class="ml-auto inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11"
            :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
            @click="toggleTheme"
          >
            <Sun v-if="isDark" class="h-4 w-4" />
            <Moon v-else class="h-4 w-4" />
          </button>
        </header>

        <main class="flex flex-1 items-center justify-center px-5 py-8 sm:px-8 lg:px-10">
          <div class="w-full max-w-md">
            <Card padding="lg" class="animate-fade-up">
              <div class="mb-6">
                <h2 class="text-xl font-medium tracking-tight text-text">创建账号</h2>
                <p class="mt-1.5 text-sm text-text-muted">
                  注册后自动登录，直接开始第一轮模拟面试
                </p>
              </div>

              <Alert
                v-if="formError"
                variant="danger"
                title="注册失败"
                :description="formError"
                class="mb-5"
              />

              <form
                class="space-y-4"
                novalidate
                :aria-busy="loading"
                @submit.prevent="handleSubmit"
              >
                <Input
                  id="register-email"
                  v-model="form.email"
                  label="邮箱"
                  type="email"
                  autocomplete="email"
                  placeholder="you@example.com"
                  required
                  :disabled="loading"
                  :error="fieldErrors.email"
                  input-class="h-11 text-base sm:h-10 sm:text-sm"
                />
                <Input
                  id="register-nickname"
                  v-model="form.nickname"
                  label="昵称"
                  autocomplete="nickname"
                  placeholder="面试官会看到的称呼"
                  required
                  :disabled="loading"
                  :error="fieldErrors.nickname"
                  hint="2–40 个字符"
                  input-class="h-11 text-base sm:h-10 sm:text-sm"
                />
                <div>
                  <Input
                    id="register-password"
                    v-model="form.password"
                    label="密码"
                    type="password"
                    autocomplete="new-password"
                    placeholder="至少 8 位，含字母与数字"
                    required
                    :disabled="loading"
                    :error="fieldErrors.password"
                    input-class="h-11 text-base sm:h-10 sm:text-sm"
                  />
                  <div v-if="form.password" class="mt-2 flex items-center gap-2">
                    <div class="flex flex-1 gap-1">
                      <span
                        v-for="step in 3"
                        :key="step"
                        :class="[
                          'h-1 flex-1 rounded-full transition-colors',
                          passwordStrength.score >= step ? passwordStrength.tone : 'bg-border',
                        ]"
                      />
                    </div>
                    <span class="w-8 text-right text-xs text-text-muted">{{
                      passwordStrength.label
                    }}</span>
                  </div>
                </div>
                <Input
                  id="register-confirm"
                  v-model="form.confirm"
                  label="确认密码"
                  type="password"
                  autocomplete="new-password"
                  placeholder="再次输入密码"
                  required
                  :disabled="loading"
                  :error="fieldErrors.confirm"
                  input-class="h-11 text-base sm:h-10 sm:text-sm"
                />
                <div v-if="!registrationOpen" class="rounded-lg border border-warning/40 bg-warning/10 px-3 py-2.5 text-xs leading-5 text-warning">
                  当前注册暂未开放，请稍后再试或联系管理员。
                </div>
                <div v-if="requireVerifyCode">
                  <Input
                    id="register-verifyCode"
                    v-model="form.verifyCode"
                    label="验证码 / 邀请码"
                    autocomplete="one-time-code"
                    placeholder="由管理员提供"
                    required
                    :disabled="loading"
                    :error="fieldErrors.verifyCode"
                    hint="开发环境填写固定验证码；生产环境填写管理员提供的访问码"
                    input-class="h-11 font-mono text-base sm:h-10 sm:text-sm"
                  />
                </div>
                <div v-if="requireCaptcha">
                  <p class="mb-1.5 text-sm font-medium text-text">人机验证</p>
                  <div class="flex items-center gap-2">
                    <div
                      class="flex h-11 flex-1 items-center justify-between gap-3 rounded-md border border-border bg-surface-2 px-3 sm:h-10"
                    >
                      <span class="tabular font-mono text-base font-medium tracking-wide text-text">
                        {{ captcha?.question ?? '加载中…' }}
                      </span>
                      <button
                        type="button"
                        class="inline-flex h-7 w-7 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface hover:text-text disabled:opacity-50"
                        :disabled="captchaLoading"
                        aria-label="刷新验证码"
                        @click="refreshCaptcha"
                      >
                        <RefreshCw
                          class="h-4 w-4"
                          :class="{ 'animate-spin': captchaLoading }"
                          aria-hidden="true"
                        />
                      </button>
                    </div>
                  </div>
                  <div class="mt-3">
                    <Input
                      id="register-captchaAnswer"
                      v-model="form.captchaAnswer"
                      label="验证码答案"
                      autocomplete="off"
                      placeholder="填写计算结果"
                      :disabled="loading"
                      :error="fieldErrors.captchaAnswer"
                      input-class="h-11 text-base sm:h-10 sm:text-sm"
                    />
                  </div>
                </div>
                <div v-if="policy?.inviteOptional !== false">
                  <Input
                    id="register-inviteCode"
                    v-model="form.inviteCode"
                    label="邀请码（可选）"
                    autocomplete="off"
                    placeholder="好友的邀请码"
                    :disabled="loading"
                    :error="fieldErrors.inviteCode"
                    :hint="
                      inviteReward
                        ? `可选；${inviteReward}`
                        : '可选；填写好友邀请码后双方获得额外额度'
                    "
                    input-class="h-11 font-mono text-base uppercase sm:h-10 sm:text-sm"
                  />
                </div>
                <Button type="submit" size="lg" block :loading="loading">
                  创建账号
                  <ArrowRight v-if="!loading" class="h-4 w-4" aria-hidden="true" />
                </Button>
              </form>

              <p class="mt-5 text-center text-sm text-text-muted">
                已有账号？
                <RouterLink :to="{ name: 'login' }" class="font-medium text-text-2 hover:text-text"
                  >去登录</RouterLink
                >
              </p>
            </Card>

            <p class="mt-6 text-center text-xs leading-relaxed text-text-muted">
              注册即表示同意仅将数据用于面试练习与自我提升；OfferCoach 不承诺任何面试或录用结果。
            </p>
          </div>
        </main>
      </section>
    </div>
  </div>
</template>
