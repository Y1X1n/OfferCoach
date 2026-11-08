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

const skillsText = computed(() =>
  data.value.skills
    .map((skill) => `${skill.name}（${levelLabel[skill.level]} · ${skill.years} 年）`)
    .join(' · '),
)
</script>

<template>
  <div class="flex min-h-full flex-col text-text">
    <header>
      <h1 class="break-words text-3xl font-light tracking-tight text-text sm:text-4xl">
        {{ basic.name }}
      </h1>
      <p
        v-if="contacts.length"
        class="mt-3 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs tracking-wide text-text-muted"
      >
        <template v-for="(item, index) in contacts" :key="item">
          <span v-if="index > 0" class="h-1 w-1 rounded-full bg-border" aria-hidden="true" />
          <span class="tabular">{{ item }}</span>
        </template>
      </p>
    </header>

    <section v-if="resume.summary" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        简介
      </h2>
      <p class="mt-3 max-w-2xl break-words text-sm leading-7 text-text">{{ resume.summary }}</p>
    </section>

    <section v-if="data.experiences.length" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        工作经历
      </h2>
      <div class="mt-4 space-y-6">
        <article v-for="(item, index) in data.experiences" :key="`exp-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5">
            <h3 class="min-w-0 break-words text-sm font-medium text-text">
              {{ item.company }}
              <span class="text-text-muted">· {{ item.title }}</span>
            </h3>
            <p class="tabular text-2xs tracking-wide text-text-muted">
              {{ item.start }} – {{ item.end }}
            </p>
          </div>
          <ul v-if="item.bullets.length" class="mt-2 space-y-1.5">
            <li
              v-for="(bullet, bulletIndex) in item.bullets"
              :key="`bullet-${bulletIndex}`"
              class="break-words text-sm leading-7 text-text-muted"
            >
              {{ bullet }}
            </li>
          </ul>
        </article>
      </div>
    </section>

    <section v-if="data.projects.length" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        项目经历
      </h2>
      <div class="mt-4 space-y-6">
        <article v-for="(project, index) in data.projects" :key="`project-${index}`">
          <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5">
            <h3 class="min-w-0 break-words text-sm font-medium text-text">{{ project.name }}</h3>
            <p class="text-2xs text-text-muted">{{ project.role }}</p>
          </div>
          <p
            v-if="project.stack.length"
            class="mt-1 break-words text-2xs tracking-wide text-text-muted"
          >
            {{ project.stack.join(' / ') }}
          </p>
          <ul v-if="project.highlights.length" class="mt-2 space-y-1.5">
            <li
              v-for="(highlight, highlightIndex) in project.highlights"
              :key="`highlight-${highlightIndex}`"
              class="break-words text-sm leading-7 text-text-muted"
            >
              {{ highlight }}
            </li>
          </ul>
          <p v-if="project.metrics.length" class="mt-1.5 break-words text-xs leading-6 text-text">
            {{ project.metrics.join(' · ') }}
          </p>
        </article>
      </div>
    </section>

    <section v-if="data.education.length" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        教育经历
      </h2>
      <div class="mt-4 space-y-3">
        <div
          v-for="(item, index) in data.education"
          :key="`edu-${index}`"
          class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5"
        >
          <p class="min-w-0 break-words text-sm text-text">
            {{ item.school }}
            <span class="text-text-muted">{{ item.degree }} · {{ item.major }}</span>
          </p>
          <p class="tabular text-2xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
        </div>
      </div>
    </section>

    <section v-if="data.skills.length" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        技能
      </h2>
      <p class="mt-3 break-words text-sm leading-7 text-text-muted">{{ skillsText }}</p>
    </section>

    <section v-if="data.certificates.length" class="mt-10">
      <h2
        class="border-t border-border pt-2.5 text-3xs font-medium uppercase tracking-hero text-text-muted"
      >
        证书
      </h2>
      <p class="mt-3 break-words text-sm leading-7 text-text-muted">
        {{ data.certificates.join(' · ') }}
      </p>
    </section>
  </div>
</template>
