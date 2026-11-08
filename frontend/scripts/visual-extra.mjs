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

await page.goto(`${BASE}/register`, { waitUntil: 'networkidle' })
await page.locator('#register-email').fill(`newuser_${Date.now()}@example.com`)
await page.locator('#register-nickname').fill('新用户')
await page.locator('#register-password').fill('Passw0rd!')
await page.locator('#register-confirm').fill('Passw0rd!')
await page.locator('#register-verifyCode').fill('123456')
await page.getByRole('button', { name: '创建账号' }).click()
await page.waitForURL((url) => url.pathname === '/', { timeout: 8000 })
await page.waitForSelector('h1')
await shot('13-register-dashboard')

await page.goto(`${BASE}/resumes`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await page.locator('input[type=file]').setInputFiles(path.join(OUT, 'sample-resume.pdf'))
await page.waitForTimeout(2200)
await shot('14-resume-uploaded')

await page.goto(`${BASE}/jds`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
await page.getByLabel('职位名称').fill('资深后端工程师')
await page.getByLabel('公司').fill('星河数据')
await page
  .getByLabel('JD 原文')
  .fill(
    '负责交易平台后端架构与稳定性治理；要求 5 年以上 Java 经验，熟悉 Spring Boot、MySQL、Redis、Kafka 与 Kubernetes；有高并发系统设计与跨团队推动经验。',
  )
await page.getByRole('button', { name: '分析岗位匹配' }).click()
await page.waitForTimeout(1400)
await shot('15-jd-analyzed')

await page.goto(`${BASE}/interview/new`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
const groups = page.getByRole('radiogroup')
await groups.nth(0).getByRole('radio').first().click()
await groups.nth(1).getByRole('radio').first().click()
await page.getByRole('group', { name: '题量' }).getByRole('button', { name: /12/ }).click()
await page.getByRole('button', { name: '开始模拟面试' }).click()
await page.waitForURL(/\/interview\//, { timeout: 8000 })
await page.waitForSelector('textarea')
const questionCount = await page
  .getByRole('navigation', { name: '题目导航' })
  .getByRole('button')
  .count()
console.log('question nav count:', questionCount)
await shot('16-twelve-questions')

await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(300)
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
console.log('mobile horizontal overflow px:', overflow)
await shot('17-mobile-room')

await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
await page.waitForSelector('h1')
const themeButton = page.getByRole('button', { name: /切换到深色模式/ })
if (await themeButton.count()) await themeButton.click()
await page.waitForTimeout(300)
await shot('18-dark-dashboard')

await browser.close()

if (errors.length) {
  console.error('BROWSER_ERRORS')
  for (const error of errors) console.error(error)
  process.exitCode = 1
} else {
  console.log('BROWSER_ERRORS none')
}
