# 支付（oc-pay）

> 状态：生效 ｜ 版本：v1.0 ｜ 更新：2026-11-17
> 关联：[architecture.md](./architecture.md)（ADR-013）、[api.md](./api.md) §3.2、[data-model.md](./data-model.md) §3、[deployment.md](../ops/deployment.md)

## 1. 定位与边界

`oc-pay` 是支付能力域，负责**订单与渠道**：下单、渠道对接、回调验签、退款、对账。它**不**决定套餐与额度。

- **套餐与额度事实源仍在 `oc-app`**：支付成功后由 `oc-pay` 回调 `oc-app` 内部接口，由 `oc-identity` 幂等发放套餐与额度。
- **金额与商品名以服务端 `subscription_plan` 为准**，前端只传 `planCode` 与渠道，避免篡改。
- 支持渠道：**微信支付 v3**（Native 扫码）、**支付宝**（`alipay.trade.precreate` 扫码）、以及**仅本地联调**的 `MOCK` 渠道。

## 2. 服务形态

顶层只新增一个模块 `oc-pay`（聚合父模块），其下：

| 子模块 | 类型 | 职责 |
|---|---|---|
| `oc-pay-core` | 共享库 | 契约 DTO（`PayDtos`）、配置属性（`PayProperties`）、内部令牌工具、`PayGateway` 客户端（oc-app 使用） |
| `oc-pay-service` | 可独立部署服务 | 订单生命周期、渠道适配器、签名/验签、回调、退款、额度发放通知（端口 **8099**） |

- 服务**仅回环可达**（`SERVER_ADDRESS` 默认 `127.0.0.1`）；内部接口鉴权头 `X-OC-Internal-Token`，未配置令牌时拒绝所有内部调用（fail-closed）。
- 仅**渠道回调路径**（`/pay/notify/{channel}`）由 Nginx 反代暴露到公网；渠道验签即鉴权。
- 与 `oc-ai` 一致：服务共享同一数据库，Flyway 由 `oc-app` 统一管理（`oc-pay-service` 关闭 Flyway），支付表由 `oc-pay-service` 独占写入。

## 3. 渠道能力

| 能力 | 微信支付 v3 | 支付宝 | MOCK |
|---|---|---|---|
| 下单 | Native 扫码（`/v3/pay/transactions/native`） | `alipay.trade.precreate` 扫码 | 返回本地确认链接 |
| 查单 | `GET /v3/pay/transactions/out-trade-no/{no}` | `alipay.trade.query` | 读本地状态 |
| 关单 | `close` | `alipay.trade.close` | 无操作 |
| 退款 | `/v3/refund/domestic/refunds` | `alipay.trade.refund` | 直接成功 |
| 回调验签 | 微信平台证书 / 公钥（`RSA-SHA256`） | 支付宝公钥（`RSA2`） | 无（本地确认端点） |
| 回调解密 | `AES-256-GCM`（APIv3 密钥） | 明文表单（验签） | — |
| 请求签名 | 商户私钥 `SHA256withRSA` | 应用私钥 `RSA2` | — |

## 4. 订单状态机

```
CREATED ──支付成功(回调/对账)──▶ PAID ──退款──▶ REFUNDED
   │                                
   ├── 渠道下单失败 ──▶ FAILED
   └── 超时/用户取消 ──▶ CLOSED
```

- **CREATED**：已下单待支付，带二维码链接与过期时间。
- **PAID**：渠道回调验签通过（或对账补记）后置位；同时置 `notify_status=PENDING` 触发额度发放。
- **CLOSED**：过期或主动关闭。只有「渠道明确返回未支付 **且** 关单被渠道确认」才会置 CLOSED；渠道不可用或关单未确认时保持 CREATED 继续重试，超过宽限期（默认 24h）仍无法确认则置 FAILED 并进入人工队列，避免用户事后支付成功但本地已关单。
- **REFUNDED**：退款累计金额达到订单金额。

## 5. 关键时序

**下单**
1. 前端 `POST /api/v1/orders`（`Idempotency-Key` 必填）→ `oc-app` 校验套餐（存在、`active`、`priceCents>0`）。
2. `oc-app` 经内部接口 `POST /internal/v1/pay/orders` 调用 `oc-pay`；`oc-pay` 落 `payment_order(CREATED)` 后调用渠道下单，回写 `code_url`。
3. 返回订单视图（含二维码链接）给前端展示。

