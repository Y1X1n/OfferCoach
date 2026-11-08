#!/usr/bin/env node
/**
 * OfferCoach question-bank builder.
 *
 * Parses the 7 public GitHub repositories shallow-cloned under
 * E:/My_project/data/question-banks/ into one normalized JSONL file.
 *
 * Node 24, built-in modules only. No npm dependencies.
 *
 * Usage:
 *   node scripts/qbank/build-question-bank.mjs
 *   node scripts/qbank/build-question-bank.mjs --keep-media   # do not delete media
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import readline from 'node:readline';
import { execFileSync } from 'node:child_process';

const SCRIPT_VERSION = '1.0.0';
const ROOT = path.resolve(import.meta.dirname, '../..');
const BANKS_DIR = path.join(ROOT, 'data', 'question-banks');
const OUT_DIR = path.join(ROOT, 'data', 'question-bank');
const NORM_DIR = path.join(OUT_DIR, 'normalized');
const OUT_JSONL = path.join(NORM_DIR, 'questions.jsonl');
const OUT_MANIFEST = path.join(NORM_DIR, 'manifest.json');
const OUT_LICENSES = path.join(OUT_DIR, 'LICENSES.md');
const KEEP_MEDIA = process.argv.includes('--keep-media');

const MAX_STEM = 2000;
const MIN_STEM = 10;
const MAX_ANSWER = 8000;

/* ------------------------------------------------------------------ repos */

const REPOS = [
  {
    key: 'bat_code',
    dir: 'bat_code',
    repo: 'DerekLin924/bat_code',
    branch: 'master',
    license: 'UNLICENSED',
    licenseFile: null,
    copyright: 'DerekLin924（仓库未提供 LICENSE，未授予任何许可）',
    language: 'zh-CN',
    licenseNote: '无 LICENSE 文件，按默认版权处理；不得公开分发。',
  },
  {
    key: 'campus-interview-questions',
    dir: 'campus-interview-questions',
    repo: '0voice/Campus_recruitment_interview_questions',
    branch: 'main',
    license: 'CC-BY-NC-SA-4.0',
    licenseFile: 'LICENSE.md',
    copyright: '0voice / 仓库作者（LICENSE.md 署名 CC BY-NC-SA 4.0）',
    language: 'zh-CN',
    licenseNote: 'GitHub 识别为 NOASSERTION，但仓库 LICENSE.md 明确为 CC BY-NC-SA 4.0；禁止商业使用，需署名并以相同方式共享。',
  },
  {
    key: 'mianstack',
    dir: 'mianstack',
    repo: 'didilili/mianstack',
    branch: 'main',
    license: 'MIT',
    licenseFile: 'LICENSE',
    copyright: 'Copyright (c) 2026 didilili',
    language: 'zh-CN',
    licenseNote: 'MIT，保留版权与许可声明即可使用、修改、分发。',
  },
  {
    key: 'ai-agent-interview-240',
    dir: 'ai-agent-interview-240',
    repo: 'SuperGODOG/ai-agent-interview-240',
    branch: 'main',
    license: 'MIT',
    licenseFile: 'LICENSE',
    copyright: 'Copyright (c) 2026 SuperGODOG',
    language: 'zh-CN',
    licenseNote: 'MIT，保留版权与许可声明即可。',
  },
  {
    key: 'agent-interview',
    dir: 'agent-interview',
    repo: 'cdavid817/agent-interview',
    branch: 'main',
    license: 'MIT',
    licenseFile: 'LICENSE',
    copyright: 'Copyright (c) 2026 cdavid817',
    language: 'zh-CN',
    licenseNote: 'MIT，保留版权与许可声明即可。',
  },
  {
    key: 'system-design-101',
    dir: 'system-design-101',
    repo: 'ByteByteGoHq/system-design-101',
    branch: 'main',
    license: 'CC-BY-NC-ND-4.0',
    licenseFile: 'LICENSE.md',
    copyright: 'ByteByteGoHq / Alex Xu（LICENSE.md 为 CC BY-NC-ND 4.0）',
    language: 'en',
    licenseNote: 'GitHub 识别为 NOASSERTION，但 LICENSE.md 明确为 CC BY-NC-ND 4.0：禁止商业使用、禁止演绎（ND）。规范化改写属于演绎作品，风险高。',
  },
  {
    key: 'codejeet',
    dir: 'codejeet',
    repo: 'ayush-that/codejeet',
    branch: 'main',
    license: 'GPL-3.0',
    licenseFile: 'LICENSE',
    copyright: 'Copyright (c) ayush-that；题目数据来源于 liquidslr/interview-company-wise-problems，LeetCode 题干版权归 LeetCode',
    language: 'en',
    licenseNote: 'GPL-3.0 强传染性；题目/题解文本还涉及 LeetCode 与上游数据集版权，公开分发前必须复核。',
  },
];

const MEDIA_EXT = new Set([
  '.mp4', '.mkv', '.avi', '.mov', '.flv', '.wmv', '.webm', '.m4v', '.mpg', '.mpeg',
  '.mp3', '.wav', '.m4a', '.ogg', '.oga', '.flac', '.aac', '.wma', '.3gp',
]);
const LARGE_BINARY_EXT = new Set(['.apkg', '.zip', '.7z', '.rar', '.tar', '.gz', '.pdf']);
const IMAGE_EXT = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.ico', '.svg']);

/* --------------------------------------------------------------- utilities */

const stats = new Map();
const seenStems = new Map();

function repoStats(cfg) {
  if (!stats.has(cfg.key)) {
    stats.set(cfg.key, {
      total: 0,
      kept: 0,
      skipped: 0,
      reasons: {},
      mediaDeleted: 0,
      mediaBytes: 0,
      imagesDeleted: 0,
      imagesBytes: 0,
      filesScanned: 0,
      durationMs: 0,
    });
  }
  return stats.get(cfg.key);
}

function skip(cfg, reason, n = 1) {
  const st = repoStats(cfg);
  st.skipped += n;
  st.reasons[reason] = (st.reasons[reason] || 0) + n;
}

function walkFiles(dir, filter = () => true) {
  const out = [];
  const stack = [dir];
  while (stack.length) {
    const cur = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(cur, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      if (ent.name === '.git' || ent.name === 'node_modules' || ent.name === '.gitignore') continue;
      const full = path.join(cur, ent.name);
      if (ent.isDirectory()) stack.push(full);
      else if (ent.isFile() && filter(full)) out.push(full);
    }
  }
  out.sort();
  return out;
}

function readText(file) {
  const st = fs.statSync(file);
  if (st.size > 4 * 1024 * 1024) return '';
  return fs.readFileSync(file, 'utf8');
}

function rel(cfg, abs) {
  return path.relative(path.join(BANKS_DIR, cfg.dir), abs).split(path.sep).join('/');
}

