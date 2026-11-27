/**
 * 生成参赛材料：cover.png（16:9）与 description.pdf。
 *
 * 依赖 playwright-core（可用 PLAYWRIGHT_CORE 指定路径）；截图默认取 SHOTS_DIR。
 *
 *   node scripts/competition/build-assets.js
 *   SHOTS_DIR=E:/tmp/oc-demo node scripts/competition/build-assets.js
 */
const fs = require('fs');
const path = require('path');

const PLAYWRIGHT = process.env.PLAYWRIGHT_CORE || 'playwright-core';
const { chromium } = require(PLAYWRIGHT);

const ROOT = path.resolve(__dirname, '..', '..');
const ASSETS = path.join(__dirname, 'assets');
const OUT = process.env.OUT_DIR || path.join(ROOT, 'dist', 'competition');
const BUILD = path.join(OUT, '.build');
const SHOTS = process.env.SHOTS_DIR || path.join(OUT, 'shots');
const CHROME = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const NEEDED_SHOTS = ['01-index.png', '03-resume-detail.png', '08-feedback-1.png'];

function copyShots() {
  const target = path.join(BUILD, 'shots');
  fs.mkdirSync(target, { recursive: true });
  NEEDED_SHOTS.forEach((name) => {
    const source = path.join(SHOTS, name);
    if (!fs.existsSync(source)) {
      throw new Error('缺少截图：' + source + '（先运行录屏脚本，或用 SHOTS_DIR 指定目录）');
    }
    fs.copyFileSync(source, path.join(target, name));
  });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  fs.mkdirSync(BUILD, { recursive: true });
  copyShots();
  fs.copyFileSync(path.join(ASSETS, 'description.html'), path.join(BUILD, 'description.html'));

  const browser = await chromium.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox'] });

  // 1. 封面 1440x810
  const coverPage = await browser.newPage({ viewport: { width: 1440, height: 810 }, deviceScaleFactor: 1 });
  await coverPage.goto('file:///' + path.join(ASSETS, 'cover.html').replace(/\\/g, '/'));
  await coverPage.waitForTimeout(600);
  const coverPath = path.join(OUT, 'cover.png');
  await coverPage.screenshot({ path: coverPath });
  console.log('[assets] cover', coverPath);
  await coverPage.close();

  // 2. 作品说明 PDF
  const docPage = await browser.newPage({ viewport: { width: 1240, height: 1754 } });
  await docPage.goto('file:///' + path.join(BUILD, 'description.html').replace(/\\/g, '/'));
  await docPage.waitForTimeout(1200);
  const pdfPath = path.join(OUT, 'description.pdf');
  await docPage.pdf({ path: pdfPath, format: 'A4', printBackground: true, preferCSSPageSize: true });
  console.log('[assets] pdf', pdfPath, (fs.statSync(pdfPath).size / 1024).toFixed(0) + 'KB');
  await docPage.close();

  await browser.close();
})().catch((error) => {
  console.error('[assets] FAILED', error.message);
  process.exit(1);
});
