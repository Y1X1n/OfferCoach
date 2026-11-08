# 题库构建脚本（qbank）

把 `data/question-banks/` 下 7 个公开 GitHub 仓库（shallow clone）解析成统一的 JSONL 题库，仅保留**文字题目 + 答案/题解/面经**，并清理克隆目录内的视频、音频、图片与大二进制文件。

- 脚本：`scripts/qbank/build-question-bank.mjs`
- 运行时：Node 24（只用 `fs` / `path` / `crypto` / `readline` / `child_process` 等内置模块，**无 npm 依赖**）
- 输出：
  - `data/question-bank/normalized/questions.jsonl`
  - `data/question-bank/normalized/manifest.json`
  - `data/question-bank/LICENSES.md`（自动生成）

## 运行

```bash
# 项目根目录 E:/My_project
node scripts/qbank/build-question-bank.mjs

# 保留克隆目录内的媒体文件（默认会删除）
node scripts/qbank/build-question-bank.mjs --keep-media
```

脚本每次运行会**重写** `questions.jsonl` 与 `manifest.json`。媒体清理是幂等的：已删除的 tracked 文件通过 `git status` 累计统计，因此 manifest 里的 `mediaDeleted` / `imagesDeleted` 不会因重复运行而归零。

### 前置条件：7 个仓库

```bash
cd E:/My_project/data
mkdir -p question-banks && cd question-banks
git clone --depth 1 https://github.com/DerekLin924/bat_code.git bat_code
git clone --depth 1 https://github.com/0voice/Campus_recruitment_interview_questions.git campus-interview-questions
git clone --depth 1 https://github.com/didilili/mianstack.git mianstack
git clone --depth 1 https://github.com/SuperGODOG/ai-agent-interview-240.git ai-agent-interview-240
git clone --depth 1 https://github.com/cdavid817/agent-interview.git agent-interview
git clone --depth 1 https://github.com/ByteByteGoHq/system-design-101.git system-design-101
git clone --depth 1 https://github.com/ayush-that/codejeet.git codejeet
```

缺少某个目录时脚本会打印 `[skip] ... not found` 并继续，其余仓库仍会产出。

## 输出格式

每行一个 JSON，字段固定为：

```json
{"sourceRepo":"owner/repo","sourcePath":"questions/Q-0001.md","sourceUrl":"https://github.com/owner/repo/blob/main/questions/Q-0001.md","sourceLicense":"MIT","title":"...","stem":"...","referenceAnswer":"...","referencePoints":["..."],"jobFamily":"AI-Agent","dimension":"DEPTH","difficulty":"MEDIUM","tags":["..."],"company":null,"language":"zh-CN"}
```

| 字段 | 规则 |
| --- | --- |
| `sourceRepo` | `owner/repo` |
| `sourcePath` | 相对克隆目录的 POSIX 路径 |
| `sourceUrl` | `https://github.com/<repo>/blob/<branch>/<urlencoded path>`（分支：bat_code 为 `master`，其余为 `main`） |
| `sourceLicense` | SPDX：`UNLICENSED` / `CC-BY-NC-SA-4.0` / `MIT` / `CC-BY-NC-ND-4.0` / `GPL-3.0` |
| `title` | 去 Markdown 后的短标题（≤200 字符） |
| `stem` | 题干（10–2000 字符）；跨仓库按「NFKC + 小写 + 去空白/标点」的 SHA-256 去重，保留首次出现（mianstack 优先） |
| `referenceAnswer` | 仓库中的答案/题解/面经正文；没有则为空字符串。上游只有答题框架的（如 ai-agent-interview-240 的 729 题）**不伪造答案**，留空 |
| `referencePoints` | 从答案/拆解中抽取的 2–5 条要点；不足 2 条或没有答案则为 `[]`（表格行、导航链接、以冒号结尾的标题会被剔除） |
| `jobFamily` | 中文短标签：`后端` `前端` `算法` `AI-Agent` `系统设计` `测试` `数据` `通用` |
| `dimension` | 仅 `DEPTH`（技术/八股/系统设计/算法）、`PROJECT`（项目）、`BEHAVIOR`（行为面）、`FIT`（自我介绍/职业规划/岗位匹配/反问） |
| `difficulty` | 仅 `EASY` / `MEDIUM` / `HARD`，按来源档位与题型保守映射 |
| `tags` | 分类/主题/技术栈/来源标记（≤8 个） |
| `company` | 从目录、面经元数据、标题括注、CSV 文件名提取并归一化；没有则 `null` |
| `language` | 中文 `zh-CN`；纯英文内容 `en`（codejeet、system-design-101） |

## 各仓库解析策略

