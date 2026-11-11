<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, FileText, FileUser, ShieldCheck, Sparkles, Wrench } from 'lucide-vue-next'
import type { Jd, JdMatch } from '@/types'
import ScoreRing from '@/components/charts/ScoreRing.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import JdRawText from './JdRawText.vue'
import { formatDate, formatRelative, scoreTone } from '@/utils/format'

const props = defineProps<{ jd: Jd; match: JdMatch }>()
const emit = defineEmits<{ start: [] }>()

const seniorityLabel: Record<Jd['structured']['seniority'], string> = {
  JUNIOR: '初级岗位',
  MID: '中级岗位',
  SENIOR: '高级岗位',
}

const matchLevel = computed(() => {
  const score = props.match.score
  if (score >= 85) return '匹配度较高'
  if (score >= 70) return '匹配度中等偏上'
  if (score >= 60) return '匹配度中等'
  return '匹配度偏低'
})
</script>

<template>
  <div class="space-y-4">
    <Card padding="lg">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="text-lg font-semibold text-text">{{ jd.title }}</h2>
            <Badge variant="neutral">{{ seniorityLabel[jd.structured.seniority] }}</Badge>
          </div>
          <p class="mt-1 text-sm text-text-muted">{{ jd.company }}</p>
          <p class="mt-0.5 text-2xs text-text-muted">
            创建于 {{ formatRelative(jd.createdAt) }}（{{ formatDate(jd.createdAt, true) }}）
          </p>
        </div>
        <Badge variant="brand" dot>已生成匹配分析</Badge>
      </div>

      <div class="mt-6 grid gap-6 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-start">
        <div
          class="flex flex-col items-center gap-2.5 rounded-lg border border-border bg-surface-2/40 px-5 py-4"
        >
          <ScoreRing
            :score="match.score"
            :size="140"
            label="匹配度"
            sublabel="满分 100 · 仅供参考"
          />
          <Badge :variant="scoreTone(match.score)">{{ matchLevel }}</Badge>
          <p class="text-center text-2xs leading-5 text-text-muted">
            用于安排准备重点，不代表面试结果。
          </p>
        </div>

        <div class="grid min-w-0 gap-5">
          <section>
            <h3 class="flex flex-wrap items-center gap-2 text-sm font-semibold text-text">
              <ShieldCheck class="h-4 w-4 text-success" aria-hidden="true" />
              已匹配技能
              <Badge variant="success">{{ match.matchedSkills.length }}</Badge>
            </h3>
            <ul v-if="match.matchedSkills.length" class="mt-2.5 space-y-2">
              <li
                v-for="item in match.matchedSkills"
                :key="item.skill"
                class="flex flex-wrap items-center gap-x-2 gap-y-1"
              >
                <Badge variant="success">{{ item.skill }}</Badge>
                <span class="min-w-0 flex-1 text-xs leading-5 text-text-muted">{{
                  item.evidence
                }}</span>
              </li>
            </ul>
            <p v-else class="mt-2 text-sm text-text-muted">
              暂未识别到与岗位直接对应的技能，建议核对 JD 关键词是否完整。
            </p>
          </section>

          <section class="border-t border-border pt-4">
            <h3 class="flex flex-wrap items-center gap-2 text-sm font-semibold text-text">
              <Wrench class="h-4 w-4 text-warning" aria-hidden="true" />
              缺口技能
              <Badge variant="warning">{{ match.missingSkills.length }}</Badge>
            </h3>
            <ul v-if="match.missingSkills.length" class="mt-2.5 space-y-2">
              <li
                v-for="item in match.missingSkills"
                :key="item.skill"
                class="rounded-md border border-warning/30 bg-warning/5 p-2.5"
              >
                <p class="flex flex-wrap items-center gap-2">
                  <Badge variant="warning">{{ item.skill }}</Badge>
                  <span class="text-2xs text-text-muted">建议补强</span>
                </p>
                <p class="mt-1 text-xs leading-5 text-text-muted">{{ item.suggestion }}</p>
              </li>
            </ul>
            <p v-else class="mt-2 text-sm text-text-muted">
              本次比对未发现明显缺口技能，可以继续关注岗位的业务场景要求。
            </p>
          </section>
        </div>
      </div>
    </Card>

    <Card padding="lg">
      <h3 class="text-sm font-semibold text-text">匹配证据</h3>
      <p class="mt-1 text-xs leading-5 text-text-muted">
        以下引用来自 JD 与你最近解析的简历，用于核对匹配判断是否符合实际情况。
      </p>

      <div class="mt-3 space-y-3">
        <div
          v-for="(item, index) in match.evidence"
          :key="index"
          class="grid gap-3 rounded-md border border-border bg-surface-2/40 p-3 sm:grid-cols-2"
        >
          <figure>
            <figcaption
              class="flex items-center gap-1.5 text-2xs text-text-muted"
            >
              <FileText class="h-3.5 w-3.5" aria-hidden="true" />
              JD 原文
            </figcaption>
            <blockquote class="mt-1.5 break-words text-sm leading-6 text-text">
              “{{ item.jdQuote }}”
            </blockquote>
          </figure>
          <figure class="border-t border-border pt-3 sm:border-l sm:border-t-0 sm:pl-3 sm:pt-0">
            <figcaption
              class="flex items-center gap-1.5 text-2xs text-text-muted"
            >
              <FileUser class="h-3.5 w-3.5" aria-hidden="true" />
              简历原文
            </figcaption>
            <blockquote class="mt-1.5 break-words text-sm leading-6 text-text">
              “{{ item.resumeQuote }}”
            </blockquote>
          </figure>
        </div>
        <p v-if="!match.evidence.length" class="text-sm text-text-muted">
          本次分析没有生成可引用的证据片段。
        </p>
      </div>

      <Alert v-if="match.risks.length" variant="warning" title="需要留意" class="mt-4">
        <ul class="mt-1 list-disc space-y-1 pl-4 text-sm leading-6">
          <li v-for="risk in match.risks" :key="risk">{{ risk }}</li>
        </ul>
      </Alert>

      <div class="mt-4 border-t border-border pt-4">
        <div class="flex items-center justify-between gap-2">
          <h3 class="text-sm font-semibold text-text">岗位原文</h3>
          <Badge variant="neutral">{{ jd.rawText.length }} 字</Badge>
        </div>
        <JdRawText :text="jd.rawText" class="mt-2" />
      </div>

      <div
        class="mt-5 flex flex-col-reverse items-stretch gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-xs leading-5 text-text-muted">
          下一步：选择简历并配置题量、难度与面试官风格。
        </p>
        <Button class="justify-center" @click="emit('start')">
          <Sparkles class="h-4 w-4" aria-hidden="true" />
          用这个 JD 开始面试
          <ArrowRight class="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </Card>
  </div>
</template>
