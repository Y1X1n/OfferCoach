<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  FileText,
  Gauge,
  Moon,
  Sun,
  Timer,
} from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Alert from '@/components/ui/Alert.vue'
import Card from '@/components/ui/Card.vue'
import { useAppStore } from '@/stores/app'
import { useTheme } from '@/composables/useTheme'
import { toast } from '@/composables/useToast'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const isDev = import.meta.env.DEV

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const { isDark, toggle: toggleTheme } = useTheme()

const form = reactive({ email: '', password: '' })
const fieldErrors = ref<{ email?: string; password?: string }>({})
const formError = ref('')
const loading = ref(false)

const highlights = [
  {
    icon: FileText,
    title: '基于简历与目标 JD 出题',
    description: '问题贴合你的真实经历与岗位要求，不背通用题库。',
  },
  {
    icon: Timer,
    title: '还原真实面试节奏',
    description: '限时作答、可跳过，练的是临场表达而不是默写答案。',
  },
  {
    icon: Gauge,
    title: '逐题给出可执行建议',
    description: '从内容、结构、深度到岗位匹配，指出下一步怎么改。',
  },
]

watch(
  () => [form.email, form.password],
  () => {
    formError.value = ''
    if (Object.keys(fieldErrors.value).length > 0) fieldErrors.value = {}
  },
)

const redirectTarget = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return '/'
  return value.startsWith('/login') ? '/' : value
})

function readError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function handleForgot() {
  toast('暂不支持自助找回', 'info', '请联系管理员重置密码，或注册一个新账号继续练习。')
}

async function handleSubmit() {
  if (loading.value) return
  formError.value = ''
  const errors: { email?: string; password?: string } = {}
  const email = form.email.trim()
  if (!email) errors.email = '请输入邮箱'
  else if (!EMAIL_RE.test(email)) errors.email = '邮箱格式不正确，例如 name@example.com'
  if (!form.password) errors.password = '请输入密码'
  fieldErrors.value = errors

  if (Object.keys(errors).length > 0) {
    await nextTick()
    document.getElementById(errors.email ? 'login-email' : 'login-password')?.focus()
    return
  }

  loading.value = true
  try {
    await store.login(email, form.password)
    await router.push(redirectTarget.value)
  } catch (error) {
    formError.value = readError(error, '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg">
    <div class="grid min-h-screen lg:grid-cols-2">
      <section
        class="relative hidden overflow-hidden border-r border-border bg-surface-2/50 lg:flex lg:flex-col"
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
              先练一遍，<br />再去面一次
            </h1>
            <p class="mt-4 text-sm leading-relaxed text-text-2">
              用你的简历与目标 JD 生成问题，按真实节奏作答，拿到逐题的可执行改进建议。
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

          <div
            class="flex max-w-md flex-wrap items-center gap-x-8 gap-y-2 border-t border-border pt-5 text-xs text-text-muted"
          >
            <span><span class="figure text-text">8 题</span> · 单轮约 10 分钟</span>
            <span class="hidden h-3 w-px bg-border sm:block" aria-hidden="true" />
            <span><span class="figure text-text">5 维</span> · 评分与证据</span>
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

        <main class="flex flex-1 items-center justify-center px-5 pb-10 sm:px-8 lg:px-10">
          <div class="w-full max-w-sm">
            <Card padding="lg" class="animate-fade-up">
              <div class="mb-6">
                <h2 class="text-xl font-medium tracking-tight text-text">登录</h2>
                <p class="mt-1.5 text-sm text-text-muted">继续你的模拟面试与简历诊断</p>
              </div>

              <Alert
                v-if="formError"
                variant="danger"
                title="登录失败"
                :description="formError"
                class="mb-5"
              />

              <div v-if="isDev" class="mb-5 rounded-md border border-dashed border-border bg-surface-2 p-3">
                <p class="text-xs font-medium text-text">开发环境提示</p>
                <p class="mt-1 text-xs leading-5 text-text-muted">
                  本地联调可使用后端「oc.demo.*」配置的演示账号；未配置时请先注册。生产环境不展示此提示。
                </p>
              </div>

              <form
                class="space-y-4"
                novalidate
                :aria-busy="loading"
                @submit.prevent="handleSubmit"
              >
                <Input
                  id="login-email"
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
                <div>
                  <Input
                    id="login-password"
                    v-model="form.password"
                    label="密码"
                    type="password"
                    autocomplete="current-password"
                    placeholder="输入密码"
                    required
                    :disabled="loading"
                    :error="fieldErrors.password"
                    input-class="h-11 text-base sm:h-10 sm:text-sm"
                  />
                  <div class="mt-1.5 flex justify-end">
                    <button
                      type="button"
                      class="rounded text-xs font-medium text-text-muted transition-colors hover:text-text"
                      @click="handleForgot"
                    >
                      忘记密码？
                    </button>
                  </div>
                </div>
                <Button type="submit" size="lg" block :loading="loading">
                  登录
                  <ArrowRight v-if="!loading" class="h-4 w-4" aria-hidden="true" />
                </Button>
              </form>

              <p class="mt-5 text-center text-sm text-text-muted">
                还没有账号？
                <RouterLink
                  :to="{ name: 'register' }"
                  class="font-medium text-text-2 hover:text-text"
                >
                  免费注册
                </RouterLink>
              </p>
            </Card>

            <p class="mt-6 text-center text-xs leading-relaxed text-text-muted">
              OfferCoach 仅用于面试练习与自我提升，不承诺任何面试或录用结果。
            </p>
          </div>
        </main>
      </section>
    </div>
  </div>
</template>
