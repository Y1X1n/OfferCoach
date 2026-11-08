# 后端服务实现

> 状态：生效 ｜ 版本：v1.3 ｜ 更新：2026-11-07
> 技术栈：JDK 21、Spring Boot 3.3.5、Spring Cloud 2023.0.3（Leyton）、MyBatis-Plus、Flyway、LangChain4j 1.0
> 关联：[架构](../design/architecture.md)、[数据模型](../design/data-model.md)、[API](../design/api.md)

## 1. 工程结构

```
backend/
├── pom.xml                       # dependencyManagement 锁定版本
├── oc-common/                    # Result、ErrorCode、BizException、Trace、RedisKeys
├── oc-ai-client/                 # AI 服务客户端 + LangChain4j 配置
├── oc-identity/                  # 账号、JWT、订阅、额度
├── oc-interview/                 # 简历、JD、会话、回答、报告
├── oc-admin/                     # 题库、Prompt、实验、日志查询
├── oc-report-worker/             # RabbitMQ 消费者 + 报告编排
├── oc-gateway/                   # Spring Cloud Gateway（WebFlux）
└── oc-app/                       # 聚合启动（MVP 单体部署）
```

**模块依赖**：

```
oc-app → oc-identity / oc-interview / oc-admin / oc-report-worker
oc-interview → oc-ai-client, oc-common
oc-identity  → oc-common
oc-admin     → oc-ai-client, oc-common
oc-ai-client → oc-common
oc-gateway   → oc-common
```

禁止循环依赖；用 ArchUnit 在 CI 中强制（`ModuleDependencyTest`）。

**工程约束（融合后）**：

- 统一 parent 管理版本；子模块不得出现同一 GAV 多版本；同类依赖用统一版本属性（如 `${spring-cloud.version}`）。
- 开发/联调可用 `SNAPSHOT`；**生产镜像与发布制品禁止 `SNAPSHOT`**，CI 阻断。
- `oc-common` 承担 core 职责，只放错误码、异常、Trace、Redis Key 等稳定契约；依赖保持最小，不传递泄漏。
- 单体阶段不引入 Feign/Nacos；拆分服务时新增 `xxx-api` / `xxx-service`，Feign 必须带 `FallbackFactory`。
- 格式化统一 **4 空格、120 列、UTF-8、LF**；使用 Spotless + Palantir Java Format，不再使用 2 空格的 google-java-format。

## 2. 分层职责

```
Controller（协议层）
  ↓ DTO
Application Service（用例编排、事务边界）
  ↓ Domain / Command
Domain Service（状态机、规则）
  ↓
Repository / Client（持久化、外部调用）
```

| 层 | 可以做 | 禁止 |
|---|---|---|
| Controller | 参数校验、鉴权注解、DTO 转换、HTTP 语义 | 业务规则、直接调 Mapper、事务 |
| Application Service | 用例编排、事务、幂等、跨聚合协调 | 拼接 SQL、返回 Entity、处理 HTTP |
| Domain | 状态迁移、额度规则、评分计算 | 依赖 Spring Web、读写 Cookie |
| Repository | CRUD、行锁、分页查询 | 业务判断、调用外部服务 |
| Client | 超时、重试、熔断、协议转换 | 业务事务、吞异常 |

### 2.1 包结构（以 interview 为例）

```
com.offercoach.interview
├── api/                 # Controller + DTO（request/response）
├── application/         # InterviewAppService、AnswerAppService、ReportAppService
├── domain/
│   ├── model/           # Session、Question、Answer 聚合
│   ├── service/         # SessionStateMachine、QuotaPolicy
│   └── event/           # ReportRequested
├── infrastructure/
│   ├── persistence/     # Mapper、RepositoryImpl、DO
│   ├── client/          # AiOrchestratorClient、IdentityClient
│   ├── mq/              # OutboxRelay、Producer
│   └── cache/           # ContextStore
└── config/
```

