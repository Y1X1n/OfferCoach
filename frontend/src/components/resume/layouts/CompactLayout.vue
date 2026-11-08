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
  <div class="flex min-h-full flex-col text-sm leading-5 text-text">
    <header
      class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-border pb-2.5"
    >
      <h1 class="min-w-0 break-words text-xl font-semibold tracking-tight text-text">
        {{ basic.name }}
      </h1>
      <p
        v-if="contacts.length"
        class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-2xs text-text-muted"
      >
        <template v-for="(item, index) in contacts" :key="item">
          <span v-if="index > 0" class="text-border" aria-hidden="true">·</span>
          <span class="tabular">{{ item }}</span>
        </template>
      </p>
    </header>

    <p v-if="resume.summary" class="mt-2.5 break-words text-xs leading-5 text-text-muted">
      {{ resume.summary }}
    </p>

    <section v-if="data.experiences.length" class="mt-3.5">
      <h2
        class="border-b border-border pb-0.5 text-3xs font-semibold uppercase tracking-badge text-text-muted"
      >
        工作经历
      </h2>
      <div class="mt-2 space-y-2.5">
        <article v-for="(item, index) in data.experiences" :key="`exp-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-3">
            <h3 class="min-w-0 text-sm font-semibold text-text">
              {{ item.company }}
              <span class="font-normal text-text-muted">· {{ item.title }}</span>
            </h3>
            <p class="tabular text-3xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
          </div>
          <ul v-if="item.bullets.length" class="mt-0.5 space-y-0.5">
            <li
              v-for="(bullet, bulletIndex) in item.bullets"
              :key="`bullet-${bulletIndex}`"
              class="flex gap-1.5 text-xs leading-5 text-text-muted"
            >
              <span class="mt-0.5 shrink-0 text-text-muted/70" aria-hidden="true">·</span>
              <span class="min-w-0 break-words">{{ bullet }}</span>
            </li>
          </ul>
        </article>
      </div>
    </section>

    <section v-if="data.projects.length" class="mt-3.5">
      <h2
        class="border-b border-border pb-0.5 text-3xs font-semibold uppercase tracking-badge text-text-muted"
      >
        项目经历
      </h2>
      <div class="mt-2 space-y-2.5">
        <article v-for="(project, index) in data.projects" :key="`project-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-3">
            <h3 class="min-w-0 text-sm font-semibold text-text">{{ project.name }}</h3>
            <p class="text-3xs text-text-muted">{{ project.role }}</p>
          </div>
          <ul v-if="project.highlights.length" class="mt-0.5 space-y-0.5">
            <li
              v-for="(highlight, highlightIndex) in project.highlights"
              :key="`highlight-${highlightIndex}`"
              class="flex gap-1.5 text-xs leading-5 text-text-muted"
            >
              <span class="mt-0.5 shrink-0 text-text-muted/70" aria-hidden="true">·</span>
              <span class="min-w-0 break-words">{{ highlight }}</span>
            </li>
          </ul>
          <p v-if="project.metrics.length" class="mt-0.5 break-words text-2xs leading-5 text-text">
            {{ project.metrics.join('；') }}
          </p>
          <p v-if="project.stack.length" class="mt-0.5 break-words text-3xs text-text-muted">
            {{ project.stack.join(' / ') }}
          </p>
        </article>
      </div>
    </section>

    <section v-if="data.education.length" class="mt-3.5">
      <h2
        class="border-b border-border pb-0.5 text-3xs font-semibold uppercase tracking-badge text-text-muted"
      >
        教育经历
      </h2>
      <div class="mt-1.5 space-y-1">
        <div
          v-for="(item, index) in data.education"
          :key="`edu-${index}`"
          class="flex flex-wrap items-baseline justify-between gap-x-3"
        >
          <p class="min-w-0 break-words text-xs text-text">
            {{ item.school }}
            <span class="text-text-muted">{{ item.degree }} · {{ item.major }}</span>
          </p>
          <p class="tabular text-3xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
        </div>
      </div>
    </section>

    <section v-if="data.skills.length" class="mt-3.5">
      <h2
        class="border-b border-border pb-0.5 text-3xs font-semibold uppercase tracking-badge text-text-muted"
      >
        技能
      </h2>
      <ul class="mt-1.5 grid gap-x-5 gap-y-1 sm:grid-cols-2">
        <li
          v-for="skill in data.skills"
          :key="skill.name"
          class="flex min-w-0 items-baseline justify-between gap-x-2"
        >
          <span class="truncate text-xs font-medium text-text">{{ skill.name }}</span>
          <span class="tabular shrink-0 text-3xs text-text-muted">
            {{ levelLabel[skill.level] }} · {{ skill.years }} 年
          </span>
        </li>
      </ul>
    </section>

    <section v-if="data.certificates.length" class="mt-3.5">
      <h2
        class="border-b border-border pb-0.5 text-3xs font-semibold uppercase tracking-badge text-text-muted"
      >
        证书
      </h2>
      <p class="mt-1.5 break-words text-xs leading-5 text-text-muted">
        {{ data.certificates.join('、') }}
      </p>
    </section>
  </div>
</template>