function sha256(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

function truncate(text, max) {
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1).trimEnd()}…`;
}

/** Some generated headings concatenate the same question twice; keep the first copy. */
function dedupeRepeatedTitle(text) {
  const t = String(text || '').trim();
  if (t.length < 80) return t;
  for (const n of [40, 30, 25]) {
    const probe = t.slice(0, n);
    const idx = t.indexOf(probe, n + 5);
    if (idx > n + 5 && t.length - idx > 15) return t.slice(0, idx).trim();
  }
  return t;
}

function loadFrontMatter(text) {
  const m = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!m) return { data: null, body: text };
  try {
    return { data: JSON.parse(m[1]), body: text.slice(m[0].length) };
  } catch {
    return { data: null, body: text.slice(m[0].length) };
  }
}

function loadYamlFrontMatter(text) {
  const m = text.match(/^\uFEFF?---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!m) return { data: null, body: text };
  const strip = (v) => String(v).trim().replace(/^["']|["']$/g, '');
  const data = {};
  let key = null;
  for (const raw of m[1].split(/\r?\n/)) {
    const line = raw.trimEnd();
    if (!line.trim() || line.trim().startsWith('#')) continue;
    const li = line.match(/^\s*-\s+(.+)$/);
    if (li && key) {
      if (!Array.isArray(data[key])) data[key] = [];
      data[key].push(strip(li[1]));
      continue;
    }
    const kv = line.match(/^([A-Za-z_][\w-]*):\s*(.*)$/);
    if (!kv) continue;
    key = kv[1];
    const v = kv[2].trim();
    if (!v) data[key] = [];
    else if (v.startsWith('[') && v.endsWith(']')) {
      data[key] = v.slice(1, -1).split(',').map(strip).filter(Boolean);
    } else data[key] = strip(v);
  }
  return { data, body: text.slice(m[0].length) };
}

const IGNORE_LINE = [
  /^在面栈阅读[:：]/,
  /^>\s*在面栈阅读/,
  /^核验日期[:：]/,
  /^来源[:：]\s*(官方资料|源码分析)/,
  /shields\.io/i,
  /^!\[/,
  /^\[\!\[/,
  /^<p\b/i,
  /^<\/p>/i,
  /^---$/,
];

function cleanMarkdown(input) {
  if (!input) return '';
  let s = String(input);
  s = s.replace(/\r\n?/g, '\n');
  s = s.replace(/<!--[\s\S]*?-->/g, '');
  s = s.replace(/^---\n[\s\S]*?\n---\n/, '');
  // images and badge links
  s = s.replace(/\[\!\[[^\]]*\]\([^)]*\)\]\([^)]*\)/g, '');
  s = s.replace(/!\[[^\]]*\]\([^)]*\)/g, '');
  s = s.replace(/<img[^>]*>/gi, '');
  // html tags, keep line breaks for common block tags
  s = s.replace(/<br\s*\/?>/gi, '\n');
  s = s.replace(/<\/(p|div|li|tr|h\d)>/gi, '\n');
  s = s.replace(/<[^>]+>/g, '');
  // entities
  s = s
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&emsp;/g, ' ');
  // images as markdown reference style
  s = s.replace(/^\s*\[[^\]]*\]:\s*\S+.*$/gm, '');
  // links -> text
  s = s.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1');
  // drop quote/nav noise lines
  s = s
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      if (!t) return true;
      if (IGNORE_LINE.some((re) => re.test(t))) return false;
      if (/^https?:\/\/\S+$/.test(t)) return false;
      if (/mianstack\.com\/question\/q-/i.test(t) && /^>/.test(t)) return false;
      return true;
    })
    .join('\n');
  // headings
  s = s.replace(/^\s{0,3}#{1,6}\s*/gm, '');
  // blockquotes
  s = s.replace(/^\s{0,3}>\s?/gm, '');
  // tables: drop separator rows, flatten cells
  s = s
    .split('\n')
    .filter((line) => !/^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(line))
    .map((line) => {
      const t = line.trim();
      if (!t.startsWith('|')) return line;
      const cells = t.replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
      return `• ${cells.filter(Boolean).join(' ｜ ')}`;
    })
    .join('\n');
  // bullets
  s = s.replace(/^\s*[-*+]\s+/gm, '• ');
  // emphasis
  s = s.replace(/\*\*([^*]+)\*\*/g, '$1');
  s = s.replace(/__([^_]+)__/g, '$1');
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1$2');
  s = s.replace(/~~([^~]+)~~/g, '$1');
  s = s.replace(/~~/g, '');
  // code fences / inline code
  s = s.replace(/^\s*```[^\n]*$/gm, '');
  s = s.replace(/`([^`\n]+)`/g, '$1');
  s = s.replace(/[ \t]+$/gm, '');
  s = s.replace(/\n{3,}/g, '\n\n');
  s = s.replace(/^\s+|\s+$/g, '');
  return s;
}

function cleanInline(text) {
  return cleanMarkdown(String(text || ''))
    .replace(/\s*\n\s*/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

/** Strip transcript list markers, Q numbers and timestamps from a question line. */
function cleanQuestionText(text) {
  return cleanInline(
    String(text || '')
      .replace(/^[•●·*\-–—\s]+/, '')
      .replace(/^(?:Q\s*)?\d{1,3}\s*[.．、)：:）]\s*(?=\S)/i, '')
      .replace(/^\d{1,3}(?=(?:有没有|是否|怎么|什么|如何|为什么|为何|请|你|能|可以))/, '')
      .replace(/[（(]\d{1,2}:\d{2}[)）]/g, ''),
  );
}

function normalizeStem(stem) {
  return stem
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\p{P}\p{S}]/gu, '');
}

function uniqueTags(tags, max = 8) {
  const out = [];
  for (const raw of tags) {
    const t = cleanInline(raw).replace(/^#+/, '').trim();
    if (!t || t.length > 40) continue;
    if (!out.includes(t)) out.push(t);
    if (out.length >= max) break;
  }
  return out;
}

function extractPoints(text, max = 5) {
  const isUsable = (l) =>
    l.length >= 15 &&
    l.length <= 240 &&
    !/^https?:/.test(l) &&
    !/^\[\d+\]/.test(l) &&
    !/[｜|]/.test(l) && // flattened table rows are not key points
    !/[：:]$/.test(l);
  const bullets = text
    .split('\n')
    .map((l) => l.replace(/^\s*(?:•|[-*]|\d{1,2}[.、)])\s+/, '').trim())
    .filter(isUsable);
  let pool = bullets;
  if (pool.length < 2) {
    pool = text
      .split(/(?<=[。！？；])|(?<=\.)\s+|\n+/)
      .map((s) => s.trim())
      .filter((s) => isUsable(s) && !/^•/.test(s));
  }
  const out = [];
  for (const p of pool) {
    const v = p.replace(/\s+/g, ' ').trim();
    if (!out.some((x) => x === v || x.includes(v) || v.includes(x))) out.push(v);
    if (out.length >= max) break;
  }
  return out;
}

/* ------------------------------------------------------------ inference */

function inferDimension(text, fallback = 'DEPTH') {
  const t = text || '';
  if (/自我介绍|职业规划|为什么选择|为何选择|岗位匹配|求职动机|离职原因|期望薪资|反问|意向城市|为什么投递/.test(t)) return 'FIT';
  if (/团队协作|团队合作|与同事|冲突|沟通|领导力|失败|挫折|压力|最有成就感|最大的挑战|加班|优缺点|职业发展|如何学习/.test(t)) return 'BEHAVIOR';
  if (/项目经历|项目介绍|项目深挖|简历|实习经历|你负责|负责的|项目中的|项目里|项目背景|项目难点|架构选型/.test(t)) return 'PROJECT';
  return fallback;
}

const JOB_RULES = [
  ['AI-Agent', /Agent|智能体|大模型|LLM|RAG|Transformer|Prompt|MCP|LoRA|SFT|RLHF|向量|多模态|推理|注意力|KV Cache|微调|Token|Embedding|Tool Calling/i],
  ['前端', /前端|Vue|React|JavaScript|TypeScript|CSS|HTML|浏览器|小程序|Webpack|Vite|Node\.js/i],
  ['算法', /LeetCode|算法|动态规划|二叉树|链表|排序|图论|回溯|贪心|双指针|手撕/i],
  ['系统设计', /系统设计|架构设计|分布式|高并发|微服务|CAP|一致性|限流|熔断|负载均衡/i],
  ['测试', /测试|QA|质量保障|自动化测试|用例/],
  ['数据', /数据开发|数据分析|大数据|数仓|SQL|Hive|Spark|Flink|指标/],
  ['后端', /后端|Java|JVM|Spring|Redis|MySQL|数据库|并发|多线程|Go\b|Golang|Python|网络|操作系统|Linux|Docker|Kubernetes|服务端|接口/i],
];

function inferJobFamily(text, fallback = '通用') {
  const t = text || '';
  for (const [name, re] of JOB_RULES) {
    if (re.test(t)) return name;
  }
  return fallback;
}

const COMPANY_ALIASES = [
  [/字节跳动|字节|bytedance|ByteDance/i, '字节跳动'],
  [/阿里巴巴|阿里云|淘天|菜鸟|饿了么|高德|盒马|灵犀/, '阿里巴巴'],
  [/腾讯|IEG|TEG|CSIG|WXG/, '腾讯'],
  [/美团|大众点评/, '美团'],
  [/百度|baidu/i, '百度'],
  [/华为|Huawei/i, '华为'],
  [/京东|JD\.com/i, '京东'],
  [/网易|NetEase/i, '网易'],
  [/小米|xiaomi/i, '小米'],
  [/快手|Kuaishou/i, '快手'],
  [/滴滴|didi/i, '滴滴'],
  [/拼多多|pdd/i, '拼多多'],
  [/小红书/, '小红书'],
  [/微软|Microsoft/i, '微软'],
  [/商汤|SenseTime/i, '商汤'],
  [/科大讯飞|讯飞/, '科大讯飞'],
  [/虾皮|Shopee/i, 'Shopee'],
  [/携程|Trip\.com/i, '携程'],
  [/大疆|DJI/i, '大疆'],
  [/米哈游|miHoYo/i, '米哈游'],
  [/哔哩哔哩|bilibili|B站/i, '哔哩哔哩'],
  [/OPPO/i, 'OPPO'],
  [/荣耀|Honor/i, '荣耀'],
  [/蚂蚁|Ant Group/i, '蚂蚁集团'],
  [/英伟达|NVIDIA|Nvidia/i, '英伟达'],
  [/Momenta/i, 'Momenta'],
  [/亚信/, '亚信'],
  [/深信服/, '深信服'],
  [/用友/, '用友'],
  [/微步在线/, '微步在线'],
  [/安克创新/, '安克创新'],
  [/多益网络/, '多益网络'],
  [/天美/, '腾讯天美'],
  [/搜狐|Sohu/i, '搜狐'],
  [/CVTE|视源/i, 'CVTE'],
  [/Todesk/i, 'ToDesk'],
  [/西山居/, '西山居'],
  [/金山/, '金山'],
  [/迅雷/, '迅雷'],
  [/虎牙/, '虎牙'],
  [/小鹏/, '小鹏'],
  [/理想汽车|理想/, '理想汽车'],
  [/^google$/i, 'Google'],
  [/^meta$|^facebook$/i, 'Meta'],
  [/^amazon$/i, 'Amazon'],
  [/^apple$/i, 'Apple'],
  [/^uber$/i, 'Uber'],
  [/^linkedin$/i, 'LinkedIn'],
  [/^netflix$/i, 'Netflix'],
  [/^twitter$/i, 'Twitter'],
  [/^airbnb$/i, 'Airbnb'],
  [/^stripe$/i, 'Stripe'],
  [/^salesforce$/i, 'Salesforce'],
  [/^adobe$/i, 'Adobe'],
  [/^oracle$/i, 'Oracle'],
  [/^ibm$/i, 'IBM'],
  [/^intel$/i, 'Intel'],
  [/^tesla$/i, 'Tesla'],
  [/^spotify$/i, 'Spotify'],
  [/^tiktok$/i, 'TikTok'],
  [/^bloomberg$/i, 'Bloomberg'],
  [/^walmart$/i, 'Walmart'],
  [/^paypal$/i, 'PayPal'],
  [/^ebay$/i, 'eBay'],
  [/^zoom$/i, 'Zoom'],
  [/^slack$/i, 'Slack'],
  [/^atlassian$/i, 'Atlassian'],
  [/^dropbox$/i, 'Dropbox'],
  [/^pinterest$/i, 'Pinterest'],
  [/^reddit$/i, 'Reddit'],
  [/^roblox$/i, 'Roblox'],
  [/^doordash$/i, 'DoorDash'],
  [/^instacart$/i, 'Instacart'],
  [/^coinbase$/i, 'Coinbase'],
  [/^databricks$/i, 'Databricks'],
  [/^snowflake$/i, 'Snowflake'],
  [/^cloudflare$/i, 'Cloudflare'],
  [/^jpmorgan$|^jp\s*morgan$/i, 'JPMorgan'],
  [/^goldman[\s-]*sachs$/i, 'Goldman Sachs'],
  [/^morgan[\s-]*stanley$/i, 'Morgan Stanley'],
  [/^tcs$/i, 'TCS'],
  [/^infosys$/i, 'Infosys'],
  [/^wipro$/i, 'Wipro'],
];

function normalizeCompany(raw) {
  if (!raw) return null;
  const t = String(raw).replace(/[\s（）()【】\[\]]/g, '').trim();
  if (!t) return null;
  if (/^(未知|不详|未披露|未透露|未注明|其他|未知公司|其他公司|某公司|某银行)$/.test(t)) return t === '某银行' ? '某银行' : null;
  for (const [re, name] of COMPANY_ALIASES) {
    if (re.test(t)) return name;
  }
  if (t.length <= 20) {
    if (!/[\u4e00-\u9fa5]/.test(t) && /^[a-z][a-z0-9 .&'-]*$/i.test(t)) {
      return t.replace(/\b[a-z]/g, (c) => c.toUpperCase());
    }
    return t;
  }
  return null;
}

function detectLanguage(text, fallback) {
  const cjk = (text.match(/[\u4e00-\u9fa5]/g) || []).length;
  const latin = (text.match(/[A-Za-z]/g) || []).length;
  if (cjk === 0 && latin > 20) return 'en';
  if (cjk > 0) return 'zh-CN';
  return fallback;
}

/* ------------------------------------------------------------- item emit */

function sourceUrl(cfg, relPath) {
  const encoded = relPath
    .split('/')
    .map((seg) => encodeURIComponent(seg))
    .join('/');
  return `https://github.com/${cfg.repo}/blob/${cfg.branch}/${encoded}`;
}