### 2.2 实现不变量

| # | 不变量 |
|---|---|
| 1 | Entity 不出 Service 层；对外返回 DTO / PageResult / void |
| 2 | Controller 不写业务规则、不 try-catch、不拼 SQL；只接参、调 Service、包响应 |
| 3 | 不跨模块读写表；跨域只走内部 API 或事件 |
| 4 | 长耗时调用（LLM/HTTP）不得放在数据库事务内；先提交事务，再调用或发消息 |
| 5 | 状态推进只走动作端点；落库必须条件更新 `WHERE status=期望值` |
| 6 | 资源归属服务端校验；不信请求体里的 `userId` / `sessionId` |
| 7 | 计费/创建类写请求必须支持幂等键；重复请求返回首次结果 |
| 8 | 额度以 DB 流水为准；Redis 只做快速拒绝，余额不允许为负 |
| 9 | 时间用 `OffsetDateTime`/`TIMESTAMPTZ`；ID 出参字符串化；金额用整数分 |
| 10 | 业务异常必须带 `ErrorCode`；对外错误不含堆栈、SQL、上游原文 |
| 11 | 日志/Trace 不含简历、回答、Prompt 正文与完整 PII |

## 3. 通用返回与异常

### 3.1 统一异常

```java
public class BizException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    public BizException(ErrorCode code, Object... args) {
        super(code.format(args));
        this.code = code;
        this.details = Map.of();
    }

    public BizException(ErrorCode code, Map<String, Object> details, Object... args) {
        super(code.format(args));
        this.code = code;
        this.details = Map.copyOf(details);
    }
    // getters
}
```

`ErrorCode` 枚举持有 `httpStatus`、`code`、`messageTemplate`，与 [api.md](../design/api.md) §1.6 错误码表一一对应。全局异常处理器：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiError> handleBiz(BizException e, HttpServletRequest req) {
        ErrorCode code = e.getCode();
        return ResponseEntity.status(code.httpStatus())
            .body(ApiError.of(code, e.getMessage(), e.getDetails(), TraceContext.requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return ResponseEntity.badRequest()
            .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "参数校验失败", Map.of("fields", fields), TraceContext.requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e) {
        log.error("unhandled error traceId={}", TraceContext.traceId(), e);
        return ResponseEntity.internalServerError()
            .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试", Map.of(), TraceContext.requestId()));
    }
}
```

**规则**：

- 业务异常必须携带 `ErrorCode`，禁止 `throw new RuntimeException("...")`。
- 对外错误信息不包含 SQL、堆栈、上游响应；完整异常只进日志。
- 404 统一用于“不存在或无权访问”，避免资源枚举。
- 可预判的空指针/越界先做前置判断，不使用异常做流程控制；捕获具体异常，不吞异常。
- 事务内 `catch` 后必须显式回滚；`finally` 不放 `return`；资源用 try-with-resources。
- 对外 HTTP 保持 `ApiError` 无信封；未来改为跨服务 RPC 时才引入 `Result<T>`，避免双形态。
- Repository/DAO 不写业务判断、不打业务日志；异常日志在 Application/Service 层记录场景与堆栈。

## 4. 状态机与并发控制

### 4.1 会话状态机

```java
public enum SessionStatus {
    CREATED, PREPARING, READY, IN_PROGRESS, PAUSED,
    FINISHED, REPORT_GENERATING, REPORT_FAILED, COMPLETED, FAILED, ABANDONED;

    private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED = Map.ofEntries(
        Map.entry(CREATED, Set.of(PREPARING, FAILED, ABANDONED)),
        Map.entry(PREPARING, Set.of(READY, FAILED)),
        Map.entry(READY, Set.of(IN_PROGRESS, ABANDONED)),
        Map.entry(IN_PROGRESS, Set.of(PAUSED, FINISHED, ABANDONED)),
        Map.entry(PAUSED, Set.of(IN_PROGRESS, ABANDONED)),
        Map.entry(FINISHED, Set.of(REPORT_GENERATING)),
        Map.entry(REPORT_GENERATING, Set.of(COMPLETED, REPORT_FAILED)),
        Map.entry(REPORT_FAILED, Set.of(REPORT_GENERATING)),
        Map.entry(FAILED, Set.of()),
        Map.entry(COMPLETED, Set.of()),
        Map.entry(ABANDONED, Set.of())
    );

    public void assertCanTransitTo(SessionStatus target) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(target)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, this, target);
        }
    }
}
```

- 所有状态变更使用条件更新：`UPDATE interview_session SET status=#{target} WHERE id=#{id} AND status=#{expected}`，影响行数为 0 则抛 `STATE_CONFLICT`。
- 同一用户仅允许一个活跃会话，由部分唯一索引兜底（[data-model.md](../design/data-model.md) §5）。
- 提交回答时对 `session` 加 Redis 锁（5s），并用 `answer(question_id)` 唯一索引防重。

