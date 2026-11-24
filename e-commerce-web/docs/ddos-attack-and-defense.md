# DDoS 攻击与防范说明

> 最后更新：2026-09-22
> 本文结合本项目的实际架构（Express + sql.js + nginx 反代 + pm2 单实例）写成，
> 文中行号引用均对应当前代码，改动后请一并更新。

## 一、什么是 DDoS

**DDoS：用大量分散的机器，同时往你的服务器发请求，把它堵死，让正常用户进不来。**

把这句话拆开，有三个要素：

| 要素 | 含义 | 为什么关键 |
|---|---|---|
| 大量分散的机器 | 请求来自成千上万个不同 IP | 所以「封 IP」这个常规手段失效 —— 封不完 |
| 同时发请求 | 短时间内的流量/请求量远超服务承载能力 | 攻击的是「承载能力」，不是代码漏洞 |
| 把服务堵死 | 耗尽带宽、连接数、CPU、内存、磁盘 IO 中的任意一项 | 打穿短板即可，不需要全部打穿 |

### 几个容易误解的点

- **「堵死」不等于彻底宕机。** 服务慢到没人能用、错误率飙到正常用户放弃，同样算攻击成功。
  对业务来说，「打不开」和「要等 30 秒才打开」的损失是一样的。
- **DDoS 和 DoS 的区别只在「分散」两个字。** 单机打你叫 DoS，封掉那个 IP 就结束了；
  几万台机器打你叫 DDoS，封不完，只能靠限流和云清洗。
- **目标不是偷数据，是让你不可用。** 它不利用业务漏洞，所以「代码写得对」并不天然免疫。

### 按层次分类

| 层次 | 典型手法 | 打的是什么 | 能不能自己防 |
|---|---|---|---|
| L3/4 网络层、传输层 | SYN Flood、UDP 反射放大（DNS/NTP/memcached）、ACK Flood | 带宽、连接表 | ❌ 只能靠云厂商清洗 |
| L7 应用层（CC 攻击） | 高频打昂贵接口、慢速攻击（Slowloris/RUDY） | CPU、内存、数据库、磁盘 | ✅ 靠自己限流 |
| 业务型 | 刷验证码、刷优惠券、批量注册 | 钱包和业务数据 | ✅ 靠限流 + 风控 |

---

## 二、本项目的攻击面

### ✅ 已经做对的部分

- **后端不直接暴露公网**：`server/index.js:158` 只监听 `127.0.0.1`，外部连不上 3000 端口，
  即使云安全组漏放也打不进来。L3/4 攻击基本无门。
- **模板店写保护**：`server/middleware/templateGuard.js` 挡住了污染数据源的路。
- **上传后缀白名单**：`server/middleware/upload.js` 的 `fileFilter` 不含 `.svg`/`.html`，堵住了存储型 XSS。
- **单实例 PID 锁**：`server/util/singleInstance.js` 保证不会多进程写坏库。
- **已经有访问日志**：`server/middleware/accessLog.js` 记了 IP、状态码、耗时，是后续做告警的基础。

### 🔴 风险 1：写放大 —— 一次写请求 = 写一整个数据库文件

`server/sqlite/connection.js:34-38`：

```js
function saveDatabase() {
  fs.writeFileSync(dbPath, buffer);   // 整库序列化 + 覆盖写
}
```

sql.js 是「整库读进内存 + 整文件覆盖回写」。按 `sandbox-and-launch.md` 里的估算，
**2000 个沙箱存活时库文件约 110MB** —— 任何一个写接口被调用一次，就要**同步写 110MB 磁盘**，
而 `writeFileSync` 是阻塞的，写盘期间 Node 事件循环完全停摆。

攻击成本极低：`POST /api/shops/sandbox` 是**公开接口**（`server/routes/shopRoutes.js:12`），
任何人都能零成本拿到一个合法 token，之后随便调个写接口（加购物车、改地址）就能触发一次全库回写。

