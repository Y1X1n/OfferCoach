import type {
  EducationItem,
  ExperienceItem,
  ProjectItem,
  ResumeLayout,
  ResumeStructured,
} from '@/types'
import { resumeLayoutName } from '@/constants/resumeLayouts'

export interface ResumeExportInput {
  title: string
  summary?: string | undefined
  structured: ResumeStructured
  layout?: ResumeLayout | undefined
}

const LEVEL_LABELS: Record<string, string> = {
  BEGINNER: '了解',
  INTERMEDIATE: '熟练',
  ADVANCED: '精通',
}

/** HTML 转义：所有用户/解析内容进入打印页面之前必须经过此函数。 */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function text(value: string | undefined | null): string {
  return typeof value === 'string' ? value.trim() : ''
}

function contactLine(structured: ResumeStructured): string {
  const basic = structured.basic
  return [basic.city, basic.yearsOfExperience > 0 ? `${basic.yearsOfExperience} 年经验` : '', basic.phoneMasked, basic.emailMasked]
    .map((item) => text(item))
    .filter((item) => item.length > 0)
    .join(' · ')
}

function period(start: string, end: string): string {
  const left = text(start)
  const right = text(end)
  if (left && right) return `${left} – ${right}`
  return left || right
}

function safeFileBase(title: string): string {
  const base = title.trim().replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, ' ')
  return base.length > 0 ? base : 'resume'
}

export function buildResumeMarkdown(input: ResumeExportInput): string {
  const data = input.structured
  const basic = data.basic
  const lines: string[] = [`# ${text(input.title) || '我的简历'}`, '']

  if (text(input.summary)) lines.push(`> ${text(input.summary)}`, '')

  const contacts = contactLine(data)
  if (contacts) lines.push(contacts, '')
  if (text(basic.name)) lines.push(`**姓名**：${text(basic.name)}`, '')

  if (data.education.length > 0) {
    lines.push('## 教育经历', '')
    for (const item of data.education) {
      lines.push(`### ${text(item.school) || '未命名学校'}`)
      lines.push(`- 学历/专业：${text(item.degree) || '—'} · ${text(item.major) || '—'}`)
      const span = period(item.start, item.end)
      if (span) lines.push(`- 时间：${span}`)
      lines.push('')
    }
  }

  if (data.experiences.length > 0) {
    lines.push('## 工作经历', '')
    for (const item of data.experiences) {
      lines.push(`### ${text(item.company) || '未命名公司'} · ${text(item.title) || '—'}`)
      const span = period(item.start, item.end)
      if (span) lines.push(`- 时间：${span}`)
      for (const bullet of item.bullets.map((line) => text(line)).filter((line) => line)) {
        lines.push(`- ${bullet}`)
      }
      lines.push('')
    }
  }

  if (data.projects.length > 0) {
    lines.push('## 项目经历', '')
    for (const item of data.projects) {
      lines.push(`### ${text(item.name) || '未命名项目'}${text(item.role) ? ` · ${text(item.role)}` : ''}`)
      if (item.stack.length > 0) lines.push(`- 技术栈：${item.stack.map((s) => text(s)).filter(Boolean).join(' / ')}`)
      for (const highlight of item.highlights.map((line) => text(line)).filter((line) => line)) {
        lines.push(`- ${highlight}`)
      }
      for (const metric of item.metrics.map((line) => text(line)).filter((line) => line)) {
        lines.push(`- 结果：${metric}`)
      }
      lines.push('')
    }
  }

  if (data.skills.length > 0) {
    lines.push('## 技能', '')
    for (const skill of data.skills) {
      lines.push(
        `- ${text(skill.name) || '—'}：${LEVEL_LABELS[skill.level] ?? skill.level}${skill.years > 0 ? ` · ${skill.years} 年` : ''}`,
      )
    }
    lines.push('')
  }

  if (data.certificates.length > 0) {
    lines.push('## 证书', '')
    for (const certificate of data.certificates.map((line) => text(line)).filter((line) => line)) {
      lines.push(`- ${certificate}`)
    }
    lines.push('')
  }

  return lines.join('\n').trimEnd() + '\n'
}