### 4.2 乐观锁

`user_quota.version` 用于额度并发更新兜底；主路径使用 `SELECT ... FOR UPDATE`。

## 5. 额度服务（强一致）

```java
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final UserQuotaMapper quotaMapper;
    private final QuotaLedgerMapper ledgerMapper;

    @Transactional
    public QuotaResult charge(Long userId, QuotaType type, int amount,
                              String reason, String refType, Long refId, String idemKey) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");

        UserQuota quota = quotaMapper.selectForUpdate(userId);   // SELECT ... FOR UPDATE
        if (quota == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);

        QuotaLedger existing = ledgerMapper.findByIdemKey(userId, idemKey);
        if (existing != null) {
            return QuotaResult.alreadyProcessed(existing.getBalanceAfter());
        }

        int balance = switch (type) {
            case INTERVIEW -> quota.getInterviewCredits();
            case RESUME -> quota.getResumeCredits();
        };
        if (balance < amount) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED,
                Map.of("required", amount, "balance", balance, "planCode", quota.getPlanCode()));
        }

        int updated = quotaMapper.decrease(userId, type, amount); // WHERE credits >= amount
        if (updated == 0) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED);
        }

        ledgerMapper.insert(QuotaLedger.builder()
            .userId(userId).quotaType(type).delta(-amount).balanceAfter(balance - amount)
            .reason(reason).refType(refType).refId(refId)
            .idempotencyKey(idemKey).build());

        return new QuotaResult(true, balance - amount);
    }
}
```

**要点**：

- 幂等检查在行锁内完成；同一用户的扣减请求被 `SELECT ... FOR UPDATE` 串行化，`UNIQUE(user_id, idempotency_key)` 只作为最后防线。
- **不要**在事务内 `catch` 唯一键冲突后继续查询：PostgreSQL 会把当前事务标记为 aborted，后续 SQL 全部失败。正确做法是让异常回滚，调用方重试；重试会命中已有流水并直接返回。
- 返还额度使用同一方法的正数版本，`reason` 区分 `SESSION_REFUND`、`PARSE_REFUND`、`ADMIN_COMPENSATION`。
- 初始化/发放额度必须同事务写 `PLAN_GRANT` 流水，保证 `quota_ledger` 是唯一事实源。
- 管理端补额度必须写 `audit_log`，且 `delta` 上限 100/次。
- 余额绝不允许为负；`CHECK` 约束兜底。

## 6. 幂等实现

