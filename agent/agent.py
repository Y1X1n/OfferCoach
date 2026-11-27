#!/usr/bin/env python3
"""OfferCoach 面试教练 · 千问 AI 平台独立功能检测入口。

本入口复用作品的业务处理链路（简历结构化 → 岗位解析与匹配 → 按缺口出题 →
逐题评分 → 复盘报告），只把「HTTP 后端」换成本进程内的编排，模型调用仍走
OpenAI 兼容的 Chat Completions 接口。

运行约束（与赛事检测环境一致）：
- 仅标准库；不读取本地文件作为模型输入；不依赖外部检索、数据库或小程序页面。
- 模型地址与密钥只从 DASHSCOPE_API_KEY / DASHSCOPE_BASE_URL / OPENAI_BASE_URL 读取。
- 业务样例随包提供（samples/ 技术岗、samples/nursing/ 护理岗），日志写入 $AGENT_LOG_DIR/agent.log。
- 支持 `--prompt "..."` 与 `--version`；正常完成退出码 0。
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

VERSION = "1.0.0"
AGENT_NAME = "OfferCoach 面试教练"

BASE_DIR = Path(__file__).resolve().parent
SAMPLES_DIR = BASE_DIR / "samples"

DEFAULT_MODEL = "qwen-plus"
MAX_TOTAL_SECONDS = 20 * 60
MAX_CALL_SECONDS = 90
MAX_ATTEMPTS = 5

LOGGER = logging.getLogger("offercoach.agent")


# --------------------------------------------------------------------------- #
# 日志
# --------------------------------------------------------------------------- #
def setup_logging() -> Path:
    log_dir = Path(os.environ.get("AGENT_LOG_DIR") or (BASE_DIR / "logs"))
    try:
        log_dir.mkdir(parents=True, exist_ok=True)
    except OSError:
        log_dir = BASE_DIR
    log_path = log_dir / "agent.log"

    LOGGER.setLevel(logging.INFO)
    LOGGER.handlers.clear()
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")

    file_handler = logging.FileHandler(log_path, encoding="utf-8")
    file_handler.setFormatter(formatter)
    LOGGER.addHandler(file_handler)

    stream_handler = logging.StreamHandler(sys.stdout)
    stream_handler.setFormatter(formatter)
    LOGGER.addHandler(stream_handler)
    return log_path


# --------------------------------------------------------------------------- #
# 模型客户端
# --------------------------------------------------------------------------- #
class ModelError(RuntimeError):
    """模型调用失败（重试耗尽或配置缺失）。"""


@dataclass
class ChatResult:
    text: str
    model: str
    prompt_tokens: int = 0
    completion_tokens: int = 0


@dataclass
class ModelClient:
    api_key: str
    base_url: str
    model: str = DEFAULT_MODEL
    timeout: int = MAX_CALL_SECONDS
    calls: int = 0
    tokens: int = 0
    started_at: float = field(default_factory=time.monotonic)

    @classmethod
    def from_env(cls) -> "ModelClient":
        api_key = (os.environ.get("DASHSCOPE_API_KEY") or "").strip()
        base_url = (os.environ.get("OPENAI_BASE_URL") or os.environ.get("DASHSCOPE_BASE_URL") or "").strip()
        if not api_key:
            raise ModelError("缺少环境变量 DASHSCOPE_API_KEY，无法调用模型服务")
        if not base_url:
            raise ModelError("缺少环境变量 DASHSCOPE_BASE_URL / OPENAI_BASE_URL，无法确定模型地址")
        base_url = base_url.rstrip("/")
        return cls(api_key=api_key, base_url=base_url)

    @property
    def endpoint(self) -> str:
        # 预置变量以 /api/v1 或 /v1 结尾，补上资源路径即可。
        if self.base_url.endswith("/chat/completions"):
            return self.base_url
        return self.base_url + "/chat/completions"

    def remaining_budget(self) -> float:
        return MAX_TOTAL_SECONDS - (time.monotonic() - self.started_at)

    def chat(self, messages: List[Dict[str, str]], max_tokens: int = 1200, temperature: float = 0.3) -> ChatResult:
        if self.remaining_budget() <= 30:
            raise ModelError("整体执行时间即将耗尽，停止继续调用模型")

        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + self.api_key,
        }

        last_error: Optional[Exception] = None
        for attempt in range(1, MAX_ATTEMPTS + 1):
            request = urllib.request.Request(self.endpoint, data=body, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    raw = response.read().decode("utf-8", errors="replace")
                data = json.loads(raw)
                text = self._extract_text(data)
                usage = data.get("usage") or {}
                self.calls += 1
                self.tokens += int(usage.get("total_tokens") or 0)
                LOGGER.info(
                    "model call ok attempt=%d model=%s tokens=%s chars=%d",
                    attempt,
                    data.get("model", self.model),
                    usage.get("total_tokens"),
                    len(text),
                )
                return ChatResult(
                    text=text,
                    model=data.get("model", self.model),
                    prompt_tokens=int(usage.get("prompt_tokens") or 0),
                    completion_tokens=int(usage.get("completion_tokens") or 0),
                )
            except urllib.error.HTTPError as error:
                detail = error.read().decode("utf-8", errors="replace")[:400]
                last_error = ModelError(f"HTTP {error.code}: {detail}")
                LOGGER.warning("model call failed attempt=%d status=%s detail=%s", attempt, error.code, detail)
                if error.code in (429, 500, 502, 503, 504):
                    self._sleep_before_retry(attempt, error.headers.get("Retry-After"))
                    continue
                raise last_error from error
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
                last_error = ModelError(f"{type(error).__name__}: {error}")
                LOGGER.warning("model call failed attempt=%d error=%s", attempt, error)
                self._sleep_before_retry(attempt, None)
                continue

        raise ModelError(f"模型调用重试 {MAX_ATTEMPTS} 次仍失败：{last_error}")

    @staticmethod
    def _extract_text(data: Dict[str, Any]) -> str:
        choices = data.get("choices") or []
        if not choices:
            raise ModelError("模型响应缺少 choices")
        message = choices[0].get("message") or {}
        content = message.get("content")
        if isinstance(content, list):
            # 部分兼容实现返回分段内容。
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        if not content:
            raise ModelError("模型响应缺少文本内容")
        return str(content)

    def _sleep_before_retry(self, attempt: int, retry_after: Optional[str]) -> None:
        delay = min(2 ** attempt, 20) + random.uniform(0, 0.8)
        if retry_after:
            try:
                delay = min(max(float(retry_after), 1.0), 30.0)
            except ValueError:
                pass
        time.sleep(delay)


# --------------------------------------------------------------------------- #
# JSON 解析
# --------------------------------------------------------------------------- #
def parse_json_block(text: str) -> Any:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z]*\s*", "", cleaned)
        cleaned = re.sub(r"\s*```$", "", cleaned)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    start_candidates = [idx for idx in (cleaned.find("{"), cleaned.find("[")) if idx >= 0]
    if not start_candidates:
        raise ModelError("模型输出中找不到 JSON：%s" % cleaned[:200])
    start = min(start_candidates)
    opening = cleaned[start]
    closing = "}" if opening == "{" else "]"
    depth = 0
    in_string = False
    escape = False
    for index in range(start, len(cleaned)):
        char = cleaned[index]
        if in_string:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return json.loads(cleaned[start : index + 1])
    raise ModelError("模型输出的 JSON 不完整：%s" % cleaned[:200])


def as_list(value: Any) -> List[Any]:
    return value if isinstance(value, list) else []


def as_text(value: Any, limit: int = 400) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return text[:limit]


# --------------------------------------------------------------------------- #
# 业务样例
# --------------------------------------------------------------------------- #
@dataclass
class Case:
    resume: str
    jd: str
    answers: Dict[str, str]


NURSING_KEYWORDS = ("护理", "护士", "医疗", "临床", "医学", "nursing", "nurse")


def sample_dir(prompt: str) -> Path:
    """按指令关键词选择行业样例：默认技术岗，命中护理/医疗关键词用护理样例。"""
    text = (prompt or "").lower()
    if any(keyword in text for keyword in NURSING_KEYWORDS):
        return SAMPLES_DIR / "nursing"
    return SAMPLES_DIR


def load_case(prompt: str = "") -> Case:
    directory = sample_dir(prompt)
    resume = read_sample("resume.md", directory)
    jd = read_sample("jd.md", directory)
    answers_path = directory / "answers.json"
    if not answers_path.exists():
        raise ModelError(f"缺少随包业务样例：{answers_path}")
    with answers_path.open("r", encoding="utf-8") as handle:
        answers = json.load(handle)
    LOGGER.info("使用行业样例：%s", directory.name)
    return Case(resume=resume, jd=jd, answers=answers)


def read_sample(name: str, directory: Path = None) -> str:
    base = directory or SAMPLES_DIR
    path = base / name
    if not path.exists():
        raise ModelError(f"缺少随包业务样例：{path}")
    return path.read_text(encoding="utf-8").strip()


# --------------------------------------------------------------------------- #
# 业务链路
# --------------------------------------------------------------------------- #
@dataclass
class Pipeline:
    client: ModelClient
    case: Case
    model_name: str = DEFAULT_MODEL
    steps: List[Dict[str, Any]] = field(default_factory=list)

    def record(self, name: str, payload: Any) -> None:
        self.steps.append({"step": name, "payload": payload})
        LOGGER.info("step done: %s", name)

    # 1. 简历结构化
    def parse_resume(self) -> Dict[str, Any]:
        prompt = (
            "你是简历解析器。把候选人简历整理成 JSON，只输出 JSON，不要解释。\n"
            "字段：name(字符串)、years(数字|null)、skills(字符串数组)、"
            "projects(数组，每项含 title/role/action/result)、highlights(字符串数组)。\n"
            "result 必须保留简历里的量化数字；简历没有的字段用 null 或空数组。\n\n"
            "简历原文：\n" + self.case.resume
        )
        result = parse_json_block(self.client.chat([{"role": "user", "content": prompt}]).text)
        if not isinstance(result, dict):
            raise ModelError("简历解析结果不是 JSON 对象")
        self.record("resume_parsed", result)
        return result

    # 2. 岗位解析
    def parse_jd(self) -> Dict[str, Any]:
        prompt = (
            "你是招聘 JD 解析器。把 JD 整理成 JSON，只输出 JSON。\n"
            "字段：title(字符串)、must_have(字符串数组)、nice_to_have(字符串数组)、"
            "responsibilities(字符串数组)。条目要短，不超过 20 字。\n\n"
            "JD 原文：\n" + self.case.jd
        )
        result = parse_json_block(self.client.chat([{"role": "user", "content": prompt}]).text)
        if not isinstance(result, dict):
            raise ModelError("JD 解析结果不是 JSON 对象")
        self.record("jd_parsed", result)
        return result

    # 3. 匹配与缺口
    def match(self, resume: Dict[str, Any], jd: Dict[str, Any]) -> Dict[str, Any]:
        prompt = (
            "你是面试教练。对比候选人简历与目标岗位，输出 JSON，只输出 JSON。\n"
            "字段：fit_score(0-100 的数字)、matched(字符串数组)、gaps(字符串数组)、"
            "interview_focus(字符串数组，3 条以内，指出面试最该验证的点)。\n"
            "判断必须有依据，gap 要具体到技能或经历，不要写空话。\n\n"
            "简历结构化：\n" + json.dumps(resume, ensure_ascii=False)
            + "\n\n岗位结构化：\n" + json.dumps(jd, ensure_ascii=False)
        )
        result = parse_json_block(self.client.chat([{"role": "user", "content": prompt}]).text)
        if not isinstance(result, dict):
            raise ModelError("匹配结果不是 JSON 对象")
        self.record("match", result)
        return result

    # 4. 出题
    def generate_questions(
        self,
        resume: Dict[str, Any],
        jd: Dict[str, Any],
        match: Dict[str, Any],
        count: int = 3,
    ) -> List[Dict[str, Any]]:
        prompt = (
            "你是面试官。基于候选人简历与岗位要求出一场面试题，输出 JSON，只输出 JSON。\n"
            "结构：{\"questions\":[{\"dimension\":\"DEPTH|PROJECT|BEHAVIOR|FIT\","
            "\"difficulty\":\"EASY|MEDIUM|HARD\",\"text\":\"题目\","
            "\"reference_points\":[\"要点\"],\"why\":\"为什么问这题\"}]}\n"
            f"要求：正好 {count} 题；至少 1 题落在 gaps 上；题目要引用简历里的具体项目或 JD 里的具体技能，"
            "不要通用题；每题 reference_points 2-4 条。\n\n"
            "简历结构化：\n" + json.dumps(resume, ensure_ascii=False)
            + "\n\n岗位结构化：\n" + json.dumps(jd, ensure_ascii=False)
            + "\n\n匹配结果：\n" + json.dumps(match, ensure_ascii=False)
        )
        parsed = parse_json_block(self.client.chat([{"role": "user", "content": prompt}], max_tokens=1600).text)
        items = parsed.get("questions") if isinstance(parsed, dict) else parsed
        questions: List[Dict[str, Any]] = []
        for index, item in enumerate(as_list(items)[:count], start=1):
            if not isinstance(item, dict):
                continue
            text = as_text(item.get("text"), 300)
            if not text:
                continue
            questions.append(
                {
                    "seq": index,
                    "dimension": as_text(item.get("dimension"), 20) or "PROJECT",
                    "difficulty": as_text(item.get("difficulty"), 10) or "MEDIUM",
                    "text": text,
                    "reference_points": [as_text(point, 80) for point in as_list(item.get("reference_points"))][:4],
                    "why": as_text(item.get("why"), 160),
                }
            )
        if not questions:
            raise ModelError("出题结果为空")
        self.record("questions", questions)
        return questions

    # 5. 评分
    def score_answer(self, question: Dict[str, Any], answer: str) -> Dict[str, Any]:
        prompt = (
            "你是严格但具体的面试评分员。给候选人回答打分，输出 JSON，只输出 JSON。\n"
            "结构：{\"overall\":0-10 的数字,\"dimensions\":{\"content\":数字,\"structure\":数字,"
            "\"depth\":数字,\"fit\":数字,\"language\":数字},\"strengths\":[\"\"],"
            "\"improvements\":[\"\"],\"evidence\":[{\"quote\":\"回答原文片段\",\"reason\":\"判断依据\"}],"
            "\"rewritten_hint\":\"一句话改写方向\"}\n"
            "要求：evidence.quote 必须逐字来自回答；没有证据就别编；打分区分度要真实，"
            "不要一律给 8 分。\n\n"
            "题目：" + question["text"] + "\n"
            "参考要点：" + json.dumps(question.get("reference_points", []), ensure_ascii=False) + "\n\n"
            "候选人回答：\n" + answer
        )
        result = parse_json_block(self.client.chat([{"role": "user", "content": prompt}]).text)
        if not isinstance(result, dict):
            raise ModelError("评分结果不是 JSON 对象")
        result["seq"] = question["seq"]
        result["question"] = question["text"]
        self.record("answer_scored", result)
        return result

    # 6. 报告
    def build_report(
        self,
        resume: Dict[str, Any],
        jd: Dict[str, Any],
        match: Dict[str, Any],
        scores: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        prompt = (
            "你是面试复盘教练。基于本次面试的评分结果写复盘报告，输出 JSON，只输出 JSON。\n"
            "结构：{\"overall\":0-100 的数字,\"summary\":\"80 字以内\","
            "\"highlights\":[\"\"],\"risks\":[\"\"],"
            "\"plan\":[{\"priority\":1,\"action\":\"\",\"reason\":\"\",\"resource\":\"\"}]}\n"
            "要求：highlights/risks 各 3 条，必须引用具体题目或回答证据；plan 3 条，按优先级排序；"
            "不要写「多加练习」这类无法执行的建议。\n\n"
            "岗位：" + as_text(jd.get("title")) + "\n"
            "匹配分：" + str(match.get("fit_score")) + "\n"
            "逐题评分：\n" + json.dumps(scores, ensure_ascii=False)
        )
        result = parse_json_block(self.client.chat([{"role": "user", "content": prompt}], max_tokens=1400).text)
        if not isinstance(result, dict):
            raise ModelError("报告结果不是 JSON 对象")
        self.record("report", result)
        return result


# --------------------------------------------------------------------------- #
# 报告渲染
# --------------------------------------------------------------------------- #
DIMENSION_LABELS = {
    "content": "内容相关性",
    "structure": "结构化表达",
    "depth": "专业深度",
    "fit": "岗位匹配",
    "language": "语言表达",
}


def render_report(
    resume: Dict[str, Any],
    jd: Dict[str, Any],
    match: Dict[str, Any],
    questions: List[Dict[str, Any]],
    scores: List[Dict[str, Any]],
    report: Dict[str, Any],
) -> str:
    lines: List[str] = []
    lines.append("# 面试复盘报告")
    lines.append("")
    lines.append(f"- 候选人：{as_text(resume.get('name'), 40) or '（未识别姓名）'}")
    lines.append(f"- 目标岗位：{as_text(jd.get('title'), 80) or '（未识别岗位）'}")
    lines.append(f"- 岗位匹配度：{as_text(match.get('fit_score'), 10)}/100")
    lines.append(f"- 综合得分：{as_text(report.get('overall'), 10)}/100")
    lines.append("")
    lines.append("## 结论")
    lines.append("")
    lines.append(as_text(report.get("summary"), 400) or "（模型未给出总结）")
    lines.append("")

    gaps = [as_text(item, 60) for item in as_list(match.get("gaps"))]
    if gaps:
        lines.append("## 能力缺口")
        lines.append("")
        for gap in gaps:
            lines.append(f"- {gap}")
        lines.append("")

    lines.append("## 逐题评分")
    lines.append("")
    for score in scores:
        overall = score.get("overall")
        lines.append(f"### 第 {score.get('seq')} 题 · {as_text(score.get('question'), 120)}")
        lines.append("")
        lines.append(f"- 得分：{as_text(overall, 10)}/10")
        dimensions = score.get("dimensions") if isinstance(score.get("dimensions"), dict) else {}
        for key, value in dimensions.items():
            label = DIMENSION_LABELS.get(key, key)
            lines.append(f"- {label}：{as_text(value, 10)}/10")
        strengths = [as_text(item, 120) for item in as_list(score.get("strengths"))]
        improvements = [as_text(item, 120) for item in as_list(score.get("improvements"))]
        if strengths:
            lines.append("- 做到的地方：" + "；".join(strengths[:3]))
        if improvements:
            lines.append("- 可改进：" + "；".join(improvements[:3]))
        evidence = as_list(score.get("evidence"))
        if evidence:
            lines.append("- 原文证据：")
            for item in evidence[:2]:
                if isinstance(item, dict):
                    lines.append(f"  > “{as_text(item.get('quote'), 160)}” —— {as_text(item.get('reason'), 120)}")
        lines.append("")

    highlights = [as_text(item, 120) for item in as_list(report.get("highlights"))]
    risks = [as_text(item, 120) for item in as_list(report.get("risks"))]
    if highlights:
        lines.append("## 亮点")
        lines.append("")
        for item in highlights:
            lines.append(f"- {item}")
        lines.append("")
    if risks:
        lines.append("## 风险")
        lines.append("")
        for item in risks:
            lines.append(f"- {item}")
        lines.append("")

    plan = as_list(report.get("plan"))
    if plan:
        lines.append("## 改进计划")
        lines.append("")
        for item in plan:
            if not isinstance(item, dict):
                continue
            lines.append(
                f"{item.get('priority', '-')}. {as_text(item.get('action'), 120)}"
                f"（原因：{as_text(item.get('reason'), 100) or '—'}；参考：{as_text(item.get('resource'), 60) or '—'}）"
            )
        lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("> 评分只用于练习参考，不代表任何企业的真实录用结论。")
    return "\n".join(lines)


# --------------------------------------------------------------------------- #
# 入口
# --------------------------------------------------------------------------- #
def write_outputs(report_markdown: str, payload: Dict[str, Any]) -> List[Path]:
    written: List[Path] = []
    log_dir = Path(os.environ.get("AGENT_LOG_DIR") or (BASE_DIR / "logs"))
    targets = [log_dir]
    if Path.cwd() != log_dir:
        targets.append(Path.cwd())

    for target in targets:
        try:
            target.mkdir(parents=True, exist_ok=True)
            report_path = target / "report.md"
            report_path.write_text(report_markdown, encoding="utf-8")
            written.append(report_path)
            result_path = target / "result.json"
            result_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            written.append(result_path)
        except OSError as error:
            LOGGER.warning("写入 %s 失败：%s", target, error)
    return written


def run(prompt: str) -> int:
    LOGGER.info("收到检测指令：%s", prompt[:300])
    client = ModelClient.from_env()
    case = load_case(prompt)
    pipeline = Pipeline(client=client, case=case)

    resume = pipeline.parse_resume()
    jd = pipeline.parse_jd()
    match = pipeline.match(resume, jd)
    questions = pipeline.generate_questions(resume, jd, match, count=3)

    scores: List[Dict[str, Any]] = []
    for question in questions:
        dimension = str(question.get("dimension", "")).upper()
        answer = case.answers.get(dimension) or case.answers.get("DEFAULT") or ""
        if not answer:
            LOGGER.warning("缺少维度 %s 的样例回答，跳过该题", dimension)
            continue
        LOGGER.info("评分第 %s 题（维度 %s）", question.get("seq"), dimension)
        scores.append(pipeline.score_answer(question, answer))

    if not scores:
        raise ModelError("没有可用于评分的回答样例")

    report = pipeline.build_report(resume, jd, match, scores)
    report_markdown = render_report(resume, jd, match, questions, scores, report)

    payload = {
        "agent": AGENT_NAME,
        "version": VERSION,
        "model": client.model,
        "model_calls": client.calls,
        "tokens": client.tokens,
        "resume": resume,
        "jd": jd,
        "match": match,
        "questions": questions,
        "scores": scores,
        "report": report,
        "steps": [step["step"] for step in pipeline.steps],
    }
    written = write_outputs(report_markdown, payload)

    print(report_markdown)
    print("")
    print("----")
    print(f"模型调用 {client.calls} 次，累计 tokens {client.tokens}")
    for path in written:
        print(f"已写入 {path}")
    LOGGER.info("执行完成：calls=%d tokens=%d", client.calls, client.tokens)
    return 0


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=f"{AGENT_NAME} 独立功能检测入口")
    parser.add_argument("--prompt", help="自然语言检测指令")
    parser.add_argument("--version", action="store_true", help="输出版本号并退出")
    args = parser.parse_args(argv)

    setup_logging()

    if args.version:
        print(VERSION)
        return 0

    if not args.prompt:
        LOGGER.error("缺少 --prompt")
        print("用法：python agent.py --prompt \"...\" | python agent.py --version", file=sys.stderr)
        return 2

    try:
        return run(args.prompt)
    except ModelError as error:
        LOGGER.error("执行失败：%s", error)
        print(f"执行失败：{error}", file=sys.stderr)
        return 1
    except Exception as error:  # noqa: BLE001 - 检测入口需要兜底并给出非零退出码
        LOGGER.exception("未预期错误：%s", error)
        print(f"执行失败：{type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
