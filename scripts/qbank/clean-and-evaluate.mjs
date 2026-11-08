#!/usr/bin/env node
// 题库审计、清洗与出题相关性评估。
// 输入：data/question-bank/normalized/questions-permissive.jsonl
// 输出：questions-clean.jsonl、audit-report.md
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '../..')
const input = path.join(root, 'data/question-bank/normalized/questions-permissive.jsonl')
const cleanOutput = path.join(root, 'data/question-bank/normalized/questions-clean.jsonl')
const reportOutput = path.join(root, 'data/question-bank/audit-report.md')

const FAMILIES = ['后端', '前端', '算法', '数据', 'AI-Agent', '系统设计', '测试', '通用']
const FAMILY_ALIASES = new Map([
  ['backend', '后端'],
  ['后端开发', '后端'],
  ['服务端', '后端'],
  ['frontend', '前端'],
  ['front-end', '前端'],
  ['ai', 'AI-Agent'],
  ['agent', 'AI-Agent'],
  ['ai-agent', 'AI-Agent'],
  ['ai_agent', 'AI-Agent'],
  ['data', '数据'],
  ['数据分析', '数据'],
  ['algorithm', '算法'],
  ['test', '测试'],
  ['qa', '测试'],
  ['system-design', '系统设计'],
  ['system_design', '系统设计'],
  ['general', '通用'],
])

const FAMILY_RULES = [
  ['AI-Agent', ['大模型', 'LLM', 'Agent', 'RAG', 'MCP', 'Prompt', '提示词', '向量', '推理', '微调', '模型', 'AI']],
  ['前端', ['前端', 'Vue', 'React', 'JavaScript', 'TypeScript', 'CSS', '浏览器', '小程序', 'Web']],
  ['测试', ['测试', 'QA', '自动化测试', '用例', '质量保障']],
  ['数据', ['数据分析', '数据开发', '数仓', '数据仓库', '指标体系', 'ETL', '报表', '数据平台']],
  ['算法', ['算法', '数据结构', 'LeetCode', '机器学习', '深度学习', '推荐', 'NLP', 'CV']],
  ['系统设计', ['系统设计', '架构师', '架构设计', '高可用', '高并发']],
  ['后端', ['后端', '服务端', 'Java', 'Spring', 'Go', 'MySQL', 'Redis', 'Kafka', '微服务', '分布式']],
]

function normalizeFamily(raw) {
  const value = (raw ?? '').trim()
  if (!value) return '通用'
  return FAMILY_ALIASES.get(value.toLowerCase()) ?? (FAMILIES.includes(value) ? value : value)
}

function inferFamily(title, text) {
  const titleText = (title ?? '').toLowerCase()
  const bodyText = `${title ?? ''}\n${text ?? ''}`.toLowerCase()
  for (const [family, keywords] of FAMILY_RULES) {
    if (keywords.some((keyword) => titleText.includes(keyword.toLowerCase()))) {
      return family
    }
  }
  for (const [family, keywords] of FAMILY_RULES) {
    if (keywords.some((keyword) => bodyText.includes(keyword.toLowerCase()))) {
      return family
    }
  }
  return '通用'
}