### 6.1 注解 + 拦截器

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String keyHeader() default "Idempotency-Key";
    int ttlHours() default 24;
    boolean required() default true;
}
```

流程：

1. 读取 `Idempotency-Key`；缺失且 `required=true` → `VALIDATION_FAILED`。
2. 计算 `requestHash = sha256(method + path + userId + canonicalJson(body))`。
3. `INSERT ... ON CONFLICT DO NOTHING` 写入 `idempotency_record(IN_PROGRESS)`；冲突则读取已有记录：
   - `COMPLETED` 且 hash 相同 → 直接回放状态码与响应体；
   - hash 不同 → `409 DUPLICATE_REQUEST`；
   - `IN_PROGRESS` → `409 DUPLICATE_REQUEST`，`Retry-After: 1`。
4. 执行业务，`ContentCachingResponseWrapper` 捕获响应。
5. 同事务更新记录为 `COMPLETED` 并写入响应；事务回滚则记录一并回滚，允许重试。
6. Redis `SET NX` 作为快速拦截，DB 为准。

### 6.2 回答去重

- `interview_answer.question_id` 唯一索引：同一题只能一条。
- 捕获唯一键冲突后查询并返回 `409 DUPLICATE_ANSWER` + 已有 `answerId`。
- 内容相似度检测（SimHash，汉明距离 ≤ 3）异步执行；命中只记日志与风控标记，不阻塞提交，避免误伤。

## 7. 评分提交编排

```java
@Service
@RequiredArgsConstructor
public class AnswerAppService {

    private final SessionRepository sessionRepo;
    private final AnswerRepository answerRepo;
    private final ContextStore contextStore;
    private final StreamTokenService tokenService;
    private final ContentSafetyService safety;

    @Transactional
    public SubmitAnswerResponse submit(Long userId, Long sessionId, SubmitAnswerCommand cmd) {
        InterviewSession session = sessionRepo.findOwned(userId, sessionId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            session.getStatus().assertCanTransitTo(SessionStatus.IN_PROGRESS);
        }

        InterviewQuestion question = sessionRepo.findQuestion(sessionId, cmd.questionId())
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!question.getStatus().equals("PENDING") && !question.getStatus().equals("ASKED")) {
            Answer existing = answerRepo.findByQuestionId(question.getId()).orElseThrow();
            throw new BizException(ErrorCode.DUPLICATE_ANSWER,
                Map.of("answerId", existing.getId().toString()));
        }

        safety.check(cmd.answerText());   // 违规抛 CONTENT_REJECTED，不扣额度

        InterviewAnswer answer = answerRepo.insertPending(session, question, cmd);
        sessionRepo.markQuestionAnswered(question.getId(), answer.getId());
        sessionRepo.touch(sessionId);

        ScoringContext ctx = contextStore.build(session, question, answer);
        contextStore.putScoreContext(answer.getId(), ctx);   // Redis TTL 15min

