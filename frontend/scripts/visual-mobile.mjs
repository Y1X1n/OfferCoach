import { chromium } from 'playwright-core'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

/**
 * 移动端截图冒烟：三档宽度 × 明暗主题，跑主干路径并做几何断言。
 *
 * 用法：
 *   MOBILE_ACCOUNT=you@example.com MOBILE_PASSWORD=secret pnpm visual:mobile
 * 可选：APP_URL（默认 http://localhost:5174）、CHROME_PATH、ARTIFACTS_DIR。
 */

const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.env.APP_URL || 'http://localhost:5174'
const ACCOUNT = process.env.MOBILE_ACCOUNT ?? ''
const PASSWORD = process.env.MOBILE_PASSWORD ?? ''
const OUT = path.resolve(process.env.ARTIFACTS_DIR || 'artifacts/mobile')

const IPHONE_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'

const SIZES = [
  { name: 'se-320', width: 320, height: 568, dpr: 2 },
  { name: 'iphone-390', width: 390, height: 844, dpr: 3 },
  { name: 'promax-430', width: 430, height: 932, dpr: 3 },
]

if (!ACCOUNT || !PASSWORD) {
  console.error('缺少登录凭据：请设置 MOBILE_ACCOUNT 与 MOBILE_PASSWORD 环境变量。')
  process.exit(2)
}

await mkdir(OUT, { recursive: true })

const problems = []
const note = (scope, message) => {
  problems.push(`[${scope}] ${message}`)
  console.log(`  ! ${scope}: ${message}`)
}

// 桥接环境里 vite 的 HMR/SSE 不会静默，networkidle 不可靠，统一等 DOM + 选择器。
async function goto(page, pathname, selector = 'h1') {
  await page.goto(`${BASE}${pathname}`, { waitUntil: 'domcontentloaded' })
  if (selector) await page.waitForSelector(selector, { timeout: 15000 })
}

async function audit(page, scope, name) {
  await page.waitForTimeout(400)
  const info = await page.evaluate(() => {
    const vw = window.innerWidth
    const nav = document.querySelector('nav[aria-label="移动端主导航"]')
    const navRect = nav ? nav.getBoundingClientRect() : null
    const navVisible = Boolean(navRect && navRect.height > 0 && getComputedStyle(nav).display !== 'none')

    const overflowing = []
    for (const el of document.querySelectorAll('main *')) {
      const cs = getComputedStyle(el)
      if (cs.display === 'none' || cs.visibility === 'hidden') continue
      const r = el.getBoundingClientRect()
      if (r.width === 0 && r.height === 0) continue
      if (r.right > vw + 1 || r.left < -1) {
        let scroller = el.parentElement
        let insideScroller = false
        while (scroller && scroller !== document.body) {
          const s = getComputedStyle(scroller)
          if (/(auto|scroll)/.test(s.overflowX)) {
            insideScroller = true
            break
          }
          scroller = scroller.parentElement
        }
        if (insideScroller) continue
        overflowing.push({
          tag: el.tagName.toLowerCase(),
          cls: String(el.className || '').slice(0, 60),
          right: Math.round(r.right),
          text: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 30),
        })
      }
    }

    const smallTargets = []
    const navTargets = [
      ...document.querySelectorAll('header button, nav[aria-label="移动端主导航"] a'),
    ]
    for (const el of navTargets) {
      const r = el.getBoundingClientRect()
      if (r.height === 0) continue
      if (r.height < 44 || r.width < 40) {
        smallTargets.push(
          `${(el.getAttribute('aria-label') || el.textContent || '').trim().slice(0, 16)} ${Math.round(r.width)}x${Math.round(r.height)}`,
        )
      }
    }

    const title = document.querySelector('header p.truncate')
    const titleTruncated = title ? title.scrollWidth > title.clientWidth + 1 : false

    return {
      vw,
      vh: window.innerHeight,
      docWidth: document.documentElement.scrollWidth,
      navVisible,
      overflowing: overflowing.slice(0, 6),
      overflowCount: overflowing.length,
      smallTargets: smallTargets.slice(0, 6),
      title: title ? title.textContent.trim() : '',
      titleTruncated,
    }
  })

  await page.screenshot({ path: path.join(OUT, `${scope}-${name}.png`) })

  if (info.docWidth > info.vw + 1) note(scope, `${name} 横向溢出 ${info.docWidth - info.vw}px`)
  if (info.overflowCount > 0) note(scope, `${name} 有 ${info.overflowCount} 个元素越界：${JSON.stringify(info.overflowing)}`)
  if (info.smallTargets.length > 0) note(scope, `${name} 触控区不足 44px：${info.smallTargets.join(' / ')}`)
  if (info.titleTruncated) note(scope, `${name} 顶栏标题被截断：「${info.title}」`)
  console.log(`  · ${name} ${info.vw}×${info.vh} nav=${info.navVisible ? 'on' : 'off'}`)
  await auditBottom(page, scope, name)
  return info
}