export function buildResumeJson(input: ResumeExportInput): string {
  return JSON.stringify(
    {
      title: input.title,
      summary: input.summary ?? '',
      layout: input.layout ?? 'classic',
      layoutName: resumeLayoutName(input.layout ?? 'classic'),
      basic: input.structured.basic,
      education: input.structured.education,
      experiences: input.structured.experiences,
      projects: input.structured.projects,
      skills: input.structured.skills,
      certificates: input.structured.certificates,
    },
    null,
    2,
  )
}

function listItems(items: string[]): string {
  const valid = items.map((item) => text(item)).filter((item) => item.length > 0)
  if (valid.length === 0) return ''
  return `<ul class="bullets">${valid.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`
}

function renderEducation(items: EducationItem[]): string {
  if (items.length === 0) return ''
  const rows = items
    .map((item) => {
      const span = period(item.start, item.end)
      return `<div class="entry"><div class="entry-head"><span class="entry-title">${escapeHtml(
        text(item.school) || '未命名学校',
      )}</span><span class="entry-meta">${escapeHtml(span)}</span></div><p class="entry-sub">${escapeHtml(
        [text(item.degree), text(item.major)].filter(Boolean).join(' · '),
      )}</p></div>`
    })
    .join('')
  return `<section><h2>教育经历</h2>${rows}</section>`
}

function renderExperiences(items: ExperienceItem[]): string {
  if (items.length === 0) return ''
  const rows = items
    .map((item) => {
      const span = period(item.start, item.end)
      return `<div class="entry"><div class="entry-head"><span class="entry-title">${escapeHtml(
        text(item.company) || '未命名公司',
      )}<span class="entry-role"> · ${escapeHtml(text(item.title) || '—')}</span></span><span class="entry-meta">${escapeHtml(
        span,
      )}</span></div>${listItems(item.bullets)}</div>`
    })
    .join('')
  return `<section><h2>工作经历</h2>${rows}</section>`
}

function renderProjects(items: ProjectItem[]): string {
  if (items.length === 0) return ''
  const rows = items
    .map((item) => {
      const stack = item.stack.map((s) => text(s)).filter(Boolean)
      return `<div class="entry"><div class="entry-head"><span class="entry-title">${escapeHtml(
        text(item.name) || '未命名项目',
      )}<span class="entry-role"> · ${escapeHtml(text(item.role) || '—')}</span></span></div>${
        stack.length > 0 ? `<p class="entry-sub">技术栈：${escapeHtml(stack.join(' / '))}</p>` : ''
      }${listItems(item.highlights)}${
        item.metrics.length > 0
          ? `<p class="entry-sub">结果：${escapeHtml(
              item.metrics.map((m) => text(m)).filter(Boolean).join('；'),
            )}</p>`
          : ''
      }</div>`
    })
    .join('')
  return `<section><h2>项目经历</h2>${rows}</section>`
}

function renderSkills(structured: ResumeStructured): string {
  if (structured.skills.length === 0) return ''
  const items = structured.skills
    .map(
      (skill) =>
        `<li><span>${escapeHtml(text(skill.name) || '—')}</span><span class="skill-meta">${escapeHtml(
          LEVEL_LABELS[skill.level] ?? skill.level,
        )}${skill.years > 0 ? ` · ${skill.years} 年` : ''}</span></li>`,
    )
    .join('')
  return `<section><h2>技能</h2><ul class="skills">${items}</ul></section>`
}

function renderCertificates(structured: ResumeStructured): string {
  if (structured.certificates.length === 0) return ''
  return `<section><h2>证书</h2>${listItems(structured.certificates)}</section>`
}

