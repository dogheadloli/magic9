# 神奇九转批量选股监控系统

基于「神奇九转 + 组合过滤」批量监控自选股，输出低9抄底 / 高9逃顶预警。
完整技术方案见 [`技术方案-神奇九转选股监控系统.md`](技术方案-神奇九转选股监控系统.md)。

## 环境

- JDK 8（本机：Temurin 1.8.0_492）
- Maven 3.9+
- MySQL（`localhost:3306`，库 `stock` 启动时自动创建）
- （可选）Docker / Docker Compose，用于容器化部署

> 因本机仅有 JDK 8，采用 Spring Boot 2.7.18（最后一个支持 Java 8 的版本）。
> 后续若升级到 JDK 17，可平滑切换到 Spring Boot 3.x。

敏感配置（数据库密码、邮件授权码、企微 Key、DeepSeek Key）通过环境变量注入，见 `.env.example`，**不要写入仓库**。

## 运行（本地）

```bash
# Windows PowerShell（已配置 JAVA_HOME / MAVEN_HOME）
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;" + $env:Path
# 按需设置密钥，例如：$env:DB_PASS="..."; $env:MAIL_PASS="..."; $env:AI_ENABLED="true"; $env:DEEPSEEK_API_KEY="..."
mvn -DskipTests spring-boot:run
```

服务端口：`8964`（可用环境变量 `SERVER_PORT` 覆盖）。

启动后打开 Web 控制台：**http://localhost:8964**

### 本地 Docker（可选）

```bash
cp .env.example .env   # 填写 DB_PASS 等；本地连本机 MySQL 保持 host.docker.internal
docker build -t stock-monitor:local .
# 临时改 .env：DOCKER_IMAGE=stock-monitor  IMAGE_TAG=local
docker compose up -d
```

## CI/CD：GitLab + Jenkins + Docker（无 K8s）

```
git push → GitLab
  → Jenkins：docker build（含 mvn）→ push 镜像仓库
  → SSH 部署机：docker compose pull && up -d
```

| 文件 | 作用 |
|------|------|
| `Dockerfile` | 多阶段构建 jar + 运行镜像 |
| `docker-compose.yml` | 服务器跑应用（MySQL 仍用宿主机） |
| `Jenkinsfile` | 构建 / 推送 / 远程部署 |
| `.env.example` | 环境变量模板 |
| `deploy/README.md` | 服务器初始化与 Jenkins 凭据说明 |

**首次**：按 [`deploy/README.md`](deploy/README.md) 在服务器准备 `/opt/stock`（`docker-compose.yml` + `.env`），配置 Jenkins 凭据与 `DOCKER_IMAGE` / `DEPLOY_*`，GitLab Webhook 指向 Jenkins。

**日常**：合并到 `main`/`master` 后由 Jenkins 自动发布，无需登录服务器。看运行日志可用 Portainer，或临时 `docker compose logs -f app`。

## 数据源

- **主数据源：腾讯财经 `ifzq.gtimg.cn`**（`@Primary TencentProvider`，日K前复权 + 实时行情，本机网络可达）。
- 备选：东方财富 `EastMoneyProvider`（本机网络下 K 线域名 `push2his` 不可达，保留作为可切换适配器）。
- 适配层接口 `MarketDataProvider`，可无痛扩展 Tushare / 付费源。

## 已实现接口

### M1 — 数据采集

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pool` | 自选股列表 |
| POST | `/api/pool` | 新增自选股 `{code, name?, group?}`，名称留空自动补全 |
| DELETE | `/api/pool/{id}` | 删除自选股 |
| PUT | `/api/pool/{id}/enabled?enabled=true` | 启用/停用监控 |
| POST | `/api/kline/backfill?code=600519` | 回补日K前复权并落库（默认 250 根） |
| GET | `/api/kline?code=600519` | 查询已落库日K |
| GET | `/api/quote?code=600519` | 实时行情快照 |

### M2 — 指标与信号

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/indicator?code=600519&limit=60` | 指标序列（TD9/MACD/MA/量能均线/BIAS），供绘图与调试 |
| GET | `/api/signal/evaluate?code=600519&asOf=2026-03-02` | 评估最新信号；`asOf` 可选，做历史评估 |
| POST | `/api/signal/scan` | 基于已落库日K扫描启用自选股，命中落库（同日同向去重） |

