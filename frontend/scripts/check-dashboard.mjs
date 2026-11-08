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

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: '填入' }).click()
await page.getByRole('button', { name: '登录' }).click()
await page.waitForURL((url) => url.pathname === '/', { timeout: 8000 })
await page.waitForSelector('h1')
await page.waitForTimeout(800)
await page.screenshot({ path: path.join(OUT, '19-dashboard-minimal.png'), fullPage: true })

const geometry = await page.evaluate(() => {
  const main = document.querySelector('main')
  const cards = [...document.querySelectorAll('main .grid > div > .rounded-lg')]
  const reportCard = [...document.querySelectorAll('main h2')]
    .find((heading) => heading.textContent?.includes('最近报告'))
    ?.closest('.rounded-lg')
  const box = (element) => {
    if (!element) return null
    const rect = element.getBoundingClientRect()
    return {
      x: Math.round(rect.x),
      y: Math.round(rect.y),
      width: Math.round(rect.width),
      height: Math.round(rect.height),
    }
  }
  return {
    viewport: { width: innerWidth, height: innerHeight },
    main: box(main),
    reportCard: box(reportCard),
    cards: cards.slice(0, 3).map(box),
    documentHeight: document.documentElement.scrollHeight,
  }
})
console.log('geometry:', JSON.stringify(geometry))

await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(400)
await page.screenshot({ path: path.join(OUT, '20-dashboard-minimal-mobile.png'), fullPage: false })
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
console.log('mobile overflow:', overflow)
const offenders = await page.evaluate(() =>
  [...document.querySelectorAll('*')]
    .map((element) => {
      const rect = element.getBoundingClientRect()
      return {
        tag: element.tagName,
        class: typeof element.className === 'string' ? element.className.slice(0, 90) : '',
        left: Math.round(rect.left),
        right: Math.round(rect.right),
        width: Math.round(rect.width),
      }
    })
    .filter((item) => item.right > window.innerWidth + 1 || item.left < -1)
    .slice(0, 12),
)
console.log('mobile offenders:', JSON.stringify(offenders))

await browser.close()
if (errors.length) {
  console.error('BROWSER_ERRORS')
  for (const error of errors) console.error(error)
  process.exitCode = 1
} else {
  console.log('BROWSER_ERRORS none')
}
