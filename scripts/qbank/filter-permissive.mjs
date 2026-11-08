#!/usr/bin/env node
// 过滤非宽松许可来源，生成可公开分发的题库文件。
// 依据：data/question-bank/LICENSES.md；导入器 QuestionBankImporter.EXCLUDED_REPOS 同步排除。
import fs from 'node:fs'
import path from 'node:path'

const EXCLUDED_REPOS = new Set([
  'DerekLin924/bat_code',
  '0voice/Campus_recruitment_interview_questions',
  'ByteByteGoHq/system-design-101',
  'ayush-that/codejeet',
])

const root = path.resolve(import.meta.dirname, '../..')
const input = path.join(root, 'data/question-bank/normalized/questions.jsonl')
const output = path.join(root, 'data/question-bank/normalized/questions-permissive.jsonl')

const lines = fs.readFileSync(input, 'utf8').split('\n').filter(Boolean)
const kept = []
const excluded = {}
for (const line of lines) {
  let record
  try {
    record = JSON.parse(line)
  } catch {
    continue
  }
  const repo = record.sourceRepo ?? 'unknown'
  if (EXCLUDED_REPOS.has(repo)) {
    excluded[repo] = (excluded[repo] ?? 0) + 1
    continue
  }
  kept.push(line)
}
fs.writeFileSync(output, `${kept.join('\n')}\n`, 'utf8')
console.log(`输入 ${lines.length} 条，保留 ${kept.length} 条，排除 ${JSON.stringify(excluded)}`)
console.log(`输出：${output}`)