/** 固定底栏会一直盖住视口中线以下的内容；只有滚到页底仍被盖住才是缺底部留白。 */
async function auditBottom(page, scope, name) {
  const nav = await page.evaluate(() => {
    const el = document.querySelector('nav[aria-label="移动端主导航"]')
    if (!el || getComputedStyle(el).display === 'none') return null
    const r = el.getBoundingClientRect()
    return { top: r.top, bottom: r.bottom }
  })
  if (!nav) return

  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await page.waitForTimeout(350)
  const covered = await page.evaluate(() => {
    const el = document.querySelector('nav[aria-label="移动端主导航"]')
    if (!el) return []
    const r = el.getBoundingClientRect()
    const hits = []
    for (const node of document.querySelectorAll('main button, main a[href], main input, main select, main textarea')) {
      const box = node.getBoundingClientRect()
      if (box.height === 0) continue
      if (box.bottom > r.top + 2 && box.top < r.bottom) {
        hits.push((node.getAttribute('aria-label') || node.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 24) || '(无文本控件)')
      }
    }
    return hits.slice(0, 6)
  })
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.waitForTimeout(250)
  if (covered.length > 0) note(scope, `${name} 滚到页底仍被底部导航遮挡：${covered.join(' / ')}`)
}

const browser = await chromium.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--disable-gpu', '--no-sandbox'],
})