**这是本项目最致命的一点，也是 L7 攻击的最佳靶心。**

### 🔴 风险 2：开店接口公开且极重，限流可被伪造头绕过

`server/controllers/shopController.js:13` 的开店限流按 `req.ip` 计数，但 `req.ip` 来自
`server/index.js:48` 的 `app.set('trust proxy', true)`。

`trust proxy = true` 的语义是**信任整条 X-Forwarded-For 链**，Express 会取链中**最左**的值作为
`req.ip` —— 那一段恰恰是客户端可以随便写的。所以只要请求头带一个 `X-Forwarded-For: 1.2.3.4`，
这个每 IP 200 次的限流就被完全绕过。

⚠️ **需要确认线上的 nginx 写法**（仓库里没有 nginx 配置）。如果用的是 `$proxy_add_x_forwarded_for`
（追加），伪造头会原样透传到后端。正确做法见第三节。

### 🟠 风险 3：上传接口没有大小上限

`server/middleware/upload.js:56`：

```js
const upload = multer({ storage: storage, fileFilter: fileFilter });
```

`fileFilter` 只校验后缀白名单，**没有配 `limits`**。结合「匿名可拿 token」，
攻击者可以并发上传 GB 级 `.mp4` **把磁盘写满**，写满后 `writeFileSync` 抛异常，服务直接不可用。

### 🟠 风险 4：登录接口无限流

`server/sqlite/userDB.js:36` 是 `SELECT * FROM users WHERE username = ? AND password = ?`（明文比对），
`authController.login` 没有任何失败计数或限流。接口本身是可被 CC 打 CPU 的点。

### 🟡 风险 5：日志同步写、无轮转

`server/middleware/accessLog.js:15` 每个请求都 `console.log`。process.stdout 在重定向到文件时是
**同步阻塞**的，pm2 日志还会无限增长 —— 高 QPS 下既是性能瓶颈，也是磁盘炸弹。

### 🟡 风险 6：单实例是不可改的硬约束

`server/ecosystem.config.js` 的 `instances: 1` 是「整库覆盖写」架构的必然要求，**不能改成 cluster**。
这意味着**扛不住就是扛不住，加机器没用** —— 所有防护必须在请求进到 Node 进程**之前**完成。
这条约束决定了下面的方案为什么把重心放在 nginx 上。

---

## 三、防范方案

### 第 0 层：边缘层（零代码改动，效果最大，优先做）

在 nginx 上做限流，**这是唯一能真正挡住 DDoS 的一层**：

```nginx
# 定义限流区：按 IP 计数，10MB 内存约存 16 万个 IP
limit_req_zone  $binary_remote_addr zone=api:10m   rate=20r/s;   # 普通接口
limit_req_zone  $binary_remote_addr zone=heavy:10m rate=6r/m;    # 重接口（开店/上传/登录）
limit_conn_zone $binary_remote_addr zone=conn:10m;

server {
    limit_conn conn 30;                  # 单 IP 最多 30 个并发连接（防 Slowloris）
    client_max_body_size 20m;            # 挡住超大 body
    client_body_timeout  15s;
    client_header_timeout 10s;

    location /api/ {
        limit_req zone=api burst=40 nodelay;
        limit_req_status 429;
        # ⚠️ 必须用 $remote_addr 覆盖，不能用 $proxy_add_x_forwarded_for ——
        #    后者是追加，客户端伪造的 XFF 会原样透传到后端，限流全部失效
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_pass http://127.0.0.1:3000;
    }

    # 重接口单独收紧
    location ~ ^/api/(shops/sandbox|login|upload)$ {
        limit_req zone=heavy burst=3 nodelay;
        limit_req_status 429;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_pass http://127.0.0.1:3000;
    }
}
```

再配合云厂商的 WAF / DDoS 高防（腾讯云有基础版免费额度），L3/4 交给它。