function emit(cfg, raw) {
  const st = repoStats(cfg);
  st.total += 1;

  let title = cleanInline(raw.title || '').slice(0, 200);
  let stem = cleanMarkdown(raw.stem || title);
  stem = stem.replace(/[ \t]+/g, (m) => (m.length > 2 ? ' ' : m)).trim();
  if (stem.length < MIN_STEM) {
    const suffix = cleanInline(raw.shortSuffix || raw.topic || '');
    if (suffix) stem = `${stem}（${suffix}）`;
  }
  if (stem.length < MIN_STEM) {
    skip(cfg, 'stemTooShort');
    return false;
  }
  if (stem.length > MAX_STEM) stem = truncate(stem, MAX_STEM);
  if (!title) title = cleanInline(stem.split('\n')[0]).slice(0, 120);

  const key = sha256(normalizeStem(stem));
  if (seenStems.has(key)) {
    skip(cfg, 'duplicateStem');
    return false;
  }

  let answer = cleanMarkdown(raw.answer || '');
  if (answer.length > MAX_ANSWER) answer = truncate(answer, MAX_ANSWER);
  let points = Array.isArray(raw.points) ? raw.points.map((p) => cleanInline(p)).filter(Boolean) : [];
  points = points.map((p) => truncate(p, 240)).filter((p, i, arr) => arr.indexOf(p) === i).slice(0, 5);
  if (points.length < 2) points = [];
  if (!answer && points.length === 0) {
    // question-only entries are allowed; nothing else to do
  }

  const dimension = raw.dimension || inferDimension(`${title}\n${stem}`, 'DEPTH');
  const item = {
    sourceRepo: cfg.repo,
    sourcePath: raw.sourcePath,
    sourceUrl: sourceUrl(cfg, raw.sourcePath),
    sourceLicense: cfg.license,
    title: title || cleanInline(stem).slice(0, 120),
    stem,
    referenceAnswer: answer,
    referencePoints: points,
    jobFamily: raw.jobFamily || inferJobFamily(`${title}\n${stem}`, '通用'),
    dimension,
    difficulty: raw.difficulty || 'MEDIUM',
    tags: uniqueTags(raw.tags || []),
    company: raw.company ?? null,
    language: raw.language || detectLanguage(`${title}\n${stem}`, cfg.language),
  };

  seenStems.set(key, item.sourcePath);
  st.kept += 1;
  writeLine(item);
  return true;
}

