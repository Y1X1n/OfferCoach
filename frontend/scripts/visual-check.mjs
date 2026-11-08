import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.env.APP_URL || 'http://127.0.0.1:5174'
const OUT = path.resolve('artifacts')
await mkdir(OUT, { recursive: true })

const browser = await chromium.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--disable-gpu', '--no-sandbox'],
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
  await page.waitForTimeout(350)
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true })
  console.log(`shot ${name}`)
}

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await shot('01-login')

await page.getByRole('button', { name: '填入' }).click()
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => url.pathname === '/', { timeout: 8000 })
await page.waitForSelector('h1')
await shot('02-dashboard')

await page.goto(`${BASE}/resumes`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('03-resumes')

await page.goto(`${BASE}/jds`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('04-jds')

await page.goto(`${BASE}/interview/new`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('05-interview-setup')

const startButton = page.getByRole('button', { name: '开始模拟面试' })
const groups = page.getByRole('radiogroup')
await groups.nth(0).getByRole('radio').first().click()
await groups.nth(1).getByRole('radio').first().click()
await page.waitForTimeout(200)
await startButton.waitFor({ state: 'visible' })
await startButton.click()
await page.waitForURL(/\/interview\//, { timeout: 8000 })
await page.waitForSelector('textarea')
await shot('06-interview-room')

await page
  .locator('textarea')
  .first()
  .fill(
    '我负责订单履约链路重构，目标是降低下单接口延迟并提升大促稳定性。首先我梳理了同步调用链，发现库存预占是主要瓶颈；其次我对比了单体内优化与拆分履约编排两个方案，最终选择拆分并保留同步兜底。为了保证一致性，我们用延迟双删和版本号处理缓存，并用 Kafka 削峰。上线后 QPS 从 800 提升到 2400，P99 从 680ms 降到 210ms，大促期间没有出现超卖。',
  )
await page.getByRole('button', { name: '提交回答' }).click()
await page.waitForTimeout(700)
const streamChars = await page
  .locator('pre')
  .first()
  .innerText()
  .catch(() => '')
console.log('stream chars at 700ms:', streamChars.length)
const gridBoxes = await page.evaluate(() => {
  const grid = document.querySelector('main .grid')
  if (!grid) return []
  return [...grid.children].map((element) => {
    const box = element.getBoundingClientRect()
    return {
      x: Math.round(box.x),
      y: Math.round(box.y),
      width: Math.round(box.width),
      height: Math.round(box.height),
    }
  })
})
console.log('room grid boxes:', JSON.stringify(gridBoxes))
await shot('07a-feedback-streaming')
await page.waitForFunction(
  () => {
    const buttons = [...document.querySelectorAll('button')]
    const next = buttons.find((button) => button.textContent?.includes('下一题'))
    return Boolean(next && !(next instanceof HTMLButtonElement && next.disabled))
  },
  null,
  { timeout: 20000 },
)
await shot('07b-feedback-done')

async function waitAdvanceEnabled() {
  await page.waitForFunction(
    () => {
      const buttons = [...document.querySelectorAll('button')]
      const advance = buttons.find((button) => /下一题|生成报告/.test(button.textContent ?? ''))
      return Boolean(advance && !(advance instanceof HTMLButtonElement && advance.disabled))
    },
    null,
    { timeout: 20000 },
  )
}

for (let index = 2; index <= 8; index += 1) {
  await page.getByRole('button', { name: '下一题' }).click()
  await page.waitForSelector('textarea')
  await page
    .locator('textarea')
    .first()
    .fill(
      `关于第 ${index} 题，我先交代背景与目标，再说明我承担的具体职责。首先我梳理了关键链路和风险点，其次对比了两个备选方案，最终根据稳定性与成本选择了拆分方案，并补充了回滚与监控。上线后核心指标提升了约 30%，故障恢复时间从 10 分钟降到 2 分钟。`,
    )
  await page.getByRole('button', { name: '提交回答' }).click()
  await page.waitForTimeout(400)
  await waitAdvanceEnabled()
}

await page.getByRole('button', { name: '结束面试' }).click()
await page.getByRole('dialog').waitFor()
let focusTrapped = true
for (let index = 0; index < 8; index += 1) {
  await page.keyboard.press('Tab')
  const inside = await page.evaluate(() =>
    Boolean(document.activeElement?.closest('[role="dialog"]')),
  )
  if (!inside) focusTrapped = false
}
console.log('dialog focus trapped:', focusTrapped)
await page.keyboard.press('Escape')
await page.getByRole('dialog').waitFor({ state: 'hidden' })
const focusRestored = await page.evaluate(() =>
  document.activeElement?.textContent?.includes('结束面试'),
)
console.log('dialog focus restored:', focusRestored)
await page.getByRole('button', { name: '结束面试' }).click()
await page.getByRole('button', { name: '结束并生成报告' }).click()
await page.waitForURL(/\/reports\//, { timeout: 8000 })
await page.waitForSelector('h1')
await shot('08-report-detail')

await page.goto(`${BASE}/reports`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('09-reports')

await page.goto(`${BASE}/plans`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('10-plans')

await page.goto(`${BASE}/settings`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('11-settings')

await page.setViewportSize({ width: 390, height: 844 })
await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await shot('12-mobile-dashboard')

await browser.close()

if (errors.length) {
  console.error('BROWSER_ERRORS')
  for (const error of errors) console.error(error)
  process.exitCode = 1
} else {
  console.log('BROWSER_ERRORS none')
}
