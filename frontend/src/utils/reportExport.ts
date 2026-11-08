/**
 * 报告导出文件名：公司-岗位-YYMMDD，例如 云帆科技-高级后端工程师-261107。
 * 仅用于浏览器打印/另存 PDF 的默认文件名，不进入后端存储。
 */
export function buildReportFileName(
  company: string | null | undefined,
  role: string | null | undefined,
  date: string | null | undefined,
  now: Date = new Date(),
): string {
  return `${safeSegment(company, '目标公司')}-${safeSegment(role, '模拟面试')}-${dateStamp(date, now)}`
}

function safeSegment(value: string | null | undefined, fallback: string): string {
  const normalized = (value ?? '').trim()
  if (!normalized) return fallback
  return normalized
    .replace(/[\\/:*?"<>|\r\n\t]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60) || fallback
}

function dateStamp(value: string | null | undefined, now: Date): string {
  const parsed = value ? new Date(value) : null
  const source = parsed && !Number.isNaN(parsed.getTime()) ? parsed : now
  const year = String(source.getFullYear()).slice(2)
  const month = String(source.getMonth() + 1).padStart(2, '0')
  const day = String(source.getDate()).padStart(2, '0')
  return `${year}${month}${day}`
}