/* ------------------------------------------------------------- JSONL sink */

let outFd = null;
let written = 0;

function writeLine(item) {
  fs.writeSync(outFd, `${JSON.stringify(item)}\n`);
  written += 1;
}

/* --------------------------------------------------------------- parsers */

function mianstackDimension(type, title) {
  const d = inferDimension(title, 'DEPTH');
  if (d !== 'DEPTH') return d;
  switch (type) {
    case '项目深挖':
      return 'PROJECT';
    case '行为面':
      return 'BEHAVIOR';
    case '经历介绍':
    case '反问':
      return 'FIT';
    default:
      return 'DEPTH';
  }
}

function mianstackJobFamily(category, title) {
  if (['llm', 'rag', 'agent', 'ai_engineering', 'training'].includes(category)) return 'AI-Agent';
  if (category === 'coding') return '算法';
  if (category === 'career') return inferJobFamily(title, '通用');
  // software
  if (/分布式|架构|高并发|系统设计/.test(title)) return '系统设计';
  if (/前端|Vue|React|CSS|浏览器/.test(title)) return '前端';
  if (/测试|QA/.test(title)) return '测试';
  if (/数据|SQL|数仓/.test(title)) return '数据';
  return '后端';
}

function difficultyFromLevel(level, type) {
  let d = { 基础: 'EASY', 进阶: 'MEDIUM', 深入: 'HARD' }[level] || 'MEDIUM';
  if (['系统设计', '方案设计', '技术决策'].includes(type) && d === 'EASY') d = 'MEDIUM';
  return d;
}

