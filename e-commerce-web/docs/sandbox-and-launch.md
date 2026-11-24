# 沙箱与上线手册（已废弃）

> **2026-10-29：多租户沙箱已彻底下线，本文档仅作历史存档，请勿按文中步骤操作。**
> 当前为单店模式：后台店铺信息由前端静态配置 `src/utils/shop.js` 提供，后端无 `/api/admin/shops/**` 接口。


> 最后更新：2026-09-15
> 上线前的检查项都带 `【上线前】` 标记，用下面这条命令一次找全（排除 docs 是因为本文件自己含这个标记）：
> `grep -rn "【上线前" . --exclude-dir=node_modules --exclude-dir=dist --exclude-dir=docs`

## 一、沙箱是怎么运作的

| | |
|---|---|
| 隔离单位 | 一个访客 = 一个独立**店铺**，`shop_id` 是隔离键 |
| 开店时机 | 访客在登录页点「一键体验」→ 后端**当场**克隆模板店的全量数据 → 签发该店的管理员 token |
| 隔离依据 | **只认 JWT 里服务端签的 `shopId`**，永不读 query / body / header |
| 有效期 | 24 小时，过期后由服务进程内的定时任务**物理删除**（业务行 + 磁盘上传图片一起删） |
| 模板店 | `shop_id = 1`「杰米小店」，永不过期，是**所有沙箱的数据源**，只有本地开发环境能写 |
| 容量 | 同一 IP 每小时 200 个；全站同时存活 2000 个（单个沙箱净占约 56KB，全满约 110MB） |

**数据流是单向的**：模板店 →（开店那一刻克隆）→ 沙箱。沙箱里的任何东西都不会回流到模板店。

沙箱店名格式是 `杰米小店（体验 #N）`，访客在顶栏能直接看到自己进的是哪个店。

## 二、日常操作

### 1. 本地开发（在模板店）

```bash
cd server && node index.js     # .env 里 ALLOW_TEMPLATE_WRITE=1 已常开
```

用 `admin` 账号登录（密码见 `server/API.md`），进去就是改造前那一套，随便增删改。

- **改的模板数据只对之后新开的沙箱生效**，已经开着的沙箱看不到（正常现象）
- 在模板店上传的图片会记进 `uploads` 表（模板店不回收，无害）
- 改 `.env` 或 `server/` 下任何文件都要**重启服务**

### 2. 访客体验（不用管）

点「一键体验」→ 得到自己的独立店铺 → 全权限 → 24 小时后自动消失。每次点击都开新店，不复用。

### 3. 验证隔离是否生效

开两个沙箱 A、B，用 A 的 token 带 B 的 id 打接口，应该全部返回 404 或空集。重点验：商品详情/改删、订单列表与详情、售后列表（含收货人隐私）、库存、分类、标签、购物车、地址、经营看板，以及 `DELETE /api/orders/batch-delete` 里塞 B 的 id。

### 4. 清沙箱（**发版前必做**）

把所有沙箱的 `expires_at` 改成过去时间 → **重启服务**（启动时会扫一次并回收）。

回收动作 = 删该店在 15 张表里的所有行 + 删它在磁盘上的上传图片 + 删 `shops` 行 + 删 `uploads` 行。

为什么发版前必须清：

- 访客可能填了真实的收货人姓名 / 电话 / 地址，**提交进 git 就等于公开**
- 清完 `git status` 里剩下的未跟踪文件全是自己的素材，一眼能判断要不要提交

### 5. 回收任务依赖进程常驻

启动扫一次 + 每小时一次，**跑在服务进程内**。进程停了回收也停，过期沙箱会一直堆着占磁盘 —— 这就是 supervisor 要开 `autorestart=true` 的原因。

### 6. 唯一需要手动维护的地方

**以后新增业务表，必须登记进 `server/sqlite/shopTables.js` 的 `SHOP_TABLES`。**

漏登记的后果：新沙箱里那张表是空的（不会被克隆），沙箱回收时那张表的数据也清不掉（**永久残留**）。

## 三、上线注意事项（按执行顺序）

### A. 本机打包前

1. `grep -rn "【上线前" . --exclude-dir=node_modules --exclude-dir=dist --exclude-dir=docs`，逐项处理
2. `VITE_API_BASE_URL` 改成空值（同源相对路径，由 Nginx 反代 `/api`），**然后重新 `npm run build`**（`VITE_` 变量是构建时打进产物的，不重新构建不生效）
3. 登录页「登录」按钮已用 `v-if="isDev"` 控制，生产环境自动隐藏、只留「一键体验」，无需手动删
4. **清沙箱**（见 二.4）
5. 提交

### B. 上传

