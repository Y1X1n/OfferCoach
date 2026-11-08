<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ArrowRight, Briefcase, CheckCircle2 } from 'lucide-vue-next'
import type { Jd } from '@/types'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import { cn } from '@/utils/cn'
import { formatRelative, formatScore } from '@/utils/format'

defineProps<{ jds: Jd[] }>()

const selectedId = defineModel<string>({ required: true })

function matchVariant(jd: Jd): 'success' | 'brand' | 'warning' | 'neutral' {
  const score = jd.match?.score
  if (score === undefined) return 'neutral'
  if (score >= 80) return 'success'
  if (score >= 65) return 'brand'
  return 'warning'
}

function matchLabel(jd: Jd): string {
  return jd.match ? `匹配度 ${formatScore(jd.match.score)}` : '匹配度待评估'
}
</script>

<template>
  <Card padding="lg">
    <section aria-labelledby="setup-jd">
      <div class="flex items-start gap-3">
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand/10 text-sm font-semibold text-brand"
        >
          2
        </span>
        <div class="min-w-0 flex-1">
          <h2 id="setup-jd" class="text-base font-semibold text-text">选择目标岗位</h2>
          <p class="mt-0.5 text-xs leading-5 text-text-muted">
            题目与追问会围绕 JD 的职责与必备技能展开。
          </p>
        </div>
        <Badge variant="brand">必选</Badge>
      </div>

      <div class="mt-4">
        <Alert
          v-if="jds.length === 0"
          variant="warning"
          title="还没有目标岗位"
          description="粘贴一份 JD，系统会提取技能要求并用于出题。"
        >
          <RouterLink
            to="/jds"
            class="mt-2 inline-flex items-center gap-1 text-xs font-medium underline underline-offset-2"
          >
            去粘贴 JD
            <ArrowRight class="h-3 w-3" aria-hidden="true" />
          </RouterLink>
        </Alert>

        <div v-else class="grid gap-3 sm:grid-cols-2" role="radiogroup" aria-labelledby="setup-jd">
          <button
            v-for="jd in jds"
            :key="jd.id"
            type="button"
            role="radio"
            :aria-checked="selectedId === jd.id"
            :class="
              cn(
                'flex h-full flex-col rounded-lg border p-4 text-left transition-all duration-150',
                selectedId === jd.id
                  ? 'border-brand bg-brand/5 shadow-sm'
                  : 'border-border bg-surface hover:border-brand/40 hover:bg-surface-2/60',
              )
            "
            @click="selectedId = jd.id"
          >
            <span class="flex items-start justify-between gap-3">
              <span
                class="flex h-9 w-9 items-center justify-center rounded-md bg-accent/10 text-accent"
              >
                <Briefcase class="h-4 w-4" aria-hidden="true" />
              </span>
              <span class="flex items-center gap-2">
                <Badge :variant="matchVariant(jd)">{{ matchLabel(jd) }}</Badge>
                <span
                  v-if="selectedId === jd.id"
                  class="flex h-5 w-5 items-center justify-center rounded-full bg-brand text-brand-foreground"
                >
                  <CheckCircle2 class="h-4 w-4" aria-hidden="true" />
                </span>
              </span>
            </span>
            <span class="mt-3 block text-sm font-semibold text-text">{{ jd.title }}</span>
            <span class="mt-0.5 block text-xs text-text-muted">{{ jd.company }}</span>
            <span class="mt-2 line-clamp-1 block text-2xs text-text-muted">
              {{ jd.structured.mustHave.slice(0, 3).join(' · ') || 'JD 未提取必备技能' }}
            </span>
            <span class="mt-auto block pt-3 text-2xs text-text-muted">
              {{ formatRelative(jd.createdAt) }}创建
            </span>
          </button>
        </div>
      </div>
    </section>
  </Card>
</template>