        String streamToken = tokenService.issue(userId, answer.getId(), Duration.ofMinutes(5));
        return SubmitAnswerResponse.of(answer, streamToken);
    }
}
```

**评分结果回写**（内部接口，幂等）：

```java
@Transactional
public FeedbackResponse saveFeedback(FeedbackCallback cmd) {
    var existing = feedbackRepo.findByRequestId(cmd.requestId());
    if (existing != null) {
        return FeedbackResponse.of(existing);   // 同一次评分的重复回调
    }
    var latest = feedbackRepo.findLatestByAnswerId(cmd.answerId());
    AnswerFeedback feedback = feedbackRepo.insert(cmd, latest == null ? 1 : latest.getVersion() + 1);
    answerRepo.markScored(cmd.answerId());
    sessionRepo.incrementAnswered(cmd.sessionId());
    aiCallLogWriter.writeAsync(cmd.toLog());
    return FeedbackResponse.of(feedback);
}
```

- 回写失败由 AI 服务重试（指数退避 3 次）；仍失败则回答状态 `FAILED`，前端提供“重新评分”。
- 回写按 `answerId` 加分布式锁（或 `SELECT ... FOR UPDATE` 锁回答行），避免并发重试产生重复版本。
- 重新评分沿用同一 `answerId`，不重复扣额度；每次生成新 `version`，旧版本保留供管理端审计。

## 8. AI 服务客户端

### 8.1 接口

```java
public interface AiOrchestratorClient {
    QuestionGenResponse generateQuestions(QuestionGenRequest request);
    ResumeParseResponse parseResume(ResumeParseRequest request);
    JdAnalyzeResponse analyzeJd(JdAnalyzeRequest request);
    ReportDraft generateReport(ReportGenRequest request);
    TranscribeResponse transcribe(TranscribeRequest request);
}
```

### 8.2 实现约束

```java
@Bean
public AiOrchestratorClient aiClient(AiProperties props) {
    var httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
        .responseTimeout(Duration.ofSeconds(props.responseTimeoutSec()))
        .compress(true);

    var factory = new ReactorClientHttpConnector(httpClient);
    return HttpServiceProxyFactory.builderFor(WebClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.serviceToken())
            .clientConnector(factory)
            .build())
        .build()
        .createClient(AiOrchestratorClient.class);
}
```

- 连接超时 2s；普通请求 30s；报告生成 90s。
- Resilience4j：熔断（失败率 50%，样本 20，打开 30s）、舱壁（报告并发 8）、重试（仅幂等 GET/带幂等键的 POST）。
- 所有请求透传 `X-Request-Id`、`traceparent`；日志记录 `sessionId`、`scene`、耗时。
- 禁止在业务事务内调用 AI 服务（长耗时）；先提交事务，再调用或发消息。
- Python 服务返回 4xx 时视为契约错误，记录告警，不重试。

### 8.3 LangChain4j 使用范围

```java
public interface ReportPolisher {
    @SystemMessage(fromResource = "prompts/report_polish.txt")
    String polish(@UserMessage String draft);
}

