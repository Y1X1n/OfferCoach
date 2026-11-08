<script setup lang="ts">
import { computed } from 'vue'
import type { Resume, SkillItem } from '@/types'

const props = defineProps<{ resume: Resume }>()

const data = computed(() => props.resume.structured)
const basic = computed(() => data.value.basic)
const contacts = computed(() =>
  [
    basic.value.city,
    `${basic.value.yearsOfExperience} 年经验`,
    basic.value.phoneMasked,
    basic.value.emailMasked,
  ].filter((item) => item && item !== '—'),
)

const levelLabel: Record<SkillItem['level'], string> = {
  BEGINNER: '了解',
  INTERMEDIATE: '熟练',
  ADVANCED: '精通',
}
</script>

<template>
  <div class="flex min-h-full flex-col text-text">
    <header class="text-center">
      <h1 class="break-words text-2xl font-semibold tracking-wide text-text">
        {{ basic.name }}
      </h1>
      <p
        v-if="contacts.length"
        class="mt-2 flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-text-muted"
      >
        <template v-for="(item, index) in contacts" :key="item">
          <span v-if="index > 0" class="text-border" aria-hidden="true">|</span>
          <span class="tabular">{{ item }}</span>
        </template>
      </p>
    </header>

    <section v-if="resume.summary" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        个人简介
      </h2>
      <p class="mt-2.5 text-sm leading-6 text-text-muted">{{ resume.summary }}</p>
    </section>

    <section v-if="data.experiences.length" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        工作经历
      </h2>
      <div class="mt-3.5 space-y-5">
        <article v-for="(item, index) in data.experiences" :key="`exp-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
            <h3 class="min-w-0 text-sm font-semibold text-text">
              {{ item.company }}
              <span class="font-normal text-text-muted">· {{ item.title }}</span>
            </h3>
            <p class="tabular text-2xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
          </div>
          <ul v-if="item.bullets.length" class="mt-1.5 space-y-1">
            <li
              v-for="(bullet, bulletIndex) in item.bullets"
              :key="`bullet-${bulletIndex}`"
              class="flex gap-2 text-sm leading-6 text-text-muted"
            >
              <span
                class="mt-2.5 h-1 w-1 shrink-0 rounded-full bg-text-muted/70"
                aria-hidden="true"
              />
              <span class="min-w-0 break-words">{{ bullet }}</span>
            </li>
          </ul>
        </article>
      </div>
    </section>

    <section v-if="data.projects.length" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        项目经历
      </h2>
      <div class="mt-3.5 space-y-5">
        <article v-for="(project, index) in data.projects" :key="`project-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
            <h3 class="min-w-0 text-sm font-semibold text-text">{{ project.name }}</h3>
            <p class="text-2xs text-text-muted">{{ project.role }}</p>
          </div>
          <p v-if="project.stack.length" class="mt-1 break-words text-2xs text-text-muted">
            技术栈：{{ project.stack.join(' / ') }}
          </p>
          <ul v-if="project.highlights.length" class="mt-1.5 space-y-1">
            <li
              v-for="(highlight, highlightIndex) in project.highlights"
              :key="`highlight-${highlightIndex}`"
              class="flex gap-2 text-sm leading-6 text-text-muted"
            >
              <span
                class="mt-2.5 h-1 w-1 shrink-0 rounded-full bg-text-muted/70"
                aria-hidden="true"
              />
              <span class="min-w-0 break-words">{{ highlight }}</span>
            </li>
          </ul>
          <p v-if="project.metrics.length" class="mt-1.5 break-words text-xs leading-6 text-text">
            结果：{{ project.metrics.join('；') }}
          </p>
        </article>
      </div>
    </section>

    <section v-if="data.education.length" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        教育经历
      </h2>
      <div class="mt-3 space-y-2">
        <div
          v-for="(item, index) in data.education"
          :key="`edu-${index}`"
          class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5"
        >
          <p class="min-w-0 break-words text-sm text-text">
            {{ item.school }}
            <span class="text-xs text-text-muted">{{ item.degree }} · {{ item.major }}</span>
          </p>
          <p class="tabular text-2xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
        </div>
      </div>
    </section>

    <section v-if="data.skills.length" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        技能
      </h2>
      <ul class="mt-3 grid gap-x-6 gap-y-2 sm:grid-cols-2">
        <li
          v-for="skill in data.skills"
          :key="skill.name"
          class="flex min-w-0 flex-wrap items-baseline justify-between gap-x-2 border-b border-border/60 pb-1.5"
        >
          <span class="truncate text-sm font-medium text-text">{{ skill.name }}</span>
          <span class="tabular text-2xs text-text-muted">
            {{ levelLabel[skill.level] }} · {{ skill.years }} 年
          </span>
        </li>
      </ul>
    </section>

    <section v-if="data.certificates.length" class="mt-7">
      <h2
        class="border-b border-border pb-1 text-2xs font-semibold uppercase tracking-caption text-text-muted"
      >
        证书
      </h2>
      <p class="mt-2.5 break-words text-sm leading-6 text-text-muted">
        {{ data.certificates.join('、') }}
      </p>
    </section>
  </div>
</template>