**支付成功**
1. 渠道 → `POST /pay/notify/{channel}`（验签 + 解密 + 时间窗）→ `oc-pay` **交叉校验**：订单存在、渠道一致、金额一致、渠道流水号非空；全部通过才幂等置 `PAID`（`WHERE status='CREATED'`）。
2. `oc-pay` → `POST {oc-app}/internal/v1/payments/notify`（内部令牌）→ `oc-identity` **回查 `payment_order`** 校验用户/套餐/金额/状态后，幂等发放套餐与额度（`payment_entitlement.order_no` 唯一）。
3. 通知失败不回滚支付事实，置 `notify_status=FAILED` 由扫描任务重试；重试耗尽或被 4xx 永久拒绝时置 `DEAD` 并写 `payment_exception`。

**退款**：`oc-pay` 在事务内锁定订单、落退款单并**原子预占**可退金额（条件更新，天然防并发超退）；**事务外**调用渠道退款，成功则记为已退、失败则归还预占金额。重试以同一 `out_refund_no` 幂等重发。

**兜底扫描**（默认每 3 分钟）：关闭过期订单（先查渠道、再确认关单）、对账遗漏回调（主动查渠道补记并校验金额）、重试未成功的额度发放通知。

同步响应（下单 / 查单 / 退款）同样验签：签名覆盖响应里业务节点的原始字节，代码按括号配对切子串，不重新序列化。未配置平台公钥时直接拒绝（`NOT_CONFIGURED`）。**上线前须用支付宝沙箱跑一遍查询与退款**，确认线上响应格式能通过验签。

对账补记与回调走同一套凭据要求：**金额必须可比、渠道流水号必须非空**。渠道只回 `trade_state=SUCCESS` 却不带金额或流水号时，不补记、留在待支付并写 `PAID_UNVERIFIED` 异常——否则一条残缺响应就能把订单置为已支付并发额度。

**异常巡检**（默认每 30 分钟）：存在 `payment_exception.status='OPEN'` 时输出 ERROR 日志，作为告警接入点。

## 6. 幂等与对账

- 下单：`payment_order(user_id, idempotency_key)` 唯一；重复请求返回首次订单。
- 支付：`markPaid` 仅在 `CREATED→PAID` 生效；重复回调与对账不产生重复发放。
- 发放：`payment_entitlement.order_no` 主键唯一；`quota_ledger` 幂等键 `order-purchase:{orderNo}:{TYPE}`；`notify_status=DEAD` 表示终态（重试耗尽或永久拒绝），不再自动重试。
- 退款：`payment_refund(order_no, idempotency_key)` 唯一；行锁保证不超退。
- 渠道交易号 `provider_txn_id` 建部分唯一索引，用于对账去重。
- 兜底扫描的取批路径：额度发放重试走部分索引 `(updated_at) WHERE status='PAID' AND notify_status IN ('PENDING','FAILED')`；对账扫描用已有的 `(status, expires_at)` 并按 `expires_at` 升序取批（`expires_at = created_at + 下单时的有效期`，按到期时间取批既能走区间扫描又天然有序）。

### 6.1 异常与人工处理（payment_exception）

资金已发生但无法自动收敛的场景一律写入 `payment_exception`（`UNIQUE(order_no, kind)`，重复命中不覆盖首次现场），状态为 `OPEN` 时由巡检任务输出 ERROR 日志：

| kind | 含义 | 建议动作 |
|---|---|---|
| `PAID_ON_INACTIVE_ORDER` | 收到支付成功时本地订单已 CLOSED/FAILED | 人工核对：补发或退款 |
| `AMOUNT_MISMATCH` | 回调/渠道金额与订单不一致 | 冻结处理，联系渠道 |
| `PAID_UNVERIFIED` | 对账时渠道称已支付，但响应缺金额或流水号 | 下一轮重查；持续出现则核对渠道后台 |
| `CHANNEL_MISMATCH` | 回调渠道与订单渠道不一致 | 排查配置或伪造 |
| `UNKNOWN_ORDER` | 回调订单号在本地不存在 | 排查跨环境回调 |
| `CLOSE_UNCERTAIN` | 过期且超过宽限期仍无法确认关单 | 核对渠道终态后关闭或退款 |
| `ENTITLEMENT_REJECTED` | 发放被 oc-app 4xx 永久拒绝 | 修复数据后重新触发 |
| `ENTITLEMENT_DEAD` | 发放重试次数耗尽 | 核对订单后补发 |