@Bean
public ReportPolisher reportPolisher(ChatModel model) {
    return AiServices.builder(ReportPolisher.class)
        .chatModel(model)
        .build();
}
```

| 场景 | 是否使用 LangChain4j | 说明 |
|---|---|---|
| 报告文案润色 | 是 | 单轮、无工具、输出 ≤ 800 字 |
| 管理端 Prompt 试跑 | 是 | 便于管理员快速验证 |
| 评分降级链路 | 是 | 非流式、固定模板、标记 `degraded=true` |
| 简历解析/出题/正式评分 | 否 | 统一由 Python 实现，避免 Prompt 双维护 |

**约束**：LangChain4j 的模型配置从统一 `AiProperties` 读取；超时 30s；禁止在循环中调用；输出必须经过长度与敏感词检查。

## 9. RabbitMQ 与 Outbox

### 9.1 消息信封

```json
{
  "eventId": "9b2f...",
  "eventType": "report.requested",
  "occurredAt": "2026-11-07T10:00:00Z",
  "traceId": "4bf92f...",
  "version": 1,
  "payload": {"reportId": "60001", "sessionId": "9001", "userId": "10001"}
}
```

### 9.2 拓扑

| 名称 | 类型 | 配置 |
|---|---|---|
| `oc.topic` | topic exchange | durable |
| `report.request.q` | quorum queue | `x-dead-letter-exchange=oc.dlx`，消费者并发 4 |
| `report.retry.5s.q` | quorum queue | `x-message-ttl=5000`，DLX 回 `oc.topic` |
| `report.retry.30s.q` | quorum queue | `x-message-ttl=30000` |
| `report.retry.120s.q` | quorum queue | `x-message-ttl=120000` |
| `report.dlq` | quorum queue | 人工处理 |
| `notify.topic` | topic exchange | 报告就绪、额度不足提醒 |

- 生产者开启 publisher confirm + returns；失败进入 Outbox 重试。
- 消费者手动 ACK；业务成功后 ACK，失败按重试次数路由到对应 TTL 队列；超过 3 次进 DLQ。
- 消费幂等：`reportId + attempt` 唯一，已 `READY` 直接 ACK。

### 9.3 Outbox Relay

```java
@Scheduled(fixedDelay = 500)
public void relay() {
    List<OutboxEvent> batch = outboxMapper.lockPending(100);  // FOR UPDATE SKIP LOCKED
    for (OutboxEvent e : batch) {
        try {
            rabbitTemplate.convertAndSend("oc.topic", e.getEventType(), e.toEnvelope(),
                msg -> { msg.getMessageProperties().setMessageId(e.getId().toString()); return msg; });
            outboxMapper.markSent(e.getId());
        } catch (Exception ex) {
            outboxMapper.markFailed(e.getId(), nextRetryAt(e.getRetryCount()));
        }
    }
}
```

- `FOR UPDATE SKIP LOCKED` 支持多实例并行。
- 发送成功以 confirm 回调为准；仅 `convertAndSend` 返回不代表 broker 已持久化。
- Outbox 表 7 天清理；积压 > 1000 告警。

## 10. 报告 Worker

```java
@RabbitListener(queues = "report.request.q", concurrency = "4")
public void onReportRequested(ReportRequestedMessage msg) {
    if (!reportRepo.tryStartAttempt(msg.reportId())) return;

    try {
        ReportDraft draft = aiClient.generateReport(msg.toRequest());
        reportRepo.saveReady(msg.reportId(), draft);
        sessionRepo.markCompleted(msg.sessionId());
        outboxRepo.enqueue("REPORT", msg.reportId(), "notify.report_ready",
            Map.of("userId", msg.userId(), "reportId", msg.reportId()));
    } catch (AiTimeoutException | AiUnavailableException e) {
        if (msg.attempt() >= 3) {
            reportRepo.markFailed(msg.reportId(), e.code());
            sessionRepo.markReportFailed(msg.sessionId());
            outboxRepo.enqueue("REPORT", msg.reportId(), "notify.report_failed", Map.of("userId", msg.userId()));
        } else {
            throw new RetryableException(msg.attempt() + 1, e);
        }
    }
}
```

- `tryStartAttempt` 用条件更新（`status='GENERATING' AND attempts<3`）同时完成幂等与并发控制；返回 0 行说明已生成或已达上限，直接 ACK。
- 报告生成超时 90s；期间每 5s 更新 Redis 进度，供前端展示。
- PDF 生成独立任务，复用同一 Worker，失败不阻塞报告主体。

## 11. 缓存规范

| 场景 | 策略 | TTL | 失效 |
|---|---|---|---|
| 用户额度 | Cache-Aside | 60s | 扣减/返还后删除 |
| 简历/JD 摘要 | Cache-Aside | 7d | 内容更新后删除 |
| Prompt 当前版本 | 启动加载 + 定时刷新 | 60s | 发布后主动删除 + 广播 |
| 会话上下文 | Write-Through | 2h | 会话结束删除 |
| 评分上下文 | Write-Once | 15m | 自然过期 |
| 套餐列表 | Cache-Aside | 5m | 管理端更新后删除 |

**禁止**：缓存 `null` 超过 1 分钟；缓存全量用户列表；把 Token、密码、完整简历写入 Redis。

## 12. 安全实现

- **JWT**：RS256 密钥从 K8s Secret/环境变量加载；`kid` 支持轮换；校验 `iss/aud/exp/nbf`。prod 缺 PEM 必须启动失败，禁止回退临时密钥。
- **会话吊销**：Refresh 使用 `familyId` 家族，旧 Token 重放即整族吊销；登出同时黑名单 Access；Redis 故障时吊销校验 fail-closed。
- **限流**：注册 3/min/IP、登录 10/min/IP；同账号 5 次失败锁定 15 分钟，返回 429。
- **RBAC**：`@PreAuthorize("hasRole('ADMIN')")`；Service 层再次校验资源归属（防越权 ID）。
- **文件**：`Content-Type` 白名单 + magic number 校验 + 大小限制 + ClamAV；存储路径不使用原始文件名。
- **SQL**：MyBatis 全部 `#{}` 参数化；动态排序字段使用枚举白名单。
- **SSRF**：MVP 不抓取用户提供的 URL；V1.2 仅允许白名单域名并禁止内网地址。
- **Prompt 注入**：用户内容用定界标签包裹；AI 服务不暴露工具；输出经过 Schema 校验；详见 [key-challenges.md](../design/key-challenges.md) 难点一/二/五。
- **敏感信息**：日志脱敏手机号/邮箱；异常信息不回传上游原文；管理端查看原始 Prompt 需二次确认并审计。
- **CORS**：仅允许配置的前端域名；`allowCredentials=true` 时禁止 `*`。
- **上传**：限制请求体大小；临时文件用后即删；禁用目录遍历。

