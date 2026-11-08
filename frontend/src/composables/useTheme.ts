import { computed, ref, watch } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'oc_theme_pref'
const LEGACY_KEY = 'oc_theme'
const preference = ref<ThemePreference>('system')
const systemDark = ref(false)
let media: MediaQueryList | null = null
let initialized = false

const isDark = computed(
  () => preference.value === 'dark' || (preference.value === 'system' && systemDark.value),
)

function applyTheme() {
  if (typeof document === 'undefined') return
  document.documentElement.classList.toggle('dark', isDark.value)
}

function readPreference(): ThemePreference {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark' || saved === 'system') return saved
  const legacy = localStorage.getItem(LEGACY_KEY)
  if (legacy === 'light' || legacy === 'dark') {
    localStorage.setItem(STORAGE_KEY, legacy)
    localStorage.removeItem(LEGACY_KEY)
    return legacy
  }
  return 'system'
}

export function initTheme() {
  if (initialized || typeof window === 'undefined') return
  initialized = true
  preference.value = readPreference()
  media = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = media.matches
  media.addEventListener('change', (event) => {
    systemDark.value = event.matches
  })
  applyTheme()
}

export function useTheme() {
  function setTheme(value: ThemePreference) {
    preference.value = value
    localStorage.setItem(STORAGE_KEY, value)
    applyTheme()
  }

  function toggle() {
    setTheme(isDark.value ? 'light' : 'dark')
  }

  watch(isDark, applyTheme)
  return { isDark, preference, toggle, setTheme }
}