function parseMianstack(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);

  // ---------- questions/*.md (JSON front matter + answer body)
  const qDir = path.join(base, 'questions');
  for (const file of walkFiles(qDir, (f) => f.endsWith('.md'))) {
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    if (!text) {
      skip(cfg, 'emptyFile');
      continue;
    }
    const { data: fm, body } = loadFrontMatter(text);
    if (!fm || !fm.title) {
      skip(cfg, 'badFrontMatter');
      continue;
    }
    let answerBody = body;
    const cut = answerBody.search(/^##\s*(对应教程|参考资料)/m);
    if (cut >= 0) answerBody = answerBody.slice(0, cut);
    const answer = fm.answerStatus === 'ready' || fm.answerStatus === 'reviewed' ? cleanMarkdown(answerBody) : cleanMarkdown(answerBody);
    const type = fm.type || '';
    const topic = fm.topic || '';
    emit(cfg, {
      sourcePath: rel(cfg, file),
      title: fm.title,
      stem: fm.title,
      topic,
      answer: answer.length >= 30 ? answer : '',
      points: answer.length >= 30 ? extractPoints(cleanMarkdown(answerBody)) : [],
      jobFamily: mianstackJobFamily(fm.category, `${fm.title} ${topic}`),
      dimension: mianstackDimension(type, `${fm.title} ${topic}`),
      difficulty: difficultyFromLevel(fm.level, type),
      tags: [fm.category, topic, type, fm.level, fm.priority, fm.id].filter(Boolean),
      company: null,
      language: 'zh-CN',
      shortSuffix: topic,
    });
  }

  // ---------- interviews/*.md (real interview transcripts, questions inside)
  const iDir = path.join(base, 'interviews');
  for (const file of walkFiles(iDir, (f) => f.endsWith('.md'))) {
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    if (!text) {
      skip(cfg, 'emptyFile');
      continue;
    }
    const { data: fm, body } = loadFrontMatter(text);
    if (!fm || !fm.title) {
      skip(cfg, 'badFrontMatter');
      continue;
    }
    const ctx = (fm.contexts && fm.contexts[0]) || {};
    const company = normalizeCompany(ctx.company);
    const roleFamily = ctx.roleFamily || '';
    const jobFamily = inferJobFamily(`${roleFamily} ${fm.title}`, '通用');
    const sections = body.split(/^##\s+/m).filter((s) => /^原帖正文/.test(s));
    if (!sections.length) {
      skip(cfg, 'noTranscript');
      continue;
    }
    const sectionText = sections[0].replace(/^原帖正文[^\n]*\n/, '').split(/^##\s+有价值评论/m)[0];
    const lines = sectionText
      .split('\n')
      .map((l) => l.replace(/^>\s?/, '').trim())
      .filter(Boolean);
    const questions = [];
    const QUESTIONISH = /[?？]$|怎么|怎样|什么|如何|为什么|为何|区别|原理|是否|哪些|介绍|实现|设计|优化|对比|说说|讲讲|有没有|能否|了解|谈谈/;
    for (const line of lines) {
      const numbered = line.match(/^(?:\d{1,3}[.．、)]|\d{1,3}\s)\s*(.+)$/);
      let q = cleanQuestionText(numbered ? numbered[1] : line);
      if (q.length < 10 || q.length > 200) continue;
      if (/｜/.test(q)) continue; // flattened table row, not a question
      if ((q.match(/。/g) || []).length > 2) continue; // narrative answer paragraph
      if (/^(答|解析|思路|回答|注|我当时|然后|面试官|所以|因此|最后|首先|挂了|面了|感觉|整体|一共|今天|昨天|上午|下午|我|我们|我说|你刚)/.test(q)) continue;
      if (/^(反问|一面|二面|三面|四面|hr面|面试|笔试|岗位|公司|薪资|结果|作者|时间)[:：]?/.test(q) && q.length < 25) continue;
      if (numbered ? !QUESTIONISH.test(q) : !(/[?？]$/.test(q) || (/^(是否|如何|为什么|为何|什么|怎么|怎样|能否|有没有|请|说说|谈谈|讲讲|聊聊|介绍|你|能|可以|关于|描述|解释|比较|列举|实现|设计)/.test(q) && q.length <= 150))) continue;
      questions.push(q);
    }
    const rounds = (ctx.rounds || []).join('/');
    const jobNature = ctx.jobNature || '';
    for (const q of [...new Set(questions)]) {
      emit(cfg, {
        sourcePath: rel(cfg, file),
        title: q,
        stem: q,
        answer: '',
        points: [],
        jobFamily,
        dimension: inferDimension(q, 'DEPTH'),
        difficulty: /设计|架构|实现|优化|原理|底层|如何/.test(q) ? 'HARD' : 'MEDIUM',
        tags: ['面经', roleFamily, jobNature, rounds, fm.id].filter(Boolean),
        company,
        language: 'zh-CN',
      });
    }
    const dropped = Math.max(0, lines.length - questions.length);
    if (dropped) skip(cfg, 'nonQuestionLine', dropped);
  }

  // ---------- collections/*.json are title+link indexes only -> skipped by design
  const cDir = path.join(base, 'collections');
  for (const file of walkFiles(cDir, (f) => f.endsWith('.json'))) {
    repoStats(cfg).filesScanned += 1;
    try {
      const data = JSON.parse(readText(file));
      skip(cfg, 'linkOnlyCollection', (data.items || []).length || 1);
    } catch {
      skip(cfg, 'badJson');
    }
  }
}

function parseAgentInterview(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  const roots = ['docs/01-foundations', 'docs/02-capabilities', 'docs/03-production', 'docs/04-products'];
  for (const r of roots) {
    const abs = path.join(base, r);
    if (!fs.existsSync(abs)) continue;
    for (const file of walkFiles(abs, (f) => f.endsWith('.md'))) {
      const relPath = rel(cfg, file);
      if (/\/README\.md$/i.test(relPath)) {
        skip(cfg, 'readmeIndex');
        continue;
      }
      repoStats(cfg).filesScanned += 1;
      const text = readText(file);
      const heads = [...text.matchAll(/<a id="([^"]+)"><\/a>\s*\n###\s*\d+\.\s*([^\n]+)/g)];
      if (!heads.length) {
        skip(cfg, 'noQuestions');
        continue;
      }
      for (let i = 0; i < heads.length; i++) {
        const h = heads[i];
        const start = h.index + h[0].length;
        const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
        const rawBody = text.slice(start, end).replace(/\n---\s*$/, '');
        const titleFull = cleanInline(h[2])
          .replace(/（[^）]{1,20}?(?:一面|二面|三面|四面|面试|笔试|面经)[^）]*）/g, '')
          .trim();
        const companyMatch = h[2].match(/[（(]([\u4e00-\u9fa5A-Za-z0-9·\s]{2,12}?)(?:[一二三四五六七八九十0-9]+面|面试|笔试|面经)[）)]/);
        const company = companyMatch ? normalizeCompany(companyMatch[1]) : null;
        const answer = cleanMarkdown(rawBody);
        if (answer.length < 80) {
          skip(cfg, 'answerTooShort');
          continue;
        }
        // points: 核心思路 paragraph + first bullets of 深入拆解
        const points = [];
        const core = rawBody.match(/【核心思路】\s*\n?([\s\S]*?)(?:\n【|\n\*\*相关知识点|$)/);
        if (core) points.push(...extractPoints(cleanMarkdown(core[1]), 3));
        const deep = rawBody.match(/【深入拆解】\s*\n?([\s\S]*?)(?:\n\*\*相关知识点|$)/);
        if (deep) points.push(...extractPoints(cleanMarkdown(deep[1]), 3));
        // files without the 【核心思路】/【深入拆解】 structure: use the answer body
        const finalPoints = points.length >= 2 ? points : extractPoints(answer, 5);
        emit(cfg, {
          sourcePath: relPath,
          title: titleFull || h[2],
          stem: titleFull || h[2],
          answer,
          points: finalPoints.slice(0, 5),
          jobFamily: inferJobFamily(titleFull, 'AI-Agent'),
          dimension: inferDimension(titleFull, 'DEPTH'),
          difficulty: /设计|架构|实现|优化|原理|底层|故障|排查/.test(titleFull) ? 'HARD' : 'MEDIUM',
          tags: ['AI-Agent', h[1], '问答'],
          company,
          language: 'zh-CN',
        });
      }
    }
  }
}

function parseAiAgentInterview(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);

  const volumeJob = [
    ['01-Agent应用开发', 'AI-Agent'],
    ['02-八股基础', '后端'],
    ['03-Transformer与大模型', 'AI-Agent'],
    ['04-LeetCode算法', '算法'],
    ['05-反问与国企特别版', '通用'],
    ['06-AI_Infra与高性能算子', 'AI-Agent'],
  ];
  const docDir = path.join(base, 'docs/01-面试八股文');
  for (const file of walkFiles(docDir, (f) => f.endsWith('.md'))) {
    const name = path.basename(file);
    if (/^00-/.test(name) || /README/i.test(name)) {
      skip(cfg, 'indexPage');
      continue;
    }
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    const heads = [...text.matchAll(/^###\s+([A-Z]?\d+)\s*·\s*(.+)$/gm)];
    if (!heads.length) {
      skip(cfg, 'noQuestions');
      continue;
    }
    const jobFamily = (volumeJob.find(([p]) => name.startsWith(p)) || [null, '通用'])[1];
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      const start = h.index + h[0].length;
      const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
      const rawBody = text.slice(start, end).replace(/\n---\s*$/, '');
      const depthM = rawBody.match(/\*\*深度\*\*:\s*(\d)\/5\s*·\s*([^\n]+)/);
      const depth = depthM ? Number(depthM[1]) : 3;
      const kind = depthM ? cleanInline(depthM[2]) : '';
      const title = dedupeRepeatedTitle(cleanInline(h[2]).replace(/\s+/g, ' ').trim());
      const difficulty = depth <= 2 ? 'EASY' : depth === 3 ? 'MEDIUM' : 'HARD';
      const isReverse = /反问/.test(name) || /反问/.test(kind);
      emit(cfg, {
        sourcePath: rel(cfg, file),
        title,
        stem: title,
        answer: '', // repo only ships a generic four-step answering framework, not real answers
        points: [],
        jobFamily: inferJobFamily(title, jobFamily),
        dimension: isReverse ? 'FIT' : inferDimension(title, 'DEPTH'),
        difficulty,
        tags: [name.replace(/^\d+-|\.md$/g, ''), kind, `深度${depth}/5`, h[1]].filter(Boolean),
        company: null,
        language: 'zh-CN',
      });
    }
  }

  // ---------- projects/*/项目内作答.md (real project answers)
  const projDir = path.join(base, 'projects');
  for (const file of walkFiles(projDir, (f) => /项目内作答\.md$/.test(f))) {
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    const project = path.basename(path.dirname(file));
    const heads = [...text.matchAll(/^###\s+L(\d+)\s+(.+)$/gm)];
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      const start = h.index + h[0].length;
      const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
      const body = text.slice(start, end).split(/^---\s*$/m)[0];
      const answer = cleanMarkdown(body);
      if (answer.length < 60) {
        skip(cfg, 'answerTooShort');
        continue;
      }
      const title = dedupeRepeatedTitle(cleanInline(h[2]).replace(/\s+/g, ' ').trim());
      emit(cfg, {
        sourcePath: rel(cfg, file),
        title,
        stem: title,
        answer,
        points: extractPoints(answer),
        jobFamily: inferJobFamily(title, 'AI-Agent'),
        dimension: 'PROJECT',
        difficulty: 'HARD',
        tags: ['项目拆解', project, `L${h[1]}`],
        company: null,
        language: 'zh-CN',
      });
    }
  }
}

function parseCampus(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  const mdFiles = walkFiles(base, (f) => f.endsWith('.md'));

  for (const file of mdFiles) {
    const relPath = rel(cfg, file);
    const parts = relPath.split('/');
    if (parts[0] === 'logos' || /^LICENSE/i.test(parts[0]) || parts[0] === 'README.md') {
      skip(cfg, 'nonContent');
      continue;
    }
    const dirName = parts.length > 1 ? parts[0] : '';
    const isVideoDir = /视频|指导|logos/.test(dirName);
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    const heads = [...text.matchAll(/^##\s+(?:(\d+)[.、]\s*)?([^\n]+)$/gm)];
    if (!heads.length) {
      skip(cfg, isVideoDir ? 'videoLinkOnly' : 'noSections');
      continue;
    }
    const company = normalizeCompany(dirName);
    let keptHere = 0;
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      const title = cleanInline(h[2]);
      if (!title || /^(面试题|目录|索引|README)$/i.test(title)) continue;
      const start = h.index + h[0].length;
      const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
      const body = text.slice(start, end);
      const answer = cleanMarkdown(body);
      if (answer.length < 60) continue;
      // a section that is just one external link / video is not real content
      if (/^[\s>•]*(?:https?:\/\/\S+|\[[^\]]+\]\([^)]*\))\s*$/m.test(body) && !/\n{2,}/.test(answer)) {
        continue;
      }
      const ok = emit(cfg, {
        sourcePath: relPath,
        title,
        stem: title.length >= MIN_STEM ? title : `${dirName}：${title}`,
        topic: dirName,
        answer,
        points: extractPoints(answer),
        jobFamily: inferJobFamily(`${title}\n${dirName}`, '后端'),
        dimension: inferDimension(title, 'DEPTH'),
        difficulty: /设计|架构|实现|优化|原理|底层|源码/.test(title) ? 'HARD' : 'MEDIUM',
        tags: ['面试题', dirName],
        company,
        language: 'zh-CN',
      });
      if (ok) keptHere += 1;
    }
    if (keptHere === 0) skip(cfg, isVideoDir ? 'videoLinkOnly' : 'noBody');
  }

  // ---------- 面经/*.txt (real interview question lists, no answers)
  const mjDir = path.join(base, '面经');
  if (fs.existsSync(mjDir)) {
    for (const file of walkFiles(mjDir, (f) => f.endsWith('.txt'))) {
      repoStats(cfg).filesScanned += 1;
      const text = readText(file);
      if (!text.trim()) {
        skip(cfg, 'emptyFile');
        continue;
      }
      const companyM = text.match(/面试公司[:：]\s*([^\n\r]+)/);
      const roleM = text.match(/面试岗位[:：]\s*([^\n\r]+)/);
      const company = companyM ? normalizeCompany(companyM[1]) : normalizeCompany(path.basename(file).replace(/面经.*$/, ''));
      const role = roleM ? cleanInline(roleM[1]) : '';
      const jobFamily = inferJobFamily(`${path.basename(file)} ${role}`, '通用');
      const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
      let found = 0;
      for (const line of lines) {
        const m = line.match(/^(?:\d{1,3}[.．、)]|\d{1,3}\s)\s*(.+)$/);
        if (!m) continue;
        const q = cleanQuestionText(m[1]);
        if (q.length < 10 || q.length > 300) continue;
        if (!/[?？]|怎么|什么|如何|为什么|为何|区别|原理|是否|哪些|介绍|实现|设计|优化|对比|说说|讲讲|有没有|能否/.test(q)) continue;
        found += 1;
        emit(cfg, {
          sourcePath: rel(cfg, file),
          title: q,
          stem: q,
          answer: '',
          points: [],
          jobFamily,
          dimension: inferDimension(q, 'DEPTH'),
          difficulty: 'MEDIUM',
          tags: ['面经', role].filter(Boolean),
          company,
          language: 'zh-CN',
        });
      }
      if (!found) skip(cfg, 'noQuestionLine');
    }
  }
}