### M3 — 盘中实时扫描与预警

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/signal/scan-realtime` | 盘中实时扫描：拼接当日快照→评估→命中落库+推送 |
| POST | `/api/alert/test?type=BUY_LOW9` | 发送样例预警，自测通知渠道是否连通 |

**调度（`@EnableScheduling`）**

- 盘中扫描：`schedule.intraday-interval-ms`（默认 5 分钟），仅交易日交易时段执行（`TradeCalendar` 校验，周末/节假日/非时段自动跳过；`force-scan: true` 可调试强扫）。
- 盘前回补：`schedule.prefetch-history-cron`（默认 09:15）回补历史日K。
- 收盘定稿：`schedule.eod-finalize-cron`（默认 15:05）覆盖落库当日定稿K线。

**实时K线拼接**：历史日K + 实时快照构造"当日未收盘K线"，在内存中参与指标计算（不污染落库历史）；并发线程池 + Guava 限流 + 失败重试。

**预警渠道（可插拔 `AlertChannel`）**

- `WeComAlertChannel` 企业微信群机器人（markdown）：需配置 `alert.wecom-webhook-key`（环境变量 `WECOM_KEY`），未配置则自动跳过。
- `ConsoleAlertChannel` 控制台/日志：始终可用。
- 去重：同标的同日同向只推一次（`signal_record` 唯一键）；跨日冷却 `alert.cooldown-days`（默认 1）。

> 配置企业微信：设置环境变量 `WECOM_KEY=你的机器人key` 后重启即可推送。

### M4 — Web 控制台

访问 **http://localhost:8964**（前端为单页原生 JS + klinecharts，已本地化于 `static/vendor/`，Spring Boot 托管，无需单独前端服务）：

- **自选股管理**（左栏）：添加（自动补全名称并回补日K）、启用/停用、删除；点击代码/名称查看其K线。
- **K线图**（中栏）：日K蜡烛图 + MA20/MA60 + 成交量 + MACD 副图；叠加标注 **低9/高9**（神奇九转）与 **抄底/逃顶信号**（★为强信号）。
- **信号列表**（底部）：按日期/类型/代码筛选；点击行跳转该股K线；顶部「实时扫描」一键触发盘中扫描。
- **自动刷新**：仅交易日交易时段每 60 秒自动刷新（轮询 `/api/market/status` 判断），刷新当前K线（含当日实时未收盘K线，用 `updateData` 仅更新最新一根、保留缩放）与信号列表；非交易时段暂停，顶部状态栏提示「交易中·自动刷新 / 已暂停」。

> K线盘中实时数据来自 `/api/indicator/realtime`（历史落库日K + 当日快照拼接）；`/api/indicator` 仅返回已落库日K（当日K线收盘 15:05 定稿后入库）。

### 隐蔽行情看板（摸鱼模式）

访问 **http://localhost:8964/quiet.html**：伪装成普通行情列表的极简页面。

- 表格列：名称 / 代码 / 当前 / 涨跌 / 涨跌幅，**纯色无涨跌红绿**（低调）。
- 底部状态栏：左「最后刷新 HH:mm:ss」，右「最后信号 MM-DD HH:mm 类型（代码）」。
- 每 60 秒自动刷新（`/api/quotes` 批量行情 + `/api/signals` 取最近一条信号）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/quiet.html` | 隐蔽行情看板 |
| GET | `/api/quotes` | 启用自选股的批量实时行情 |

### 深度伪装：IDE / AI 编程工具（摸鱼 Pro）

访问 **http://localhost:8964/ide.html**：整页伪装成 VS Code / Cursor 风格的代码编辑器。

- 标题栏 + 文件树 + 代码标签页 + 行号 + Python 语法高亮 + 底部终端 + 右侧 AI 对话 + 蓝色状态栏，乍看就是在写代码。
- **自选股行情** → 渲染成"自动生成的代码数据" `SNAPSHOT = { "600519": 1206.70, # 贵州茅台 -0.08% ... }`。
- **最后信号（时间+类型）** → 伪装成终端里的编译器诊断 `analyzer.py:88: note: LOW9 matched 600519 (score 2/3) at 06-25 09:45`，同时右侧 AI 助手也会"提到"它。
- **最后刷新时间** → 右下角状态栏时钟 `⟳ HH:mm:ss`（每 60 秒刷新）。
- 同样复用 `/api/quotes` 与 `/api/signals`，每 60 秒自动刷新。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | Web 控制台首页 |
| GET | `/api/signals?code=&date=&type=` | 信号记录查询 |

**指标 / 信号说明**

- 神奇九转 TD 计数（`Td9Calculator`）：低9 / 高9。
- MACD（`MacdCalculator`，12/26/9）、MA5/10/20/60、量能均线、BIAS20/BIAS60。
- 背离检测（`DivergenceDetector`）：底背离 / 顶背离。
- 信号引擎（`SignalEngine`，加权打分）：
  - **低9抄底** = 低9(必备) + MACD底背离 + 持续缩量 + 回踩均线支撑
  - **高9逃顶** = 高9(必备) + MACD顶背离 + 放量滞涨 + 股价远离均线
  - 除必备项外，命中加分项 `>= strategy.score-threshold`(默认2) 即预警，满 3 标"强信号"。
- 策略参数见 `application.yml` 的 `strategy.*`，可调。

> 注意：通过 PowerShell `Invoke-RestMethod` 发送/接收含中文的请求时需显式 UTF-8；用 `curl.exe` 更直观。服务端存储与返回均为 UTF-8。

## 数据库表（JPA 自动建表）

- `stock_pool`：自选股池
- `kline_daily`：日K线（前复权，唯一键 code+trade_date）
- `signal_record`：信号记录（唯一键 code+trade_date+signal_type）

## 里程碑

- [x] M1：数据采集（自选股池 + 东方财富/腾讯适配器 + 日K落库）
- [x] M2：指标与信号引擎（TD9 / MACD / MA20·MA60 / 量能 / BIAS / 背离 + 加权打分）
- [x] M3：盘中每 5 分钟实时扫描 + 实时K线拼接 + 企业微信预警
- [x] M4：Web 页面（自选股管理 + K线图含信号标注 + 信号列表）
