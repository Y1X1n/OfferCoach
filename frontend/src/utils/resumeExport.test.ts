import { describe, expect, it } from 'vitest'
import type { ResumeStructured } from '@/types'
import {
  buildResumeHtml,
  buildResumeJson,
  buildResumeMarkdown,
  escapeHtml,
} from './resumeExport'

const structured: ResumeStructured = {
  basic: {
    name: '张三',
    city: '上海',
    yearsOfExperience: 3,
    phoneMasked: '138****6721',
    emailMasked: 'zhang***@example.com',
  },
  education: [
    {
      school: '华东理工大学',
      degree: '本科',
      major: '计算机科学与技术',
      start: '2018-09',
      end: '2022-06',
    },
  ],
  experiences: [
    {
      company: '云帆科技',
      title: '后端开发工程师',
      start: '2022-07',
      end: '至今',
      bullets: ['负责订单履约链路重构'],
    },
  ],
  projects: [
    {
      name: '订单履约',
      role: '核心开发',
      stack: ['Java', 'Redis'],
      highlights: ['拆分履约编排'],
      metrics: ['P99 从 680ms 降到 210ms'],
    },
  ],
  skills: [{ name: 'Java', level: 'ADVANCED', years: 3 }],
  certificates: ['CET-6'],
}

const input = {
  title: '张三 · 后端开发',
  summary: '3 年后端经验',
  structured,
  layout: 'modern' as const,
}

describe('buildResumeMarkdown', () => {
  it('包含基本信息、教育、工作、项目、技能与证书', () => {
    const markdown = buildResumeMarkdown(input)
    expect(markdown).toContain('# 张三 · 后端开发')
    expect(markdown).toContain('## 教育经历')
    expect(markdown).toContain('## 工作经历')
    expect(markdown).toContain('## 项目经历')
    expect(markdown).toContain('## 技能')
    expect(markdown).toContain('## 证书')
    expect(markdown).toContain('P99 从 680ms 降到 210ms')
  })
})

describe('buildResumeJson', () => {
  it('输出可解析的 JSON 且保留版式', () => {
    const parsed = JSON.parse(buildResumeJson(input)) as {
      title: string
      layout: string
      basic: { name: string }
    }
    expect(parsed.title).toBe('张三 · 后端开发')
    expect(parsed.layout).toBe('modern')
    expect(parsed.basic.name).toBe('张三')
  })
})

describe('buildResumeHtml', () => {
  it('转义脚本与属性，避免 XSS', () => {
    const html = buildResumeHtml({
      ...input,
      title: '<script>alert(1)</script>',
      structured: {
        ...structured,
        basic: { ...structured.basic, name: '<img src=x onerror=alert(1)>' },
        experiences: [
          { ...structured.experiences[0]!, bullets: ['</script><script>alert(2)</script>'] },
        ],
      },
    })

    expect(html).not.toContain('<script>alert')
    expect(html).not.toContain('<img src=x')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(html).toContain('data-layout="modern"')
  })

  it('escapeHtml 处理全部敏感字符', () => {
    expect(escapeHtml(`&<>"'`)).toBe('&amp;&lt;&gt;&quot;&#39;')
  })
})