同时把后端 `server/index.js:48` 从 `app.set('trust proxy', true)` 改成
`app.set('trust proxy', 1)` —— 明确只信任一层反代，比信任整条链安全得多。

### 第 1 层：应用层限流（代码级兜底）

nginx 被绕过时（比如同机房内网调用）的最后一道。建议：

- 在 `server/middleware/` 下新增通用限流中间件，挂载在 `resolveShop` **之前**
  （参考 `server/index.js` 中 `accessLog` 的位置）；
- 按「IP + 路径」分桶：普通 GET 宽松（60 次/分钟），写接口收紧（20 次/分钟），
  `/api/shops/sandbox`、`/api/login`、`/api/upload` 单独最严；
- **不要把 `shopController.js:13` 那份内存 Map 复制一遍** —— 它重启清零、无法跨接口复用，
  建议抽成可复用的 `rateLimit(options)` 工厂，让开店接口也换成它。

登录接口额外加**失败计数 + 指数退避**（同账号/同 IP 连续失败 5 次锁 15 分钟），这是防撞库的标准做法。

> 注：内存限流重启即清零，且多实例不共享。本项目是单实例，所以够用；
> 如果哪天架构变了，要把计数挪到 Redis。

### 第 2 层：资源层加固

| 项目 | 现状 | 改法 |
|---|---|---|
| 上传大小 | 无限制（`upload.js:56`） | 加 `limits: { fileSize: 5 * 1024 * 1024, files: 1 }`，并在全局错误处理里把 `LIMIT_FILE_SIZE` 转成 413 |
| 写放大 | 每次写都全库回写（`connection.js:34`） | 给 `saveDatabase` 加**写合并**：短窗口内（如 200ms）的多次调用只落盘一次。改动最小、收益最大的一处 |
| 日志 | 同步 `console.log` + 无轮转 | 用 pm2 的 `pm2-logrotate`，或改成异步批量写；保证 429/5xx 能查到即可 |
| JSON body | Express 默认 100kb | 已够用，但建议显式写成 `express.json({ limit: '100kb' })`，避免以后被改大 |
| 模板店只读 | 已有 `templateGuard` ✅ | —— |

### 第 3 层：监控告警（发现比拦截更重要）

`accessLog` 已经记了 IP、状态码、耗时，这是很好的基础。建议：

- 对日志做聚合告警：**单 IP 每分钟请求数**、**429/5xx 占比突增**、**`saveDatabase` 单次耗时**超阈值；
- 暴露一个内部接口，输出「当前沙箱数、内存占用、库文件大小」，便于判断是谁在打、打到什么程度；
- `sandbox-and-launch.md` 里提到的「回收间隔 1 小时」，被攻击时可临时调短，加快释放名额。

---

## 四、优先级

| 优先级 | 动作 | 成本 | 效果 |
|---|---|---|---|
| **P0** | nginx 加 `limit_req` / `limit_conn`，XFF 用 `$remote_addr` 覆盖 | 改配置，10 分钟 | 挡住绝大部分 L7 攻击 |
| **P0** | `trust proxy` 从 `true` 改成 `1`（`server/index.js:48`） | 一行代码 | 堵住伪造 IP 绕过限流 |
| **P1** | multer 加 `fileSize` + 上传/登录/开店接口独立限流 | 半天 | 堵住磁盘打满和爆破 |
| **P1** | `saveDatabase` 加写合并节流 | 半天 | 削掉最大的一处写放大 |
| **P2** | 日志轮转 + 限流告警 | 半天 | 能看见、能响应 |
| **P3** | 密码哈希（bcrypt） | 需处理存量数据，谨慎 | 属安全加固，非 DDoS 范畴 |

---

## 五、一句话总结

本项目**不用担心 L3/4**（后端只监听 127.0.0.1），**要防的是 L7**：
而 L7 的靶心是「公开的开店接口 + 每次写都全库回写」这对组合。
**先用 nginx 把流量挡在 Node 进程之外，再削掉写放大，最后补上监控** —— 顺序不能反。
