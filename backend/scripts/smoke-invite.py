#!/usr/bin/env python3
"""生产冒烟：邀请注册、验证码、奖励与运行时策略。只读取服务器 600 权限凭据文件。"""
import json
import os
import random
import re
import secrets
import string
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8081").rstrip("/") + "/api/v1"
PASSWORD = os.environ.get("SMOKE_PASSWORD") or f"Smoke#{secrets.token_hex(4)}a1"


def credentials(path):
    values = {}
    pattern = re.compile(r"^\s*([A-Za-z0-9_]+)\s*[:=]\s*(.*)$")
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = pattern.match(line)
            if match:
                values[match.group(1).strip()] = match.group(2).strip()
    return values


def call(method, path, body=None, token=None, device=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    if device:
        request.add_header("X-Device-Id", device)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            text = response.read().decode()
            return response.status, json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        try:
            return error.code, json.loads(text)
        except json.JSONDecodeError:
            return error.code, {"raw": text[:200]}


def login(email, password):
    status, payload = call("POST", "/auth/login", {"account": email, "password": password})
    assert status == 200, f"login {status} {payload}"
    return payload["accessToken"]


def solve_captcha():
    status, payload = call("POST", "/auth/captcha")
    assert status == 200, f"captcha {status} {payload}"
    match = re.match(r"(\d+) ([+\-×]) (\d+)", payload["question"])
    assert match, payload["question"]
    left, operator, right = int(match.group(1)), match.group(2), int(match.group(3))
    answer = left + right if operator == "+" else left - right if operator == "-" else left * right
    return payload["captchaId"], str(answer)


def register(email, invite=None, captcha=None, device=None):
    captcha_id, captcha_answer = captcha or solve_captcha()
    body = {
        "email": email,
        "password": PASSWORD,
        "nickname": "冒烟测试",
        "captchaId": captcha_id,
        "captchaAnswer": captcha_answer,
    }
    if invite:
        body["inviteCode"] = invite
    return call("POST", "/auth/register", body, device=device or "smoke-" + email)


def pause():
    """注册限流 3/min/IP；分组测试之间等待窗口重置。"""
    time.sleep(65)


def random_email(tag):
    suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"smoke-{tag}-{suffix}@offercoach.dev"


def settings_payload(current):
    keys = [
        "registrationOpen",
        "dailyInterviewLimit",
        "dailyResumeLimit",
        "freeMonthlyInterviews",
        "freeMonthlyResumes",
        "inviteRewardInviterInterview",
        "inviteRewardInviteeResume",
        "inviteDailyCap",
        "inviteTotalCap",
        "registrationIpDailyCap",
        "registrationDeviceDailyCap",
    ]
    return {key: current[key] for key in keys}


def main():
    failures = []

    def check(name, condition, detail=""):
        if condition:
            print(f"PASS {name}")
        else:
            failures.append(name)
            print(f"FAIL {name} {detail}")

    admin = credentials(os.environ["ADMIN_CREDENTIALS_FILE"])
    admin_token = login(admin["email"], admin["password"])

    status, policy = call("GET", "/auth/registration-policy")
    check("policy 200", status == 200, str(policy))
    check("policy open + captcha", policy["registrationOpen"] and policy["requireCaptcha"], str(policy))
    check("policy verifyCode off + invite optional", not policy["requireVerifyCode"] and policy["inviteOptional"])

    status, invite = call("GET", "/users/me/invite", token=admin_token)
    check("admin invite 200", status == 200, str(invite))
    code = invite["inviteCode"] if status == 200 else ""
    check("invite code generated", bool(code))

    before_status, before_quota = call("GET", "/quota", token=admin_token)
    user_a = random_email("a")
    status, registered_a = register(user_a, invite=code)
    check("register with invite 201", status == 201, str(registered_a)[:300])
    token_a = registered_a["accessToken"] if status == 201 else ""
    after_status, after_quota = call("GET", "/quota", token=admin_token)
    if before_status == 200 and after_status == 200 and status == 201:
        check(
            "inviter +1 interview credit",
            after_quota["interviewCredits"] == before_quota["interviewCredits"] + 1,
            f"{before_quota['interviewCredits']} -> {after_quota['interviewCredits']}",
        )
    status, quota_a = call("GET", "/quota", token=token_a)
    check("invitee +1 resume credit", status == 200 and quota_a["resumeCredits"] == 4, str(quota_a))

    status, profile_a = call("GET", "/users/me/invite", token=token_a)
    check("invitee has own code", status == 200 and bool(profile_a["inviteCode"]))
    code_a = profile_a["inviteCode"] if status == 200 else ""

    user_b = random_email("b")
    status, registered_b = register(user_b, invite=code_a)
    check("second invite register 201", status == 201, str(registered_b)[:300])
    status, quota_a_after = call("GET", "/quota", token=token_a)
    check("invitee A +1 interview credit", status == 200 and quota_a_after["interviewCredits"] == 4, str(quota_a_after))

    pause()
    user_c = random_email("c")
    status, invalid = register(user_c, invite="ZZZZZZZZZZ")
    check(
        "invalid invite rejected 400",
        status == 400 and "inviteCode" in json.dumps(invalid, ensure_ascii=False),
        str(invalid)[:300],
    )

    pause()
    captcha = solve_captcha()
    first_user = random_email("d")
    status_first, _ = register(first_user, captcha=captcha)
    second_user = random_email("e")
    status_second, reused = register(second_user, captcha=captcha)
    check("captcha one-time", status_first == 201 and status_second == 400, f"{status_first}/{status_second}")
    if status_second == 400:
        check("captcha reuse message", "captchaAnswer" in json.dumps(reused, ensure_ascii=False), str(reused)[:200])

    status, current = call("GET", "/admin/settings/quota", token=admin_token)
    check("admin settings 200", status == 200, str(current))

    closed = settings_payload(current)
    closed["registrationOpen"] = False
    closed["dailyResumeLimit"] = 2
    status, updated = call("PUT", "/admin/settings/quota", closed, token=admin_token)
    check("admin settings update 200", status == 200 and updated["registrationOpen"] is False, str(updated))
    time.sleep(32)
    status, policy_closed = call("GET", "/auth/registration-policy")
    check(
        "runtime policy propagated",
        status == 200 and policy_closed["registrationOpen"] is False and policy_closed["dailyResumeLimit"] == 2,
        str(policy_closed),
    )
    status, blocked = register(random_email("f"))
    check("registration closed blocked", status == 400, str(blocked)[:300])

    restore = settings_payload(updated)
    restore["registrationOpen"] = True
    restore["dailyResumeLimit"] = 3
    status, restored = call("PUT", "/admin/settings/quota", restore, token=admin_token)
    check("admin settings restored", status == 200 and restored["registrationOpen"] is True, str(restored)[:200])
    time.sleep(32)
    status, policy_open = call("GET", "/auth/registration-policy")
    check("registration reopened", status == 200 and policy_open["registrationOpen"] is True, str(policy_open))

    try:
        demo = credentials(os.environ["DEMO_CREDENTIALS_FILE"])
        demo_token = login(demo["email"], demo["password"])
        status, reports = call("GET", "/reports?page=1&size=5", token=demo_token)
        items = reports.get("items", []) if isinstance(reports, dict) else []
        ready = next((item for item in items if item.get("status") == "READY"), None)
        if ready:
            status, detail = call("GET", f"/reports/{ready['id']}", token=demo_token)
            breakdown = detail.get("questionBreakdown") or []
            first = breakdown[0] if breakdown else {}
            has_answer = bool(first.get("answer"))
            has_feedback = isinstance(first.get("feedback"), dict)
            check("report breakdown answer/evidence", status == 200 and has_answer and has_feedback, str(first)[:300])
        else:
            print("SKIP report breakdown: no READY report for demo user")
    except Exception as exception:  # noqa: BLE001
        print(f"SKIP report breakdown: {type(exception).__name__}")

    if failures:
        print("FAILURES: " + ", ".join(failures))
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())