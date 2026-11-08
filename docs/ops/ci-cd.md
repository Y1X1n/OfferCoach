# CI/CD 与发布

> 状态：生效 ｜ 版本：v1.1 ｜ 更新：2026-11-07
> 本文只讲**怎么构建、怎么打 tag、怎么发布与回滚**；环境、端口、Compose、K8s 与 Nginx 见 [deployment.md](deployment.md)；监控告警与故障处置见 [observability.md](observability.md)。

## 1. 一页结论

1. **触发一律是打 git tag**（`v1.2.0` 或 `1.2.0`，CI 自动剥 `v`）；push 到 `main` 不触发生产发布。
2. **镜像 tag = 版本号**，同时更新 `:latest` 仅用于开发；生产部署必须使用明确版本号，禁止 `latest`。
3. **构建在 CI runner，服务器只 pull**；禁止在服务器上编译（避免 OOM 与环境污染）。
4. **数据库迁移与应用发布解耦**：Flyway 由独立 Job 执行，遵循 expand → migrate → contract。
5. **生产发布必须灰度**：10% 流量 30 分钟，指标门禁通过后全量；任一门禁触发自动回滚。

## 2. 流水线

```text
[PR]
  lint + typecheck + unit
  → 构建镜像（不推送）
  → Testcontainers 集成测试
  → OpenAPI 破坏性检查（oasdiff）
  → 安全扫描（依赖/镜像/密钥）
  → AI 评测冒烟（黄金集 20 条）

[main 合并]
  → 推送镜像（git sha tag，仅开发/预发）
  → 部署 staging
  → E2E 关键路径（15 条）
  → 压测冒烟（50 VU × 2min）
  → 人工审批

[tag 发布]
  → 构建并推送版本镜像（version + latest）
  → 部署 staging（版本镜像）
  → 生产金丝雀 10%（30min 指标门禁）
  → 全量
  → 部署后验证（健康、TTFT、错误率、报告成功率）
```

### 2.1 阶段职责

| 阶段 | 做什么 | 失败处理 |
|---|---|---|
| PR | 静态检查、单测、契约、镜像构建 | 阻止合并 |
| main | 集成、E2E、压测、staging 部署 | 阻止进入发布候选 |
| tag | 版本镜像、staging 验证、生产灰度 | 自动回滚 + 告警 |
| 发布后 | 健康检查、关键路径冒烟、指标观察 24h | 回滚或降级 |

### 2.2 构建产物

| 组件 | 产物 | 命名 |
|---|---|---|
| 前端 | Nginx 静态镜像 | `offercoach-web:<version>` |
| Java 服务 | JRE 镜像（单 jar） | `offercoach-<component>:<version>` |
| AI 服务 | Python 镜像（uv 依赖） | `offercoach-ai:<version>` |
| Worker | JRE 镜像 | `offercoach-report-worker:<version>` |
| 迁移 Job | 同 Java 镜像，执行 Flyway | 复用 `offercoach-app:<version>` |

- 组件码：`gateway`、`identity`、`interview`、`admin`、`worker`、`ai`、`web`。
- 版本号 `major.minor.patch[-stage]`，不带 `v`；镜像 tag 与 git tag 一致。
- 构建产物必须带 SBOM（CycloneDX）与镜像签名（cosign，V1.1）。

## 3. 数据库迁移

- Flyway 在应用启动前由**独立 Job** 执行；多副本时用分布式锁保证只跑一次。
- 遵循 **expand → migrate → contract**：先加列/表，再双写/回填，最后删旧字段。
- 禁止在同一发布中执行“删列/改类型/重命名”等破坏性操作。
- 迁移前自动快照；staging 必须先在脱敏副本上演练。
- 回滚不自动回滚数据库；应用需兼容上一版本 Schema（至少一个发布周期）。
- 迁移脚本按 `V{yyyyMMddHHmm}__{description}.sql` 命名；禁止修改已执行脚本。

**发布顺序**：

```text
备份 → 迁移 Job（expand）→ 部署新版本（双写/兼容）→ 回填 → 观察
  → 下一发布：停止双写 → 删旧字段（contract）
```

## 4. 金丝雀门禁

| 指标 | 阈值（对比基线） |
|---|---|
| HTTP 5xx | < 0.5%，且不高于基线 2 倍 |
| SSE 首字 P95 | 回退 ≤ 15% |
| 评分失败率 | < 1% |
| 报告成功率 | ≥ 99% |
| 单场成本 | 上涨 ≤ 20% |
| 前端 JS 错误率 | 不高于基线 1.5 倍 |

- 观察窗口 30 分钟；样本不足时延长到 60 分钟。
- 任一项触发 → 自动回滚并告警；回滚后保留现场（日志/Trace/镜像 tag）。
- 回滚只回应用镜像，不回数据库；数据库兼容性由 expand-contract 保证。

## 5. 发布检查清单

- [ ] CI 全绿；镜像扫描无 High/Critical。
- [ ] Flyway 迁移已在 staging 演练，兼容上一版本。
- [ ] 配置/密钥已更新，未提交仓库。
- [ ] 灰度 10% 指标正常 30 分钟。
- [ ] 关键路径冒烟：登录 → 上传 → 面试 → 评分 → 报告。
- [ ] SSE 首字 P95 达标；错误率正常。
- [ ] 回滚镜像与开关已确认。
- [ ] 值班人、告警群、变更记录已同步。
- [ ] CHANGELOG 与相关文档已更新。

## 6. 回滚

| 场景 | 动作 |
|---|---|
| 应用错误率高 | 回滚到上一版本镜像；保留当前日志与 Trace |
| 首字/性能回退 | 回滚镜像；检查 Prompt/模型/索引变更 |
| 迁移导致异常 | 停止发布；按 expand 阶段保留旧字段；必要时用快照恢复 |
| AI 供应商故障 | 不回滚应用；切备用供应商或模板降级 |
| 数据错误 | 停止写入；按审计/流水修复；禁止直接改库不记录 |

- 回滚后 24h 内完成复盘：触发指标、根因、修复、预防。
- 禁止用 `latest` 回滚；必须指定版本 tag。

## 7. 凭据与权限

- CI 凭据只存在 runner 的 Secret/凭据插件：镜像仓库、K8s kubeconfig、部署 Token。
- 生产部署使用最小权限 ServiceAccount；禁止 cluster-admin。
- 镜像仓库开启不可变 tag（release 仓），防止覆盖。
- 所有生产变更记录操作者、版本、时间、审批人。

## 8. 常见失败

| 现象 | 排查 |
|---|---|
| 构建成功但镜像启动失败 | 看启动日志、健康检查、环境变量、JVM 内存 |
| 迁移 Job 卡住 | 查 Flyway 锁、长事务、连接数 |
| 灰度指标正常但全量出错 | 检查流量分布、缓存、特征开关、数据倾斜 |
| E2E 偶发失败 | 查 AI 回放、测试数据隔离、异步等待、重试是否掩盖 flaky |
| 回滚后仍异常 | 检查数据库兼容、配置中心、缓存未失效 |