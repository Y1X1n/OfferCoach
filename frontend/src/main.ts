import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from '@/App.vue'
import router from '@/router'
import { initTheme } from '@/composables/useTheme'
import { toast } from '@/composables/useToast'
import '@/styles/main.css'

initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)

app.config.errorHandler = (error, _instance, info) => {
  // eslint-disable-next-line no-console -- 浏览器端异常没有服务端日志出口，必须保留原始堆栈
  console.error(`[vue] ${info}`, error)
  toast('页面出现异常', 'error', '可以刷新页面重试；若持续出现，请复制诊断信息反馈。')
}

window.addEventListener('unhandledrejection', (event) => {
  const reason: unknown = event.reason
  if (reason instanceof DOMException && reason.name === 'AbortError') return
  // eslint-disable-next-line no-console -- 未处理的 Promise 拒绝需要保留现场
  console.error('[unhandledrejection]', reason)
})

app.mount('#app')