function cleanRow(row) {
  const stem = String(row.stem ?? '').trim()
  if (!stem) return { drop: 'empty-stem' }
  if (/^https?:\/\//i.test(stem)) return { drop: 'link-only' }
  if (/^#{1,6}\s/.test(stem)) return { drop: 'heading' }
  if (stem.length < 8) return { drop: 'too-short' }
  if (!/[\p{L}\p{N}]/u.test(stem)) return { drop: 'no-text' }

  const family = normalizeFamily(row.jobFamily)
  const answer = String(row.referenceAnswer ?? '').trim()
  const points = Array.isArray(row.referencePoints) ? row.referencePoints.filter(Boolean) : []
  let quality = 0.4
  if (answer.length >= 30) quality += 0.2
  if (points.length >= 2) quality += 0.15
  if ((row.company ?? '').trim()) quality += 0.1
  if (Array.isArray(row.tags) && row.tags.filter(Boolean).length >= 2) quality += 0.1
  if (stem.length >= 12 && stem.length <= 200) quality += 0.05
  quality = Math.min(1, Math.round(quality * 100) / 100)

  return {
    row: {
      ...row,
      stem,
      jobFamily: family,
      qualityScore: quality,
      hasAnswer: answer.length >= 30,
    },
  }
}

function bigrams(text) {
  const normalized = String(text ?? '').toLowerCase().replace(/[\s\p{P}\p{S}，。；：、“”（）【】]+/gu, '')
  const grams = new Set()
  for (let index = 0; index + 1 < normalized.length; index += 1) {
    grams.add(normalized.slice(index, index + 2))
  }
  return grams
}

function similarity(first, second) {
  const left = bigrams(first)
  const right = bigrams(second)
  if (!left.size || !right.size) return 0
  let intersection = 0
  for (const gram of left) if (right.has(gram)) intersection += 1
  return intersection / (left.size + right.size - intersection)
}

function scoreRow(row, family, keywords, queryText, opts) {
  const title = String(row.title ?? '').toLowerCase()
  const stem = String(row.stem ?? '').toLowerCase()
  const tags = (row.tags ?? []).map((tag) => String(tag).toLowerCase())
  let score = 0
  for (const keyword of keywords) {
    if (title.includes(keyword)) score += 2.0
    if (stem.includes(keyword)) score += 1.5
    for (const tag of tags) {
      if (tag === keyword) score += 1.5
      else if (tag.includes(keyword)) score += 0.8
    }
  }
  score += 2.5 * similarity(queryText, `${title} ${stem} ${tags.join(' ')}`)
  if (family) {
    if (normalizeFamily(row.jobFamily) === family) score += 1.5
    else if (normalizeFamily(row.jobFamily) === '通用') score += 0.2
    else score -= 1.0
  }
  if (opts.answerBonus) {
    if (row.hasAnswer) score += 0.8
    if ((row.referencePoints ?? []).filter(Boolean).length) score += 0.2
    score += (row.qualityScore ?? 0) * 0.5
  }
  return Math.round(score * 1000) / 1000
}

const profiles = [
  {
    name: '后端',
    title: '高级后端工程师',
    text: '负责高并发订单系统的架构设计与核心链路开发，保障系统稳定性；参与容量评估、缓存一致性、消息可靠性方案设计与落地；推动服务拆分、灰度发布与故障复盘。3 年以上 Java 后端经验，熟悉 Spring Boot、Spring Cloud、MySQL、Redis、Kafka；具备高并发、分布式事务与稳定性治理经验。',
    keywords: ['java', 'spring', 'mysql', 'redis', 'kafka', '高并发', '缓存', '消息', '分布式', '订单'],
    core: ['java', 'spring', 'mysql', 'redis', 'kafka', '微服务', '订单'],
  },
  {
    name: 'AI-Agent',
    title: 'AI Agent 工程师',
    text: '负责大模型 Agent 应用设计与开发，熟悉 RAG、MCP、Prompt 工程、向量检索与工具调用，具备模型评测与线上稳定性经验。',
    keywords: ['大模型', 'agent', 'rag', 'mcp', 'prompt', '向量', '模型', '推理'],
    core: ['大模型', 'agent', 'rag', 'mcp', 'prompt', '推理'],
  },
  {
    name: '前端',
    title: '高级前端工程师',
    text: '负责 Web 前端架构与性能优化，熟悉 Vue、React、TypeScript、CSS、浏览器渲染与工程化。',
    keywords: ['vue', 'react', 'typescript', 'javascript', 'css', '浏览器', '前端'],
    core: ['vue', 'react', 'typescript', 'javascript', 'css'],
  },
  {
    name: '数据',
    title: '数据分析师',
    text: '负责业务数据分析、指标体系与数仓建设，熟悉 SQL、ETL、报表与数据可视化。',
    keywords: ['sql', '数据', '指标', '数仓', 'etl', '报表', '分析'],
    core: ['sql', '数仓', '指标', 'etl', '报表'],
  },
]

function keywordHit(row, keywords) {
  const text = `${row.title ?? ''} ${row.stem ?? ''} ${(row.tags ?? []).join(' ')}`.toLowerCase()
  return keywords.some((keyword) => text.includes(keyword))
}

function familyDimensionMatrix(rows) {
  const table = {}
  for (const row of rows) {
    const family = row.jobFamily || '通用'
    table[family] ??= {}
    table[family][row.dimension] = (table[family][row.dimension] ?? 0) + 1
  }
  return table
}

function evaluate(rows, profile) {
  const keywords = profile.keywords.map((keyword) => keyword.toLowerCase())
  const family = inferFamily(profile.title, profile.text)
  const queryText = `${keywords.join(' ')} ${family}`

  // 现状：JD 岗位族为空时，取全库前 200 条后按关键词排序
  const baselinePool = rows.slice(0, 200)
  const baseline = baselinePool
    .map((row) => ({ row, score: scoreRow(row, null, keywords, queryText, { answerBonus: false }) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 20)

  // 修复后：主岗位族 + 通用 + 系统设计，按关键词/答案/质量排序
  const familyRows = rows.filter((row) => ['通用', family, '系统设计'].includes(normalizeFamily(row.jobFamily)))
  const fixed = familyRows
    .map((row) => ({ row, score: scoreRow(row, family, keywords, queryText, { answerBonus: true }) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 20)

  const summarize = (items) => {
    const familyMix = {}
    const dimensionMix = {}
    const allowed = new Set([family, '通用', '系统设计'])
    let hits = 0
    let coreHits = 0
    let primary = 0
    let offDomain = 0
    for (const item of items) {
      familyMix[item.row.jobFamily] = (familyMix[item.row.jobFamily] ?? 0) + 1
      dimensionMix[item.row.dimension] = (dimensionMix[item.row.dimension] ?? 0) + 1
      if (keywordHit(item.row, keywords)) hits += 1
      if (keywordHit(item.row, core.map((keyword) => keyword.toLowerCase()))) coreHits += 1
      if (normalizeFamily(item.row.jobFamily) === family) primary += 1
      if (!allowed.has(normalizeFamily(item.row.jobFamily))) offDomain += 1
    }
    const total = items.length || 1
    return {
      familyMix,
      dimensionMix,
      hitRate: Math.round((hits / total) * 100),
      coreHitRate: Math.round((coreHits / total) * 100),
      primaryShare: Math.round((primary / total) * 100),
      offDomainShare: Math.round((offDomain / total) * 100),
      top: items.slice(0, 8).map((item) => `${item.row.dimension}/${item.row.jobFamily}｜${item.row.title}`),
    }
  }

  const core = profile.core ?? keywords
  return { profile: profile.name, inferredFamily: family, baseline: summarize(baseline), fixed: summarize(fixed) }
}

const rawLines = fs.readFileSync(input, 'utf8').split('\n').filter(Boolean)
const rows = rawLines.map((line) => JSON.parse(line))
const dropped = {}
const cleaned = []
for (const row of rows) {
  const result = cleanRow(row)
  if (result.drop) {
    dropped[result.drop] = (dropped[result.drop] ?? 0) + 1
    continue
  }
  cleaned.push(result.row)
}

fs.writeFileSync(cleanOutput, `${cleaned.map((row) => JSON.stringify(row)).join('\n')}\n`, 'utf8')

const matrix = familyDimensionMatrix(cleaned)
const evaluations = profiles.map((profile) => evaluate(cleaned, profile))
const qualityBuckets = { '0.4-0.6': 0, '0.6-0.8': 0, '0.8-1.0': 0 }
for (const row of cleaned) {
  if (row.qualityScore < 0.6) qualityBuckets['0.4-0.6'] += 1
  else if (row.qualityScore < 0.8) qualityBuckets['0.6-0.8'] += 1
  else qualityBuckets['0.8-1.0'] += 1
}

const report = [
  '# 题库审计与出题相关性报告',
  '',
  `> 生成时间：${new Date().toISOString()}；输入 ${rows.length} 条，清洗后 ${cleaned.length} 条。`,
  '',
  '## 清洗',
  '',
  `- 剔除：${Object.entries(dropped).map(([reason, count]) => `${reason} ${count}`).join('、') || '无'}`,
  '- 岗位族别名归一化；质量分按答案、参考要点、公司、标签与题干长度计算（0–1）。',
  '',
  '## 质量分布',
  '',
  ...Object.entries(qualityBuckets).map(([bucket, count]) => `- ${bucket}：${count}`),
  '',
  '## 岗位族 × 维度',
  '',
  '| 岗位族 | DEPTH | PROJECT | BEHAVIOR | FIT |',
  '|---|---:|---:|---:|---:|',
  ...Object.entries(matrix).map(([family, dims]) => `| ${family} | ${dims.DEPTH ?? 0} | ${dims.PROJECT ?? 0} | ${dims.BEHAVIOR ?? 0} | ${dims.FIT ?? 0} |`),
  '',
  '## 出题相关性（top 20）',
  '',
]

for (const result of evaluations) {
  report.push(`### ${result.profile}（推断岗位族：${result.inferredFamily}）`)
  report.push('')
  report.push(`| 方案 | 核心命中 | 主题命中 | 本岗位族占比 | 非本域占比 |`)
  report.push('|---|---:|---:|---:|---:|')
  report.push(`| 现状（全库前 200） | ${result.baseline.coreHitRate}% | ${result.baseline.hitRate}% | ${result.baseline.primaryShare}% | ${result.baseline.offDomainShare}% |`)
  report.push(`| 修复后（岗位族优先） | ${result.fixed.coreHitRate}% | ${result.fixed.hitRate}% | ${result.fixed.primaryShare}% | ${result.fixed.offDomainShare}% |`)
  report.push('')
  report.push(`现状示例：${result.baseline.top.slice(0, 5).join('；')}`)
  report.push('')
  report.push('修复后示例：')
  result.fixed.top.forEach((item) => report.push(`- ${item}`))
  report.push('')
}

fs.writeFileSync(reportOutput, `${report.join('\n')}\n`, 'utf8')
console.log(`cleaned ${rows.length} -> ${cleaned.length}, dropped ${JSON.stringify(dropped)}`)
for (const result of evaluations) {
  console.log(
    `${result.profile}: family=${result.inferredFamily} baseline(core=${result.baseline.coreHitRate}% off=${result.baseline.offDomainShare}%) fixed(core=${result.fixed.coreHitRate}% off=${result.fixed.offDomainShare}%)`,
  )
  console.log('  baseline families', result.baseline.familyMix)
  console.log('  fixed families   ', result.fixed.familyMix)
}
console.log(`report: ${reportOutput}`)
console.log(`clean:  ${cleanOutput}`)