const PRINT_STYLES = `
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #fff; color: #111827; }
  body { font-family: "Inter", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif; font-size: 13px; line-height: 1.65; }
  .page { width: 210mm; min-height: 297mm; margin: 0 auto; padding: 16mm 15mm; }
  h1 { margin: 0; font-size: 26px; line-height: 1.25; letter-spacing: .02em; }
  h2 { margin: 22px 0 8px; font-size: 11px; font-weight: 700; letter-spacing: .18em; text-transform: uppercase; color: #4b5563; border-bottom: 1px solid #d4d8de; padding-bottom: 4px; }
  p { margin: 0; }
  .header { text-align: center; margin-bottom: 18px; }
  .contacts { margin-top: 6px; font-size: 12px; color: #4b5563; }
  .summary { margin-top: 14px; color: #374151; }
  .entry { margin-top: 12px; break-inside: avoid; }
  .entry-head { display: flex; justify-content: space-between; gap: 12px; align-items: baseline; }
  .entry-title { font-weight: 600; font-size: 13.5px; }
  .entry-role { font-weight: 400; color: #4b5563; }
  .entry-meta { font-size: 11.5px; color: #6b7280; white-space: nowrap; font-variant-numeric: tabular-nums; }
  .entry-sub { color: #4b5563; font-size: 12px; margin-top: 2px; }
  .bullets { margin: 4px 0 0; padding-left: 16px; }
  .bullets li { margin: 2px 0; color: #374151; }
  .skills { list-style: none; margin: 6px 0 0; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 4px 24px; }
  .skills li { display: flex; justify-content: space-between; gap: 12px; border-bottom: 1px solid #eceef1; padding-bottom: 3px; }
  .skill-meta { color: #6b7280; font-size: 11.5px; }
  body[data-layout="modern"] h1 { text-align: left; font-size: 30px; }
  body[data-layout="modern"] .header { text-align: left; border-bottom: 2px solid #111827; padding-bottom: 12px; }
  body[data-layout="modern"] h2 { border-bottom: 0; border-left: 2px solid #111827; padding-left: 8px; }
  body[data-layout="compact"] .page { padding: 12mm 13mm; font-size: 12px; }
  body[data-layout="compact"] h1 { font-size: 22px; }
  body[data-layout="compact"] .header { text-align: left; }
  body[data-layout="compact"] h2 { margin: 14px 0 6px; }
  body[data-layout="compact"] .entry { margin-top: 8px; }
  body[data-layout="minimal"] .page { padding: 22mm 20mm; }
  body[data-layout="minimal"] h1 { font-size: 34px; font-weight: 500; }
  body[data-layout="minimal"] h2 { border-bottom: 0; letter-spacing: .24em; margin-top: 28px; }
  body[data-layout="minimal"] .entry { margin-top: 16px; }
  .print-hint { position: fixed; right: 16px; top: 16px; border: 1px solid #d4d8de; background: #f9fafb; color: #4b5563; border-radius: 6px; padding: 8px 12px; font-size: 12px; }
  @media print {
    .page { width: auto; min-height: 0; padding: 0; }
    .print-hint { display: none; }
    h2 { break-after: avoid; }
  }
  @page { size: A4; margin: 14mm; }
`

/** 生成独立打印文档：只含简历版式，所有文本均已转义。 */
export function buildResumeHtml(input: ResumeExportInput): string {
  const data = input.structured
  const basic = data.basic
  const contacts = contactLine(data)
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${escapeHtml(text(input.title) || '我的简历')}</title>
<style>${PRINT_STYLES}</style>
</head>
<body data-layout="${escapeHtml(input.layout ?? 'classic')}">
<div class="print-hint">在打印对话框中选择“另存为 PDF”</div>
<main class="page">
  <header class="header">
    <h1>${escapeHtml(basic.name || text(input.title) || '我的简历')}</h1>
    ${contacts ? `<p class="contacts">${escapeHtml(contacts)}</p>` : ''}
  </header>
  ${text(input.summary) ? `<p class="summary">${escapeHtml(text(input.summary))}</p>` : ''}
  ${renderExperiences(data.experiences)}
  ${renderProjects(data.projects)}
  ${renderEducation(data.education)}
  ${renderSkills(data)}
  ${renderCertificates(data)}
</main>
</body>
</html>`
}

function download(filename: string, content: string, mime: string): void {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function exportResumeMarkdown(input: ResumeExportInput): string {
  const content = buildResumeMarkdown(input)
  download(`${safeFileBase(input.title)}.md`, content, 'text/markdown')
  return content
}

export function exportResumeJson(input: ResumeExportInput): string {
  const content = buildResumeJson(input)
  download(`${safeFileBase(input.title)}.json`, content, 'application/json')
  return content
}

/** 新窗口打印：用户可在打印对话框中选择“另存为 PDF”。 */
export function exportResumePdf(input: ResumeExportInput): void {
  const win = window.open('', '_blank', 'width=900,height=1000')
  if (!win) throw new Error('浏览器拦截了新窗口，请允许弹窗后重试')
  win.document.open()
  win.document.write(buildResumeHtml(input))
  win.document.close()
  win.focus()
  const print = () => {
    win.setTimeout(() => {
      win.print()
    }, 200)
  }
  if (win.document.readyState === 'complete') print()
  else win.addEventListener('load', print, { once: true })
}