6. ⚠️ **`.env` 不要传上去**。腾讯云单独放一份，只含 `JWT_SECRET=<新随机串>` 和 `PORT`，**绝不能带 `ALLOW_TEMPLATE_WRITE`**
7. `node_modules` 不用传，云上 `npm i` 装
8. 要传的：`server/`（含 `sqlite/database.sqlite`，模板数据全靠它）、`dist`、`public/image`

### C. 腾讯云

9. **`JWT_SECRET` 必须是新生成的随机串**，不要沿用本地那份。缺失会直接拒绝启动（刻意的设计）
10. 用 supervisor 托管后端（本机不用 pm2）：`/etc/supervisor/conf.d/stepin-api.conf` 里 `directory=<项目>/server`、`command=node index.js`、`autostart=true`、`autorestart=true`，配好后 `supervisorctl reread && supervisorctl update` 拉起
11. **只能单实例**。已有代码层兜底：`util/singleInstance.js` 的 PID 锁会让第二个实例拒绝启动
12. 安全组放行端口；确认 `dist` 由谁托管（`index.js` 只托管了 `/image/**`，没托管 dist，通常是 nginx）
13. **启动日志瞄一眼**：出现 `⚠️ ALLOW_TEMPLATE_WRITE=1` 就说明写保护在线上被关掉了，立刻处理

### D. 上线后自测

14. 首页点「一键体验」→ 商品 / 订单 / 售后 / 库存**每个页面都有数据**（证明克隆完整）
15. 在沙箱里删商品、删订单 → 重新体验 → 数据完好（证明隔离生效）

## 四、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 401「登录已失效，请重新登录」 | token 无效，或店铺已被回收 | 重新登录 / 重新体验 |
| 401「体验店铺已过期，请重新体验」 | 沙箱过期（回收任务删除该店之前的窗口里会看到这条） | 重新体验 |
| 429「体验店铺开得太频繁了」 | 同一 IP 一小时内超过 200 个 | 隔一小时，或调 `SANDBOX_PER_IP_LIMIT` |
| 429「体验名额已满」 | 全站同时存活超过 2000 个 | 调 `SANDBOX_ACTIVE_LIMIT`，或清掉一批过期沙箱 |
| 403「模板店是演示数据源，已禁止写入」 | 模板店写保护生效 | 本地维护素材时确认 `.env` 里有 `ALLOW_TEMPLATE_WRITE=1` 并已重启 |
| 启动失败「缺少环境变量 JWT_SECRET」 | `.env` 没配或路径不对 | 补上 `JWT_SECRET` |
| 启动失败「检测到另一个服务实例正在运行」 | 有旧进程没停，或强杀留下的僵尸锁 | 停掉旧进程；确认它真的不在了，删掉 `server/sqlite/database.lock` |
| 前端请求全 404 / 跨域报错 | 打包时 `VITE_API_BASE_URL` 没改，或 nginx 没反代 `/api` | 改地址后重新 `npm run build` |

## 五、还没做、待决定的事项

| 事项 | 现状 | 影响 |
|---|---|---|
| `admin` 密码 | 仍是弱口令，**明文入库**，还写在 `swagger.yml`、`API.md`、登录页 placeholder 里 | 藏了按钮 ≠ 关了门，知道密码的人直接打 `POST /api/login` 仍能登进模板店（只读） |
| 后端登录接口 | 没关 | 要真关门得加开关让 `/api/login` 返回 403 |
| 生产环境闸门 | 没加 | 加了之后，`.env` 误传上云时服务会直接起不来，而不是带着破防的配置跑 |
| 回收间隔 | 1 小时 | 可改成 10 分钟，过期名额释放快 6 倍 |
| 名额满时的行为 | 直接 429 拒绝新访客 | 可改成淘汰最老的沙箱，对真实访客更友好 |
| 顺手可清的隐患 | `GET /swagger.yml` 无鉴权明文下发接口文档、`server/models/User.js` 死代码、`sqlite/_db_head_tmp.sqlite` | 与本功能无关，仅记录 |

## 六、关键文件速查

| 文件 | 作用 |
|---|---|
| `server/sqlite/shopTables.js` | 店铺数据的单一事实来源 + 沙箱 TTL / 限额常量 |
| `server/sqlite/shopDB.js` | 开店克隆、回收、清理 |
| `server/middleware/shop.js` | 解析 `req.shopId`（隔离的信任根） |
| `server/middleware/templateGuard.js` | 模板店写保护 |
| `server/util/jwt.js` | 密钥与签发（缺失即拒绝启动） |
| `server/util/singleInstance.js` | 单实例 PID 锁 |
| `server/ecosystem.config.js` | pm2 进程配置（本项目用 supervisor 托管，此文件是 pm2 专用、可忽略） |
| `server/swagger.yml` | 接口文档（含沙箱与店铺接口） |