## 13. 可观测性

- **日志**：JSON 结构化，字段 `ts/level/service/traceId/requestId/userId/sessionId/scene/latencyMs/code`；不记录正文。
- **指标**：Micrometer + Prometheus；HTTP RED、JVM、Hikari、线程池、MQ 深度、Outbox 积压、AI 调用耗时与 Token。
- **链路**：OpenTelemetry 自动埋点；跨 Java → Python 透传 `traceparent`；SSE 记录 `streamId`。
- **审计**：管理端写操作统一 AOP 记录前后值；密钥、密码字段强制脱敏。

## 14. 配置管理

```yaml
oc:
  auth:
    access-token-ttl: 15m
    refresh-token-ttl: 7d
    jwt:
      issuer: offercoach
      audience: offercoach-web
  quota:
    free-monthly-interviews: 3
    free-monthly-resumes: 3
    daily-session-cap: 3
  rate-limit:
    login-per-minute: 10
    register-per-minute: 3
    fail-threshold: 5
    fail-window: 15m
  ai:
    base-url: http://ai-orchestrator:8000
    service-token: ${AI_SERVICE_TOKEN}
    response-timeout-sec: 30
    report-timeout-sec: 90
  upload:
    max-size: 10MB
    allowed-types: application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document
  rate-limit:
    login-per-minute: 10
    create-interview-per-minute: 5
```

- 敏感配置只从环境变量/Secret 注入；仓库不提交真实密钥。
- 配置变更需重启或走配置中心；业务开关（降级、灰度）支持热更新。
- 每个环境独立 `application-{env}.yml`；禁止本地默认值连接生产。

## 15. 代码质量门禁

| 检查 | 工具 | 阈值 |
|---|---|---|
| 格式 | Spotless + Palantir Java Format（4 空格/120 列） | 必须通过 |
| 静态检查 | Checkstyle + ErrorProne | 0 error |
| 架构 | ArchUnit | 无循环依赖、分层合规 |
| 覆盖率 | JaCoCo | 核心域 ≥ 70%，整体 ≥ 55% |
| 漏洞 | OWASP Dependency-Check / Trivy | 无 High/Critical |
| 契约 | springdoc + oasdiff | 无未声明破坏性变更 |
| 复杂度 | Sonar | 方法认知复杂度 ≤ 15 |

**提交前**：`./mvnw spotless:apply verify`；CI 失败禁止合并。

### 15.1 联调前自测清单

- [ ] `.http`/Postman 覆盖成功、参数校验失败、未登录、越权、404、状态冲突。
- [ ] Service 规则单测：状态机、额度扣减/返还、幂等、评分加权、上下文裁剪。
- [ ] Testcontainers：PG + Redis + MQ 关键路径跑通；迁移从空库与上一版本均成功。
- [ ] 重复提交同一 `Idempotency-Key` 返回首次结果；不同 Key 同题返回 `DUPLICATE_ANSWER`。
- [ ] 并发创建会话只成功 1 个；并发扣额度余额不为负。
- [ ] SSE 用 `curl -N` 验证逐块 flush；客户端 abort 后上游停止生成。
- [ ] OpenAPI 响应 Schema 校验通过；字段名/类型/可空性与文档一致。
- [ ] AI 输出：JSON 合法率 100%；修复失败有明确错误码；降级标记可见。
- [ ] 日志含 `requestId/traceId`，不含正文与 PII；错误码与 [api.md](../design/api.md) 一致。
- [ ] 种子数据/演示账号可用；本地 Compose 一条命令起服务。