function parseBatCode(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  for (const file of walkFiles(base, (f) => f.endsWith('.md'))) {
    repoStats(cfg).filesScanned += 1;
    const relPath = rel(cfg, file);
    if (/README\.md$/i.test(relPath) || /指南|提示词|LICENSE/i.test(relPath)) {
      skip(cfg, 'nonQuestionDoc');
      continue;
    }
    const text = readText(file);
    // bat_code markdown files are nowcoder link indexes, no question body
    const body = cleanMarkdown(text.replace(/^#[^\n]*\n/, ''));
    const links = (text.match(/\[[^\]]+\]\(http/g) || []).length;
    if (links > 0 && body.length < 400) {
      skip(cfg, 'linkOnly');
      continue;
    }
    const heads = [...text.matchAll(/^#{1,4}\s+(.+)$/gm)];
    let kept = 0;
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      const start = h.index + h[0].length;
      const end = i + 1 < heads.length ? heads[i + 1].index : text.length;
      const sectionBody = text.slice(start, end);
      const answer = cleanMarkdown(sectionBody);
      if (answer.length < 60 || /\[[^\]]+\]\(http/.test(sectionBody) && answer.length < 120) continue;
      const title = cleanInline(h[1]);
      const company = normalizeCompany(path.basename(path.dirname(file)));
      kept += emit(cfg, {
        sourcePath: relPath,
        title,
        stem: title,
        answer,
        points: extractPoints(answer),
        jobFamily: inferJobFamily(`${title} ${company || ''}`, '算法'),
        dimension: 'DEPTH',
        difficulty: 'MEDIUM',
        tags: ['笔试题', company].filter(Boolean),
        company,
        language: 'zh-CN',
      }) ? 1 : 0;
    }
    if (!kept) skip(cfg, 'linkOnly');
  }
  for (const file of walkFiles(base, (f) => f.endsWith('.ipynb'))) {
    repoStats(cfg).filesScanned += 1;
    skip(cfg, 'codeNotebook');
  }
}

function parseSystemDesign(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  const dir = path.join(base, 'data/guides');
  const questionRe = /^(what|how|why|when|which|who|where|can|could|is|are|do|does|did|should|will|would|the difference|difference|explain|top \d+)\b/i;
  for (const file of walkFiles(dir, (f) => f.endsWith('.md'))) {
    repoStats(cfg).filesScanned += 1;
    const text = readText(file);
    const { data: fm, body } = loadYamlFrontMatter(text);
    if (!fm || !fm.title) {
      skip(cfg, 'badFrontMatter');
      continue;
    }
    const title = cleanInline(Array.isArray(fm.title) ? fm.title[0] : fm.title);
    if (!questionRe.test(title) && !/[?？]$/.test(title)) {
      skip(cfg, 'notAQuestion');
      continue;
    }
    const answer = cleanMarkdown(body);
    if (answer.length < 80) {
      skip(cfg, 'answerTooShort');
      continue;
    }
    const cats = Array.isArray(fm.categories) ? fm.categories : [];
    emit(cfg, {
      sourcePath: rel(cfg, file),
      title,
      stem: title,
      answer,
      points: extractPoints(answer),
      jobFamily: '系统设计',
      dimension: 'DEPTH',
      difficulty: answer.length > 3000 ? 'HARD' : 'MEDIUM',
      tags: ['系统设计', ...(cats || []), ...(Array.isArray(fm.tags) ? fm.tags : [])].slice(0, 6),
      company: null,
      language: 'en',
    });
  }
}

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (const ch of line) {
    if (ch === '"') {
      quoted = !quoted;
      continue;
    }
    if (ch === ',' && !quoted) {
      out.push(cur);
      cur = '';
    } else cur += ch;
  }
  out.push(cur);
  return out;
}

function parseCodejeet(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  const compDir = path.join(base, 'data/companies');
  const companyIndex = new Map(); // title lower -> {company, freq, rows}
  for (const file of walkFiles(compDir, (f) => f.endsWith('.csv'))) {
    repoStats(cfg).filesScanned += 1;
    const company = normalizeCompany(path.basename(file, '.csv'));
    const lines = readText(file).split(/\r?\n/);
    for (let i = 1; i < lines.length; i++) {
      if (!lines[i].trim()) continue;
      const cells = parseCsvLine(lines[i]);
      if (cells.length < 4) continue;
      const title = cells[2].trim().toLowerCase();
      const freq = Number(String(cells[5] || '').replace('%', '')) || 0;
      if (!title) continue;
      const prev = companyIndex.get(title);
      if (!prev || freq > prev.freq) {
        companyIndex.set(title, { company, freq, rows: (prev?.rows || 0) + 1 });
      } else {
        prev.rows += 1;
      }
    }
  }

  const probDir = path.join(base, 'data/problems');
  const seenTitles = new Set();
  for (const file of walkFiles(probDir, (f) => f.endsWith('.json'))) {
    repoStats(cfg).filesScanned += 1;
    let data;
    try {
      data = JSON.parse(readText(file));
    } catch {
      skip(cfg, 'badJson');
      continue;
    }
    if (!data.title) {
      skip(cfg, 'badJson');
      continue;
    }
    const title = cleanInline(data.title);
    seenTitles.add(title.toLowerCase());
    const hit = companyIndex.get(title.toLowerCase());
    const topics = Array.isArray(data.topics) ? data.topics : [];
    const diff = { Easy: 'EASY', Medium: 'MEDIUM', Hard: 'HARD' }[data.difficulty] || 'MEDIUM';
    const statement = cleanMarkdown(data.content_markdown || data.question || '');
    const noStatement = statement.length < 20;
    const stem = noStatement
      ? `${title}（LeetCode ${data.id}${topics.length ? `，${topics[0]}` : ''}）`
      : `${title}\n\n${statement}`;
    emit(cfg, {
      sourcePath: rel(cfg, file),
      title,
      stem,
      answer: '',
      points: [],
      jobFamily: '算法',
      dimension: 'DEPTH',
      difficulty: diff,
      tags: ['LeetCode', ...topics, data.category, hit?.company].filter(Boolean).slice(0, 7),
      company: hit?.company || null,
      language: 'en',
    });
  }

  // company CSV titles that have no problem-detail JSON (mostly premium/extra)
  const csvOnly = new Map();
  for (const file of walkFiles(compDir, (f) => f.endsWith('.csv'))) {
    const company = normalizeCompany(path.basename(file, '.csv'));
    const lines = readText(file).split(/\r?\n/);
    for (let i = 1; i < lines.length; i++) {
      const cells = parseCsvLine(lines[i]);
      if (cells.length < 8) continue;
      const title = cleanInline(cells[2]);
      if (!title || seenTitles.has(title.toLowerCase())) continue;
      if (!csvOnly.has(title.toLowerCase())) {
        csvOnly.set(title.toLowerCase(), {
          title,
          company,
          topics: cells[6] ? cleanInline(cells[6]) : '',
          difficulty: { Easy: 'EASY', Medium: 'MEDIUM', Hard: 'HARD' }[cells[3]] || 'MEDIUM',
          sourcePath: rel(cfg, file),
        });
      }
    }
  }
  for (const v of csvOnly.values()) {
    emit(cfg, {
      sourcePath: v.sourcePath,
      title: v.title,
      stem: `${v.title}（${v.topics || v.company}）`,
      answer: '',
      points: [],
      jobFamily: '算法',
      dimension: 'DEPTH',
      difficulty: v.difficulty,
      tags: ['LeetCode', v.company, v.topics].filter(Boolean),
      company: v.company,
      language: 'en',
    });
  }

  // blog articles / frontend pages are not Q&A
  for (const file of walkFiles(path.join(base, 'content'), (f) => f.endsWith('.md'))) {
    repoStats(cfg).filesScanned += 1;
    skip(cfg, 'articleNotQuestion');
  }
}

/* --------------------------------------------------------------- cleanup */

function deleteMedia(cfg) {
  if (KEEP_MEDIA) return;
  const base = path.join(BANKS_DIR, cfg.dir);
  const st = repoStats(cfg);
  for (const file of walkFiles(base)) {
    const ext = path.extname(file).toLowerCase();
    const isMedia = MEDIA_EXT.has(ext) || LARGE_BINARY_EXT.has(ext);
    const isImage = IMAGE_EXT.has(ext);
    if (!isMedia && !isImage) continue;
    let size = 0;
    try {
      size = fs.statSync(file).size;
    } catch {
      continue;
    }
    try {
      fs.unlinkSync(file);
    } catch {
      continue;
    }
    if (fs.existsSync(file)) continue;
    if (isMedia) {
      st.mediaDeleted += 1;
      st.mediaBytes += size;
    } else {
      st.imagesDeleted += 1;
      st.imagesBytes += size;
    }
  }
}

/* -------------------------------------------------------------- manifest */

function fmtBytes(n) {
  if (n > 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
  if (n > 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${n} B`;
}

/**
 * Media may have been removed on a previous run; git knows about tracked files
 * that are gone, so use it to keep the manifest cumulative across re-runs.
 */
function gitDeletionCounts(cfg) {
  const base = path.join(BANKS_DIR, cfg.dir);
  if (!fs.existsSync(path.join(base, '.git'))) return null;
  try {
    const out = execFileSync('git', ['-C', base, 'status', '--porcelain'], {
      encoding: 'utf8',
      maxBuffer: 64 * 1024 * 1024,
    });
    let media = 0;
    let images = 0;
    for (const line of out.split('\n')) {
      if (!line.startsWith(' D')) continue;
      let p = line.slice(2).trim();
      if (p.startsWith('"') && p.endsWith('"')) p = p.slice(1, -1);
      const ext = path.extname(p).toLowerCase();
      if (MEDIA_EXT.has(ext) || LARGE_BINARY_EXT.has(ext)) media += 1;
      else if (IMAGE_EXT.has(ext)) images += 1;
    }
    return { media, images };
  } catch {
    return null;
  }
}

function writeManifest(startedAt) {
  const repos = {};
  for (const cfg of REPOS) {
    const st = repoStats(cfg);
    const git = gitDeletionCounts(cfg);
    const mediaDeleted = Math.max(st.mediaDeleted, git?.media || 0);
    const imagesDeleted = Math.max(st.imagesDeleted, git?.images || 0);
    repos[cfg.key] = {
      repo: cfg.repo,
      directory: cfg.dir,
      license: cfg.license,
      licenseFile: cfg.licenseFile,
      branch: cfg.branch,
      total: st.total,
      kept: st.kept,
      skipped: st.skipped,
      skippedReasons: st.reasons,
      mediaDeleted,
      mediaDeletedBytes: st.mediaBytes,
      imagesDeleted,
      imagesDeletedBytes: st.imagesBytes,
      filesScanned: st.filesScanned,
      durationMs: st.durationMs,
    };
  }
  const manifest = {
    scriptVersion: SCRIPT_VERSION,
    nodeVersion: process.version,
    generatedAt: new Date().toISOString(),
    startedAt,
    output: path.relative(ROOT, OUT_JSONL).split(path.sep).join('/'),
    totalRecords: written,
    repos,
    totals: {
      total: Object.values(repos).reduce((a, r) => a + r.total, 0),
      kept: Object.values(repos).reduce((a, r) => a + r.kept, 0),
      skipped: Object.values(repos).reduce((a, r) => a + r.skipped, 0),
      mediaDeleted: Object.values(repos).reduce((a, r) => a + r.mediaDeleted, 0),
      imagesDeleted: Object.values(repos).reduce((a, r) => a + r.imagesDeleted, 0),
    },
  };
  fs.writeFileSync(OUT_MANIFEST, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  return manifest;
}

function writeLicenses() {
  const lines = [];
  lines.push('# 题库数据源许可证与版权说明');
  lines.push('');
  lines.push(`> 生成时间：${new Date().toISOString()}　脚本版本：${SCRIPT_VERSION}`);
  lines.push('> 本文件由 `scripts/qbank/build-question-bank.mjs` 生成。以下信息来自各仓库 LICENSE 文件与 GitHub 仓库页。');
  lines.push('');
  lines.push('| 仓库 | 目录 | License (SPDX) | 版权归属 | 公开分发风险 |');
  lines.push('| --- | --- | --- | --- | --- |');
  const risk = {
    bat_code: '高：无许可证即默认保留全部权利',
    'campus-interview-questions': '高：NC 禁止商业使用 + SA 传染',
    mianstack: '低：保留 MIT 声明即可',
    'ai-agent-interview-240': '低：保留 MIT 声明即可',
    'agent-interview': '低：保留 MIT 声明即可',
    'system-design-101': '高：NC + ND（禁止演绎）',
    codejeet: '高：GPL-3.0 传染 + 上游/LeetCode 版权',
  };
  for (const cfg of REPOS) {
    lines.push(`| [${cfg.repo}](https://github.com/${cfg.repo}) | \`data/question-banks/${cfg.dir}/\` | ${cfg.license} | ${cfg.copyright.replace(/\|/g, '\\|')} | ${risk[cfg.key]} |`);
  }
  lines.push('');
  lines.push('## 使用限制');
  lines.push('');
  for (const cfg of REPOS) {
    lines.push(`### ${cfg.repo}`);
    lines.push('');
    lines.push(`- 仓库地址：https://github.com/${cfg.repo}`);
    lines.push(`- License 文件：${cfg.licenseFile ? `\`${cfg.licenseFile}\`` : '无（UNLICENSED）'}`);
    lines.push(`- SPDX：\`${cfg.license}\``);
    lines.push(`- 版权归属：${cfg.copyright}`);
    lines.push(`- 说明：${cfg.licenseNote}`);
    lines.push('');
  }
  lines.push('## 复核提醒');
  lines.push('');
  lines.push('1. **bat_code** 没有任何 LICENSE 文件，按著作权法默认保留所有权利，严格来说不能复制、改写或公开分发；上线前必须联系作者取得书面授权或将该数据源从公开产物中移除。');
  lines.push('2. **0voice/Campus_recruitment_interview_questions** 在 GitHub 被识别为 `NOASSERTION`，但仓库内 `LICENSE.md` 明确写的是 **CC BY-NC-SA 4.0**：禁止商业用途、必须署名、衍生作品需同协议共享。将题目并入商业产品（含付费 OfferCoach）前需取得授权。');
  lines.push('3. **ByteByteGoHq/system-design-101** 在 GitHub 被识别为 `NOASSERTION`，仓库内 `LICENSE.md` 为 **CC BY-NC-ND 4.0**：**ND 禁止演绎**。本脚本对正文做了 Markdown 清洗与结构化，属于演绎行为，风险最高；建议仅内部评测使用或替换该数据源。');
  lines.push('4. **ayush-that/codejeet** 为 **GPL-3.0**，且题目数据转引自 `liquidslr/interview-company-wise-problems`，LeetCode 题干版权归 LeetCode。GPL 传染性与第三方版权都需要法务复核，公开分发前建议剔除或取得授权。');
  lines.push('5. **mianstack / ai-agent-interview-240 / agent-interview** 为 MIT：分发时保留原始版权声明与 LICENSE 文本。mianstack 题目与答案中引用的第三方文章链接仅作参考，不随本数据集分发。');
  lines.push('6. 本数据集**仅保留文字题目与答案/题解/面经**，未包含任何视频、音频或图片；克隆目录中的媒体文件已由脚本删除。');
  lines.push('');
  fs.writeFileSync(OUT_LICENSES, `${lines.join('\n')}\n`, 'utf8');
}

/* ------------------------------------------------------------ validation */

async function validate() {
  const REQUIRED_KEYS = [
    'sourceRepo', 'sourcePath', 'sourceUrl', 'sourceLicense', 'title', 'stem',
    'referenceAnswer', 'referencePoints', 'jobFamily', 'dimension', 'difficulty',
    'tags', 'company', 'language',
  ];
  const DIMENSIONS = new Set(['DEPTH', 'PROJECT', 'BEHAVIOR', 'FIT']);
  const DIFFICULTIES = new Set(['EASY', 'MEDIUM', 'HARD']);
  const perRepo = {};
  let lines = 0;
  let errors = 0;
  const samples = [];

  const rl = readline.createInterface({ input: fs.createReadStream(OUT_JSONL, 'utf8'), crlfDelay: Infinity });
  for await (const line of rl) {
    if (!line.trim()) continue;
    lines += 1;
    let obj;
    try {
      obj = JSON.parse(line);
    } catch (e) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: JSON.parse failed: ${e.message}`);
      continue;
    }
    const keys = Object.keys(obj);
    if (keys.length !== REQUIRED_KEYS.length || !REQUIRED_KEYS.every((k) => keys.includes(k))) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: field set mismatch`);
    }
    for (const k of ['sourceRepo', 'sourcePath', 'sourceUrl', 'sourceLicense', 'title', 'stem', 'jobFamily', 'dimension', 'difficulty', 'language']) {
      if (typeof obj[k] !== 'string' || !obj[k].trim()) {
        errors += 1;
        if (samples.length < 5) samples.push(`line ${lines}: empty ${k}`);
      }
    }
    if (!DIMENSIONS.has(obj.dimension)) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: bad dimension ${obj.dimension}`);
    }
    if (!DIFFICULTIES.has(obj.difficulty)) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: bad difficulty ${obj.difficulty}`);
    }
    if (obj.stem.length < MIN_STEM || obj.stem.length > MAX_STEM) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: stem length ${obj.stem.length}`);
    }
    if (!Array.isArray(obj.referencePoints) || !Array.isArray(obj.tags)) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: non-array field`);
    }
    if (!obj.sourceUrl.startsWith(`https://github.com/${obj.sourceRepo}/blob/`)) {
      errors += 1;
      if (samples.length < 5) samples.push(`line ${lines}: bad sourceUrl ${obj.sourceUrl}`);
    }
    perRepo[obj.sourceRepo] = (perRepo[obj.sourceRepo] || 0) + 1;
  }

  console.log('\n=== validation ===');
  console.log(`JSONL lines: ${lines}`);
  console.log(`JSON.parse / schema errors: ${errors}`);
  if (samples.length) console.log(`samples:\n  ${samples.join('\n  ')}`);
  const table = REPOS.map((cfg) => [cfg.key, perRepo[cfg.repo] || 0]);
  const width = Math.max(...table.map(([k]) => k.length));
  for (const [k, v] of table) console.log(`  ${k.padEnd(width)}  ${String(v).padStart(6)}`);
  console.log(`  ${'TOTAL'.padEnd(width)}  ${String(lines).padStart(6)}`);
  if (errors > 0) throw new Error(`validation failed with ${errors} error(s)`);
  if (lines < 3000) throw new Error(`expected >= 3000 records, got ${lines}`);
  return { lines, perRepo };
}