处理完成后由运维把对应行置为 `RESOLVED`/`IGNORED`（当前无管理端界面，直接操作数据库）。

## 7. 配置（环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `OC_PAY_INTERNAL_TOKEN` | 空 | 服务间令牌；oc-app 与 oc-pay 必须一致；未配置时内部接口一律拒绝（fail-closed） |
| `OC_PAY_BASE_URL` | `http://127.0.0.1:8099` | oc-pay 地址（oc-app 侧使用） |
| `OC_PAY_APP_BASE_URL` | `http://127.0.0.1:8081` | oc-app 地址（oc-pay 回调发放额度使用） |
| `OC_PAY_PUBLIC_BASE_URL` | 空 | 对外回调基地址；启用真实渠道时必填 |
| `OC_PAY_ORDER_EXPIRE_MINUTES` | `30` | 订单过期时长 |
| `OC_PAY_MOCK_ENABLED` | `false` | 本地模拟渠道；生产开启会启动失败 |
| `OC_PAY_WECHAT_ENABLED` / `WECHAT_PAY_*` | 关闭 | 微信支付 v3 商户配置（`APP_ID` / `MCH_ID` / `MCH_SERIAL_NO` / `PRIVATE_KEY_PATH` / `API_V3_KEY` / `PLATFORM_CERT_PATH`） |
| `OC_PAY_ALIPAY_ENABLED` / `ALIPAY_*` | 关闭 | 支付宝配置（`APP_ID` / `PRIVATE_KEY_PATH` / `PUBLIC_KEY_PATH`） |

> 商户私钥、APIv3 密钥、平台证书等凭据**只放服务器端 600 权限文件 / 环境变量**，不落库、不打日志、不进仓库。

## 8. 安全

- 内部接口 fail-closed：令牌未配置时拒绝所有内部调用；常量时间比较。
- 回调验签：微信平台证书验签 + AES-GCM 解密；支付宝 RSA2 验签；验签失败拒绝并留痕（`payment_notify_log`，不存原始报文）。
- `oc-app` 侧安全链仅放行 `POST /internal/v1/payments/notify`，由安全链内的 `InternalTokenFilter` 校验令牌；其余 `/internal/**` 仍 `denyAll`。
- 内部令牌过滤器按容器**解码后**的 `servletPath` 判定 `/internal/**`（不再用原始 `requestURI` 前缀），原始 URI 仍含 `%` 或 `;` 的内部请求直接 400，杜绝 `/%69nternal/...` 这类编码绕过。
- MOCK 渠道只能确认 `channel=MOCK` 的订单，真实订单不受影响；生产禁用。

## 9. 本地联调

`bash scripts/run-dev.sh` 会一并启动 oc-pay（8099）、生成 `OC_PAY_INTERNAL_TOKEN` 并默认开启 `OC_PAY_MOCK_ENABLED=true`。

1. `POST /api/v1/orders {planCode:"PRO", channel:"mock"}`（带 `Idempotency-Key`）→ 返回 `codeUrl` 指向 `http://127.0.0.1:8099/pay/mock/{orderNo}/pay`。
2. 浏览器打开该链接即视为支付成功 → `oc-pay` 置 `PAID` 并回调 `oc-app` 发放 PRO。
3. `GET /api/v1/orders/{orderNo}` 轮询状态；`GET /api/v1/quota` 确认套餐与额度已生效。

套餐页「升级到专业版」已接入该流程：选择支付方式 → 展示二维码（本地为模拟渠道，附"打开模拟支付页"）→ 轮询至 `PAID` → 自动刷新额度并提示开通成功。

## 10. 验收要点

- 单测：密钥 PEM 往返、微信/支付宝签名验签、AES-GCM 解密、微信回调解密、支付宝回调验签、订单创建幂等、退款超额拒绝（`mvn -pl oc-pay/oc-pay-service -am test`）。
- 迁移：`payment_*` 表由 `oc-app` Flyway 建立，导入/联调环境可重复执行。
- 生产：真实渠道配置后，扫码支付 → 回调 → 额度到账闭环；退款按剩余金额限制。

## 11. 待办

- 微信 JSAPI / H5、支付宝电脑网站跳转支付（当前为扫码）可后续按需扩展。
- 退款为同步受理，异步退款结果（渠道终态）暂以受理状态为准，可后续接退款回调细化为终态。