async function login(page) {
  await goto(page, '/login', 'input')
  await page.getByPlaceholder('you@example.com').fill(ACCOUNT)
  await page.getByPlaceholder('输入密码').fill(PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/', { timeout: 20000 })
  await page.waitForSelector('h1', { timeout: 15000 })
}

async function toggleDark(page) {
  const toggle = page.getByRole('button', { name: '切换到深色模式' })
  if (await toggle.count()) await toggle.first().click()
  await page.waitForTimeout(250)
}

async function runPass(size, { dark = false, full = true } = {}) {
  const scope = `${size.name}${dark ? '-dark' : ''}`
  console.log(`\n== ${scope} (${size.width}×${size.height} @${size.dpr}x)`)
  const context = await browser.newContext({
    viewport: { width: size.width, height: size.height },
    deviceScaleFactor: size.dpr,
    isMobile: true,
    hasTouch: true,
    userAgent: IPHONE_UA,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    colorScheme: dark ? 'dark' : 'light',
  })
  const page = await context.newPage()
  const consoleErrors = []
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    const text = message.text()
    // 未登录时的 /auth/refresh 401 属预期噪音。
    if (text.includes('401')) return
    consoleErrors.push(text.slice(0, 200))
  })
  page.on('pageerror', (error) => consoleErrors.push(`pageerror: ${error.message}`))

  try {
    await login(page)
    if (dark) await toggleDark(page)

    await audit(page, scope, '01-dashboard')

    // 抽屉导航：关闭键必须在面板内且可点。
    const menu = page.getByRole('button', { name: '打开导航' })
    if (await menu.count()) {
      await menu.first().click()
      await page.waitForTimeout(350)
      await page.screenshot({ path: path.join(OUT, `${scope}-01b-drawer.png`) })
      const close = page.getByRole('button', { name: '关闭导航' })
      if (!(await close.count())) note(scope, '抽屉缺少关闭按钮')
      else await close.first().click()
      await page.waitForTimeout(300)
    }

    // 简历：列表 → 详情 → 返回
    await goto(page, '/resumes')
    await audit(page, scope, '02-resumes-list')
    const resumeCard = page.locator('aside ul li button[aria-pressed]').first()
    if (await resumeCard.count()) {
      await resumeCard.click()
      await page.waitForSelector('h1')
      await audit(page, scope, '03-resumes-detail')
      const back = page.getByRole('button', { name: '返回简历列表' })
      if (!(await back.count())) note(scope, '简历详情缺少「返回列表」')
      else await back.click()
    }

    // 岗位：列表 → 详情
    await goto(page, '/jds')
    await audit(page, scope, '04-jds-list')
    const jdCard = page.locator('aside ul li button[aria-pressed]').first()
    if (await jdCard.count()) {
      await jdCard.click()
      await page.waitForTimeout(400)
      await audit(page, scope, '05-jds-detail')
    }

    // 创建面试：底部操作条
    await goto(page, '/interview/new')
    await audit(page, scope, '06-interview-setup')
    const barButton = page.getByRole('button', { name: '开始面试', exact: true })
    if (!(await barButton.count())) note(scope, '创建面试缺少底部开始操作条')
    else if (!(await barButton.first().isVisible())) note(scope, '创建面试底部开始操作条不可见')

    // 面试房间：优先继续进行中的会话，否则尝试新建
    let roomOpened = false
    const continueLink = page.getByRole('link', { name: /继续作答/ }).first()
    await goto(page, '/')
    if (await continueLink.count()) {
      await continueLink.click()
      roomOpened = true
    } else {
      await goto(page, '/interview/new')
      const start = page.getByRole('button', { name: /开始模拟面试|开始面试/ }).first()
      if (await start.count()) {
        const enabled = await start.isEnabled().catch(() => false)
        if (enabled) {
          await start.click()
          await page.waitForURL(/\/interview\/\d+/, { timeout: 20000 })
          roomOpened = true
        }
      }
    }

    if (roomOpened) {
      await page.waitForSelector('textarea', { timeout: 20000 })
      const room = await audit(page, scope, '07-interview-room')
      if (room.navVisible) note(scope, '面试房间应是专注模式，底部导航不应显示')
      const shellHeader = await page.evaluate(() => {
        const header = document.querySelector('body header')
        if (!header) return 'none'
        return getComputedStyle(header).display
      })
      if (shellHeader !== 'none') note(scope, `面试房间外壳顶栏未隐藏（display=${shellHeader}）`)

      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
      await page.waitForTimeout(400)
      await audit(page, scope, '08-interview-composer')
    } else {
      console.log('  · 跳过面试房间：没有进行中的会话，且新建所需数据不足')
    }

    if (full) {
      await goto(page, '/reports')
      await audit(page, scope, '09-reports')
      await goto(page, '/plans')
      await audit(page, scope, '10-plans')
      await goto(page, '/settings')
      await audit(page, scope, '11-settings')
    }

    if (consoleErrors.length > 0) note(scope, `控制台错误：${consoleErrors.join(' | ')}`)
  } catch (error) {
    note(scope, `流程中断：${String(error).split('\n')[0]}`)
  } finally {
    await context.close()
  }
}

for (const size of SIZES) {
  await runPass(size)
}
// 暗色只跑一档，覆盖工作台、列表与房间。
await runPass(SIZES[1], { dark: true, full: false })

await browser.close()

console.log(`\n截图目录：${OUT}`)
if (problems.length > 0) {
  console.error(`\n发现 ${problems.length} 个移动端问题：`)
  for (const item of problems) console.error(`- ${item}`)
  process.exitCode = 1
} else {
  console.log('\n移动端断言全部通过')
}