/* ------------------------------------------------------------------ main */

async function main() {
  const startedAt = new Date().toISOString();
  fs.mkdirSync(NORM_DIR, { recursive: true });
  fs.writeFileSync(OUT_JSONL, '', 'utf8');
  outFd = fs.openSync(OUT_JSONL, 'a');

  const parsers = [
    ['mianstack', parseMianstack],
    ['agent-interview', parseAgentInterview],
    ['ai-agent-interview-240', parseAiAgentInterview],
    ['campus-interview-questions', parseCampus],
    ['system-design-101', parseSystemDesign],
    ['bat_code', parseBatCode],
    ['codejeet', parseCodejeet],
  ];

  for (const [key, fn] of parsers) {
    const cfg = REPOS.find((r) => r.key === key);
    const dir = path.join(BANKS_DIR, cfg.dir);
    if (!fs.existsSync(dir)) {
      console.warn(`[skip] ${cfg.dir} not found under data/question-banks`);
      repoStats(cfg);
      continue;
    }
    const t0 = Date.now();
    console.log(`[parse] ${cfg.repo} ...`);
    try {
      fn(cfg);
    } catch (e) {
      console.error(`[error] ${cfg.repo}: ${e.stack || e.message}`);
      skip(cfg, 'parserError');
    }
    repoStats(cfg).durationMs = Date.now() - t0;
    const st = repoStats(cfg);
    console.log(`        candidates=${st.total} kept=${st.kept} skipped=${st.skipped} dup=${st.reasons.duplicateStem || 0}`);
  }

  fs.closeSync(outFd);
  outFd = null;

  console.log('\n[cleanup] deleting media / large binaries / images inside clone dirs');
  for (const cfg of REPOS) {
    if (fs.existsSync(path.join(BANKS_DIR, cfg.dir))) deleteMedia(cfg);
  }

  const manifest = writeManifest(startedAt);
  writeLicenses();

  console.log('\n=== per repo ===');
  for (const cfg of REPOS) {
    const r = manifest.repos[cfg.key];
    console.log(
      `  ${cfg.key.padEnd(28)} total=${String(r.total).padStart(6)} kept=${String(r.kept).padStart(5)} ` +
        `skipped=${String(r.skipped).padStart(6)} mediaDel=${r.mediaDeleted} imgDel=${r.imagesDeleted}`,
    );
  }
  console.log(`  media deleted: ${manifest.totals.mediaDeleted} files`);
  console.log(`  images deleted: ${manifest.totals.imagesDeleted} files`);

  const result = await validate();
  console.log(`\n[ok] wrote ${result.lines} records -> ${path.relative(ROOT, OUT_JSONL)}`);
  console.log(`[ok] manifest -> ${path.relative(ROOT, OUT_MANIFEST)}`);
  console.log(`[ok] licenses -> ${path.relative(ROOT, OUT_LICENSES)}`);
}

main().catch((e) => {
  if (outFd !== null) {
    try { fs.closeSync(outFd); } catch {}
  }
  console.error(e.stack || e.message);
  process.exit(1);
});