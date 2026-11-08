import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://127.0.0.1:5174'
const OUT = path.resolve('artifacts')
await mkdir(OUT, { recursive: true })

const browser = await chromium.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--no-sandbox'],
})
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  locale: 'zh-CN',
})
const page = await context.newPage()
const errors = []
page.on('console', (message) => {
  if (message.type() === 'error') errors.push(`console: ${message.text()}`)
})
page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))

async function shot(name) {
  await page.waitForTimeout(300)
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true })
  console.log(`shot ${name}`)
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: '填入' }).click()
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => url.pathname === '/', { timeout: 8000 })

await page.goto(`${BASE}/resumes`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('21-resume-layout-classic')

await page.getByRole('button', { name: '选择版式' }).click()
await page.getByRole('dialog').waitFor()
await shot('22-resume-layout-picker')
await page.getByRole('button', { name: '应用版式：现代双栏' }).click()
await page.getByRole('dialog').waitFor({ state: 'hidden' })
await page.waitForTimeout(400)
await shot('23-resume-layout-modern')

await page.getByRole('button', { name: '版式', exact: true }).click()
await page.getByRole('dialog').waitFor()
await page.getByRole('button', { name: '应用版式：极简留白' }).click()
await page.getByRole('dialog').waitFor({ state: 'hidden' })
await page.waitForTimeout(400)
await shot('24-resume-layout-minimal')

await page.getByRole('button', { name: '编辑内容' }).click()
await page.getByRole('dialog').waitFor()
await page.getByLabel('简历标题').fill('张三 · 后端开发（已编辑）')
await page
  .getByLabel('简历摘要')
  .fill(
    '3 年后端经验，主栈 Java / Spring Boot；负责订单履约与支付链路，具备高并发、缓存一致性与消息队列实践。正在补充 Kubernetes 与可观测性经验。',
  )
await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()
await page.getByRole('dialog').waitFor({ state: 'hidden' })
await page.waitForTimeout(400)
await shot('25-resume-edited')

await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(300)
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
console.log('resume mobile overflow:', overflow)
await page.screenshot({ path: path.join(OUT, '26-resume-mobile.png'), fullPage: false })

await browser.close()
if (errors.length) {
  console.error('BROWSER_ERRORS')
  for (const error of errors) console.error(error)
  process.exitCode = 1
} else {
  console.log('BROWSER_ERRORS none')
}