## 16. 常见错误清单

1. 在 `@Transactional` 内调用 LLM/HTTP 长请求，导致连接池耗尽。
2. 先发 MQ 再提交数据库，消息早于数据可见。
3. 用 Redis 计数作为额度唯一依据，宕机后超额。
4. 状态更新不带 `WHERE status=expected`，并发下跳状态。
5. 捕获异常后返回 `200` 空对象，前端无法区分失败。
6. 把 `Entity` 直接序列化给前端，泄露内部字段（如 `passwordHash`）。
7. SSE 接口被网关 `ModifyResponseBody` 或压缩缓冲，导致“假流式”。
8. 重试非幂等请求，造成重复扣额度或重复报告。
9. 在日志中打印完整简历、回答、Prompt，造成隐私泄露。
10. 管理端只做前端隐藏，后端未校验角色。

## 17. 编码红线（外部规范融合）

> 来源：阿里巴巴 Java 开发手册（2024-08-19）与 Spring Cloud 实践规范；采纳/适配明细见 [评审记录](../reviews/backend-spec-fusion-2026-11-07.md)。

### 17.1 命名与结构

- 类 `UpperCamelCase`；方法/参数/变量 `lowerCamelCase`；常量 `UPPER_SNAKE_CASE`；包名全小写、单数、无拼音/中文。
- Service/Repository 暴露接口，实现类后缀 `Impl`；异常类后缀 `Exception`；测试类 `<被测类>Test`。
- DO 对应表、DTO 对外传输；禁止 `xxxPOJO`；布尔字段不加 `is` 前缀。
- 应用服务方法用 `get/list/count/create/update/delete`；Repository 可保留 `find*`。
- 禁止魔法值；`long` 字面量用 `L`；常量按域拆分，不集中到单个常量类。

### 17.2 格式与注释

- 4 空格缩进、120 列、UTF-8、LF；空块 `{}` 单行；`{` 前不换行；运算符两侧空格。
- 类、公共方法、枚举值必须有 Javadoc；`TODO`/`FIXME` 写清人与日期。
- 公共契约写“做什么/约束/异常”，内部显然代码不写复述式注释；改代码必须同步改注释。

### 17.3 OOP 与集合

- DTO/DO 用包装类型，不设默认值，必须实现 `toString`，构造器不放业务逻辑；`equals/hashCode` 成对重写。
- 循环删除用 `Iterator`；`Comparator` 必须满足对称性与传递性；`subList`/`Arrays.asList` 不写回原集合。
- Map 遍历用 `entrySet`；大集合预估初始容量；`ConcurrentHashMap` 的 key/value 不允许 null。

### 17.4 并发

- 禁用 `Executors`，线程池用 `ThreadPoolExecutor`，线程命名、队列有界；`Timer` 改 `ScheduledExecutorService`。
- 时间格式化用 `DateTimeFormatter`；`ThreadLocal` 用 `static`；`volatile` 只解决可见性，计数用 `LongAdder/Atomic*`。
- 多资源锁固定顺序；锁内禁止 RPC/LLM/长 I/O；乐观锁冲突重试 ≥ 3 次。

### 17.5 日志与测试

- 日志用 SLF4J + 占位符，禁止字符串拼接与生产 DEBUG；异常日志必须含场景 + 堆栈。
- `ERROR` 只用于系统/重要错误，参数错误用 `WARN`；禁止重复 appender 与 PII。
- 单测遵循 AIR（自动、独立、可重复），禁止 `System.out`，用断言；不依赖共享环境，集成用 Testcontainers。
