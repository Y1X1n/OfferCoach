<script setup lang="ts">
import { Monitor, Moon, Palette, Sun } from 'lucide-vue-next'
import type { Component } from 'vue'
import Card from '@/components/ui/Card.vue'
import { useTheme } from '@/composables/useTheme'

type ThemeChoice = 'light' | 'dark' | 'system'

const { preference: themeChoice, setTheme: applyThemePreference } = useTheme()

const themeOptions: { value: ThemeChoice; label: string; icon: Component }[] = [
  { value: 'light', label: '浅色', icon: Sun },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor },
]

function chooseTheme(value: ThemeChoice): void {
  applyThemePreference(value)
}
</script>

<template>
  <Card>
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted">
        <Palette class="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <h2 class="text-sm font-semibold text-text">外观</h2>
        <p class="text-xs text-text-muted">选择界面主题，偏好保存在本机。</p>
      </div>
    </div>

    <div role="group" aria-label="外观主题" class="mt-4 grid grid-cols-3 gap-2">
      <button
        v-for="option in themeOptions"
        :key="option.value"
        type="button"
        :aria-pressed="themeChoice === option.value"
        :class="[
          'flex flex-col items-center gap-2 rounded-md border px-2 py-3 text-xs font-medium transition-colors',
          themeChoice === option.value
            ? 'border-text/30 bg-surface-2 text-text'
            : 'border-border bg-surface text-text-muted hover:bg-surface-2 hover:text-text',
        ]"
        @click="chooseTheme(option.value)"
      >
        <component :is="option.icon" class="h-4 w-4" aria-hidden="true" />
        {{ option.label }}
      </button>
    </div>

    <p class="mt-3 text-xs leading-5 text-text-muted">
      跟随系统会实时响应系统深浅色变化；主题偏好仅保存在本机。
    </p>
  </Card>
</template>
