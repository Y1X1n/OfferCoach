<script setup lang="ts">
import { computed } from 'vue'
import type { Resume, SkillItem } from '@/types'

const props = defineProps<{ resume: Resume }>()

const data = computed(() => props.resume.structured)
const basic = computed(() => data.value.basic)

const levelLabel: Record<SkillItem['level'], string> = {
  BEGINNER: '了解',
  INTERMEDIATE: '熟练',
  ADVANCED: '精通',
}
</script>

<template>
  <div class="flex min-h-full flex-col text-text">
    <header class="border-b border-border pb-5">
      <h1 class="break-words text-3xl font-semibold leading-tight tracking-tight text-text">
        {{ basic.name }}
      </h1>
      <p class="mt-1.5 text-xs text-text-muted">
        {{ basic.city }} · {{ basic.yearsOfExperience }} 年工作经验
      </p>
    </header>

    <div class="mt-6 flex flex-col gap-8 sm:flex-row">
      <div class="min-w-0 flex-1 space-y-6">
        <section v-if="resume.summary">
          <h2
            class="border-l-2 border-brand pl-2 text-2xs font-semibold uppercase tracking-label text-text"
          >
            个人简介
          </h2>
          <p class="mt-2.5 text-sm leading-6 text-text-muted">{{ resume.summary }}</p>
        </section>

        <section v-if="data.experiences.length">
          <h2
            class="border-l-2 border-brand pl-2 text-2xs font-semibold uppercase tracking-label text-text"
          >
            工作经历
          </h2>
          <div class="mt-3 space-y-4">
            <article v-for="(item, index) in data.experiences" :key="`exp-${index}`">
              <h3 class="break-words text-sm font-semibold text-text">{{ item.title }}</h3>
              <p
                class="mt-0.5 flex flex-wrap items-baseline justify-between gap-x-3 text-xs text-text-muted"
              >
                <span>{{ item.company }}</span>
                <span class="tabular">{{ item.start }} – {{ item.end }}</span>
              </p>
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

        <section v-if="data.projects.length">
          <h2
            class="border-l-2 border-brand pl-2 text-2xs font-semibold uppercase tracking-label text-text"
          >
            项目经历
          </h2>
          <div class="mt-3 space-y-4">
            <article v-for="(project, index) in data.projects" :key="`project-${index}`">
              <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
                <h3 class="min-w-0 break-words text-sm font-semibold text-text">
                  {{ project.name }}
                </h3>
                <p class="text-2xs text-text-muted">{{ project.role }}</p>
              </div>
              <div v-if="project.stack.length" class="mt-1.5 flex flex-wrap gap-1">
                <span
                  v-for="tech in project.stack"
                  :key="tech"
                  class="rounded-sm border border-border px-1.5 py-0.5 text-3xs text-text-muted"
                >
                  {{ tech }}
                </span>
              </div>
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
              <ul v-if="project.metrics.length" class="mt-1.5 space-y-0.5">
                <li
                  v-for="(metric, metricIndex) in project.metrics"
                  :key="`metric-${metricIndex}`"
                  class="break-words text-xs leading-5 text-text"
                >
                  — {{ metric }}
                </li>
              </ul>
            </article>
          </div>
        </section>
      </div>

      <aside
        class="min-w-0 shrink-0 space-y-6 border-t border-border pt-6 sm:w-52 sm:border-l sm:border-t-0 sm:pl-6 sm:pt-0"
      >
        <section>
          <h2 class="text-3xs font-semibold uppercase tracking-badge text-text-muted">联系方式</h2>
          <ul class="mt-2.5 space-y-2 text-xs leading-5">
            <li class="break-words">
              <span class="block text-3xs uppercase tracking-wider text-text-muted">城市</span>
              <span class="text-text">{{ basic.city }}</span>
            </li>
            <li class="break-words">
              <span class="block text-3xs uppercase tracking-wider text-text-muted">手机</span>
              <span class="tabular text-text">{{ basic.phoneMasked }}</span>
            </li>
            <li class="break-all">
              <span class="block text-3xs uppercase tracking-wider text-text-muted">邮箱</span>
              <span class="text-text">{{ basic.emailMasked }}</span>
            </li>
            <li>
              <span class="block text-3xs uppercase tracking-wider text-text-muted">经验</span>
              <span class="tabular text-text">{{ basic.yearsOfExperience }} 年</span>
            </li>
          </ul>
        </section>

        <section v-if="data.skills.length">
          <h2 class="text-3xs font-semibold uppercase tracking-badge text-text-muted">技能</h2>
          <ul class="mt-2.5 space-y-2">
            <li v-for="skill in data.skills" :key="skill.name" class="min-w-0">
              <p class="truncate text-xs font-medium text-text">{{ skill.name }}</p>
              <p class="tabular text-3xs text-text-muted">
                {{ levelLabel[skill.level] }} · {{ skill.years }} 年
              </p>
            </li>
          </ul>
        </section>

        <section v-if="data.education.length">
          <h2 class="text-3xs font-semibold uppercase tracking-badge text-text-muted">教育经历</h2>
          <ul class="mt-2.5 space-y-2.5">
            <li v-for="(item, index) in data.education" :key="`edu-${index}`" class="min-w-0">
              <p class="break-words text-xs font-medium text-text">{{ item.school }}</p>
              <p class="break-words text-2xs text-text-muted">
                {{ item.degree }} · {{ item.major }}
              </p>
              <p class="tabular text-3xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
            </li>
          </ul>
        </section>

        <section v-if="data.certificates.length">
          <h2 class="text-3xs font-semibold uppercase tracking-badge text-text-muted">证书</h2>
          <ul class="mt-2.5 space-y-1">
            <li
              v-for="certificate in data.certificates"
              :key="certificate"
              class="break-words text-xs leading-5 text-text"
            >
              {{ certificate }}
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>