| 仓库 | 数据文件 | 策略 | 保留 |
| --- | --- | --- | --- |
| mianstack | `questions/*.md`（8430，JSON front matter + 答案正文）、`interviews/*.md`（958 面经）、`collections/*.json`（356 题集索引） | 题目映射 front matter（category/type/level/topic）；答案取正文并截断到 `## 对应教程`/`## 参考资料`（去课程广告）；面经从 `## 原帖正文` 的数字/问号行抽题；`collections` 只有标题+外链，按「纯链接页」跳过 | 19657 |
| agent-interview | `docs/01..04/**/*.md`（118 文件，2038 题） | 按 `<a id>` + `### N. 题目` 切分；答案取正文，要点取「核心思路/深入拆解」，无结构则从句段抽取；标题括注（腾讯二面等）提取公司 | 2026 |
| ai-agent-interview-240 | `docs/01-面试八股文/*.md`（729 题）、`projects/*/项目内作答.md` | 题库只有四步答题框架、没有真实答案 → 题干入库、答案留空；项目内作答（11 条）作为 `PROJECT` 维度真实答案 | 726 |
| campus-interview-questions | `京东/腾讯/阿里/百度/网易/搜狐/readme.md`（真实文字 Q&A）、`面经/*.txt`（37 份面经）、其余 `*视频讲解/readme.md` | 只解析 `## N. 题目` + 正文的 readme；面经按数字行抽题（仅保留问句特征行），公司取 `📍面试公司：` 或文件名；**视频讲解目录只有 B 站链接，全部跳过** | 428 |
| system-design-101 | `data/guides/*.md`（400 篇英文讲解） | 只收标题为疑问句/What-How-Why 的讲解（155 篇），正文即参考答案；非问句的清单/书单跳过 | 155 |
| bat_code | `*/[a-z]*.md`、`README.md`、`Leetcode/*.ipynb` | 正文只有牛客专栏外链，属纯链接页；`同步指南/操作指南/更新提示词` 与 Jupyter 代码笔记不是题目 → 全部跳过 | 0 |
| codejeet | `data/problems/*.json`（3310）、`data/companies/*.csv`（714）、`content/blog/*.md`（2739） | LeetCode 题干 + 难度 + topics；公司按 CSV 中该题出现频率最高的公司赋值；528 道无题干（premium）保留为「标题（题号/topic）」；博客文章不是题目，跳过 | 3393 |

## 媒体清理

脚本在解析完成后遍历 7 个克隆目录（不触碰 `.git`）：

- 删除视频/音频：`.mp4/.mkv/.avi/.mov/.flv/.wmv/.webm/.m4v/.mp3/.wav/.m4a/.ogg/.flac/...`
- 删除大二进制/图片：`.apkg`（agent-interview 的 Anki 卡组，2 个共 7 MB）、`.zip/.7z/.rar/.pdf`，以及 `.png/.jpg/.svg/.webp` 等（209 张，约 51 MB）
- 本次实际结果：音频/视频 0 个（7 个仓库本身不含媒体文件），大二进制 2 个、图片 209 张。

## 已知缺口与数据质量问题

1. **bat_code 保留 0 条**：仓库是牛客笔试专栏的「标题 + 链接」索引，没有题面/答案；按「纯链接页」规则跳过，若要使用需另行抓取外链（注意版权）。
2. **mianstack `collections/` 未入库**：356 个 JSON、15901 条只有 `originalTitle + sourceUrl` 的题集索引，属于纯链接页；其中大量题目标题与 `questions/` 重复。
3. **mianstack 7739 道题 `answerStatus=pending`**：只有题目没有答案，已按规则留空；仅 690 道有参考答案。
4. **ai-agent-interview-240 的 729 题没有真实答案**：仓库只提供通用的「四步答题框架/核心考点」，为避免伪造答案，`referenceAnswer` 留空；其 85 篇语言八股是主题式文章而非题目，未入库。
5. **近义题未合并**：只按标准化题干做精确 SHA-256 去重，同一考点的不同问法会并存（尤其 mianstack 面经）。
6. **英文数据（codejeet / system-design-101）无中文翻译**，`language=en`；codejeet 不提供题解，`referenceAnswer` 为空。
7. **company 归一化基于关键词表**，生僻公司可能保留英文原名或为空；codejeet 的 company 取该题在 CSV 中频率最高的公司，不代表唯一出处。
8. **license 高风险**：bat_code 无 LICENSE；campus 为 CC BY-NC-SA 4.0；system-design-101 为 CC BY-NC-ND 4.0（禁止演绎）；codejeet 为 GPL-3.0 且题干来自 LeetCode。公开分发/商业化前必须复核，详见 `data/question-bank/LICENSES.md`。

## 校验

脚本写入后会重新逐行读取 JSONL，校验 `JSON.parse`、字段集合、枚举值、必填字段、题干长度与 `sourceUrl` 前缀；错误数 > 0 或总条数 < 3000 时以非零码退出。正常输出形如：

```text
=== validation ===
JSONL lines: 26385
JSON.parse / schema errors: 0
  mianstack                    19657
  ...
  TOTAL                        26385
```