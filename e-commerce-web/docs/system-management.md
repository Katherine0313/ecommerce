# 系统管理模块：数据库管理 / 接口管理 实施方案（参考 CRMEB）

> 结论先说：CRMEB 这两个模块值得借鉴的是**信息结构与交互**，不是它的实现代码。
> 我们是 Vue3 + Ant Design Vue + Spring Boot 微服务 + 单库 MySQL，直接照搬 PHP 的"拼接 SQL / 扫描路由文件"会踩坑。
> 本文给出两套可落地的前后端方案：数据库管理以 JDBC + information_schema 实现，接口管理以网关聚合的 OpenAPI 文档同步实现。

## 零、已确认的决策（2026-11-08）

- **数据库管理第一版 = P0**：表清单 + 字段详情 + 备份/下载/删除 + 优化表 + 导出列表。**不做**备注编辑、**不做**备份恢复。
- **接口管理数据来源 = OpenAPI 自动同步**：从网关 `/v3/api-docs` 拉取，落库后只人工补充中文名/说明/示例；不做 CRMEB 式手工建接口。
- **在线调试第一版不做**：只做文档浏览与编辑，规避 SSRF 与误操作；后续按需再评估。
- **备份文件落在服务器本地持久目录**：由 `DB_BACKUP_DIR` 配置，部署时必须挂持久卷。
- 其余：两个页面都挂在「系统管理」下（`admin_menu.parent_id = 8`）；P0 只给超管角色授权。

---

## 一、CRMEB 是怎么做的

### 1.1 数据库管理

前端页面：`template/admin/src/pages/system/maintain/systemDatabackup/index.vue`

- 两个 Tab：
  - **数据库列表**
    - 列：表名 / 备注（点击变输入框，失焦保存）/ 类型（ENGINE）/ 大小（DATA_LENGTH）/ 更新时间 / 行数 / 详情
    - 顶部操作：备份、优化表、修复表、导出文件（导出当前表清单为 CSV）
    - 详情：右侧抽屉展示 `SHOW FULL COLUMNS` 结果（字段名 / 数据类型 / 默认值 / 允许非空 / 自动递增 / 备注），字段备注也可行内编辑
  - **备份列表**
    - 扫描备份目录，按时间聚合成一条：备份名称 / part（分卷号）/ 大小 / compress / 时间 / 操作（导入、删除、下载）

后端：
- `adminapi/controller/v1/system/SystemDatabackup.php`
- `services/system/SystemDatabackupServices.php`
- `crmeb/services/MysqlBackupService.php`

实现要点：
- 表清单：`SHOW TABLE STATUS`
- 表结构：`information_schema.columns`
- 备份：`SHOW CREATE TABLE` + 每 1000 行 `SELECT *` 拼 INSERT；按 20MB 分卷、gzip、文件名为 `Ymd-His-<part>.sql`
- 恢复：按分卷 + `part/start` 断点续传，进度靠前端反复请求
- 改备注：直接 `ALTER TABLE ... COMMENT` / `ALTER TABLE ... MODIFY COLUMN ... COMMENT`

结论：交互值得抄；"拼接 DDL 改备注"和"在线恢复"是高风险点，必须加护栏。

### 1.2 接口管理

前端页面：`pages/system/backendRouting/index.vue` + `debugging.vue`

- 顶部 4 个来源 Tab（管理端 / 用户端 / 客服端 / 对外接口）
- 左侧树（`vue-tree-list`）：分类目录 + 接口叶子；叶子前面有 GET / POST / PUT / DEL 色块（蓝 / 绿 / 橙 / 红）
- 右侧详情：
  - 接口名称 / 请求类型 / 功能描述 / 所属分类 / 是否公共
  - 调用方式：路由地址 / 文件地址 / 方法名
  - header / query / body / response 四张可编辑的树形参数表
  - 返回数据示例（JSON）+ 错误码（code / value / solution）
- 顶部操作：新增分类、同步；节点操作：新增接口、重命名分类、删除
- 调试抽屉：改 method + path，填 Params / Body / Header，直接发请求并显示结果

后端：`SystemRouteServices::syncRoute()`
- 反射加载 ThinkPHP 路由文件，按 `mark_name` / `cate_name` 建两级分类
- 与 `system_route` / `system_route_cate` 做 diff：新增的入库，路由文件里不存在的删除
- 这套做法强依赖"路由集中写在路由文件里"，Spring 的注解路由不能这样扫描。

---

## 二、本项目现状（决定实现方式）

### 前端（`e-commerce`）
- Vue 3 + Vite + Ant Design Vue 3.2，页面统一 `<script setup>`
- 静态路由：[index.js](../../src/router/index.js)，`meta.permission` 控制访问，[permission.js](../../src/router/permission.js) 做兜底跳转
- 按钮权限：`v-permission="'system:xxx:yyy'"`
- 侧边栏由静态路由渲染（`renderMenu: true`）；`admin_menu` 表负责"角色分配 + 权限码"
- 请求封装：[request.js](../../src/api/request.js)（axios，带 Bearer token）
- 系统管理接口集中在 [system.js](../../src/api/system.js)
- 下载工具：[download.js](../../src/utils/download.js)（blob）

### 后端（`e-commerce-boot`）
- Spring Boot 微服务；网关 8080；系统管理在 `auth-service`（9001）
- **单库 MySQL** `e_commerce_db`（所有服务共用，见 `auth-service/src/main/resources/application-dev.yml`）
- 统一返回 `Result<T>` / `PageVO<T>`；权限 `@RequirePerm`；审计 `@OperationLog`
- `common` 已带：`JdbcTemplate`、`HttpClientUtil`（RestClient）、springdoc、Jackson
- 网关已聚合各服务 OpenAPI：
  - `/v3/api-docs/swagger-config`
  - `/auth-service/v3/api-docs`、`/product-service/v3/api-docs` … `/monitor-service/v3/api-docs`
- 各服务 `springdoc.api-docs.enabled` 默认关闭（`SPRINGDOC_ENABLED:false`）

### 差异对照

| 能力 | CRMEB | 本项目建议 |
|---|---|---|
| 表清单 | `SHOW TABLE STATUS` | `information_schema.TABLES`（分页 + 搜索） |
| 备份 | PHP 拼 SQL 分卷 | Java JDBC + gzip + 分卷 |
| 改备注 | 直接拼 ALTER | 只允许表/字段注释，标识符白名单 |
| 接口数据 | 扫描路由文件 | 拉取网关 OpenAPI 自动入库 |
| 调试 | 同源任意路径直发 | 仅 `/api/admin/**` + 生产开关 + 审计 |

---

## 三、数据库管理：前端怎么做

### 3.1 页面与路由

新增文件：
- `src/api/systemDatabase.js`
- `src/pages/system/database/DatabaseManage.vue`
- `src/pages/system/database/TableColumnsDrawer.vue`

路由（加到 `src/router/index.js` 的 `/system` children）：

```js
{
  path: 'database',
  name: 'SystemDatabase',
  meta: { title: '数据库管理', permission: 'system:db:list' },
  component: () => import('@/pages/system/database/DatabaseManage.vue'),
}
```

### 3.2 页面骨架（AntD 组件对照 CRMEB 的 Element）

`<a-tabs>`：**数据库表** / **备份记录**

数据库表 Tab：
- 工具条：`<a-input-search>`（表名）+「备份」「优化表」「修复表」「刷新」「导出列表」
- `<a-table :row-selection>` 多选，列：表名 / 备注 / 引擎 / 大小 / 行数 / 更新时间 / 操作（详情）
- 大小由后端返回 `dataLengthHuman`，前端不再换算
- 详情 `<a-drawer>`：`<a-descriptions>` 表信息 + `<a-table>` 字段列表

备份记录 Tab：
- `<a-table>`：备份名称 / 分卷 / 大小 / 压缩 / 创建时间 / 操作（下载、删除、恢复[P2]）
- 「立即备份」：`<a-modal>` + `<a-tree-select multiple>` 选表（默认全库）
- 备份进度：`<a-progress>` + 2s 轮询，完成后刷新列表

行内改备注：
- 展示态 `<a-typography-text>`，点击切 `<a-input @blur @pressEnter>`，失败回滚原值

权限：
- 本次实现：`system:db:backup` / `system:db:optimize` / `system:db:download` / `system:db:delete`
- 暂缓：`system:db:comment`（备注编辑）、`system:db:restore`（恢复）

交互规则：
- 未选表点备份/优化 → 提示"请先选择表"，不发请求
- 备份/优化是长任务：按钮 loading + 防重复点击
- 下载必须走 axios `responseType: 'blob'` + `downloadBlob`；**不要 `window.open`**（浏览器不会带 Authorization）
- 删除/优化都要二次确认；恢复（P2）文案必须写明"会覆盖现有数据"

### 3.3 前端 API 契约

```js
// src/api/systemDatabase.js
import axios from './request.js'

export const getDbTables = (params) =>
  axios.get('/api/admin/system/database/tables', { params })

export const getTableColumns = (table) =>
  axios.get(`/api/admin/system/database/tables/${encodeURIComponent(table)}/columns`)

export const updateTableComment = (table, comment) =>
  axios.put(`/api/admin/system/database/tables/${encodeURIComponent(table)}/comment`, { comment })

export const updateColumnComment = (table, column, comment) =>
  axios.put(
    `/api/admin/system/database/tables/${encodeURIComponent(table)}/columns/${encodeURIComponent(column)}/comment`,
    { comment }
  )

export const optimizeTables = (tables) =>
  axios.post('/api/admin/system/database/tables/optimize', { tables })

export const repairTables = (tables) =>
  axios.post('/api/admin/system/database/tables/repair', { tables })

export const createDbBackup = (tables) =>
  axios.post('/api/admin/system/database/backups', { tables })

export const getDbBackups = () => axios.get('/api/admin/system/database/backups')

export const getDbBackupTask = (taskId) =>
  axios.get(`/api/admin/system/database/tasks/${taskId}`)

export const downloadDbBackup = (fileName) =>
  axios.get(`/api/admin/system/database/backups/${encodeURIComponent(fileName)}/download`, {
    responseType: 'blob',
  })

export const deleteDbBackup = (fileName) =>
  axios.delete(`/api/admin/system/database/backups/${encodeURIComponent(fileName)}`)

export const restoreDbBackup = (fileName) =>
  axios.post(`/api/admin/system/database/backups/${encodeURIComponent(fileName)}/restore`)
```

返回结构（沿用项目约定 `Result<PageVO<T>>`）：

```jsonc
// GET /tables
{
  "success": true,
  "data": {
    "list": [
      {
        "name": "orders",
        "comment": "订单表",
        "engine": "InnoDB",
        "dataLength": 15204352,
        "dataLengthHuman": "14.5 MB",
        "rows": 3284,
        "updateTime": "2026-11-07 18:20:11"
      }
    ],
    "total": 42,
    "page": 1,
    "pageSize": 50
  }
}
```

---

## 四、数据库管理：后端怎么做

### 4.1 代码位置与类

放在 `auth-service`（系统管理能力、权限注解、操作日志都在这里，且是单库）：

```
ecommerce.auth
├── controller/system/AdminDatabaseController.java
├── service/system/DatabaseAdminService.java
├── service/system/DatabaseBackupService.java
├── vo/DbTableVO.java / DbColumnVO.java / DbBackupVO.java / DbBackupTaskVO.java
└── dto/DbCommentDTO.java / DbTablesDTO.java
```

直接用 `JdbcTemplate` / `DataSource`，不引第三方备份库。

### 4.2 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/api/admin/system/database/tables?keyword&page&pageSize` | `system:db:list` | 表清单分页 |
| GET | `/api/admin/system/database/tables/{table}/columns` | `system:db:list` | 表结构 |
| PUT | `/api/admin/system/database/tables/{table}/comment` | `system:db:comment` | 改表备注 |
| PUT | `/api/admin/system/database/tables/{table}/columns/{column}/comment` | `system:db:comment` | 改字段备注 |
| POST | `/api/admin/system/database/tables/optimize` | `system:db:optimize` | 批量优化 |
| POST | `/api/admin/system/database/tables/repair` | `system:db:optimize` | 批量修复 |
| POST | `/api/admin/system/database/backups` | `system:db:backup` | 发起备份（异步） |
| GET | `/api/admin/system/database/backups` | `system:db:list` | 备份记录 |
| GET | `/api/admin/system/database/backups/{fileName}/download` | `system:db:download` | 下载 |
| DELETE | `/api/admin/system/database/backups/{fileName}` | `system:db:delete` | 删除 |
| GET | `/api/admin/system/database/tasks/{taskId}` | `system:db:list` | 备份进度 |
| POST | `/api/admin/system/database/backups/{fileName}/restore` | `system:db:restore` | 恢复（P2） |

### 4.3 表清单查询

```sql
SELECT TABLE_NAME, TABLE_COMMENT, ENGINE, DATA_LENGTH, TABLE_ROWS, UPDATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_TYPE = 'BASE TABLE'
  AND (#{keyword} IS NULL OR TABLE_NAME LIKE CONCAT('%', #{keyword}, '%'))
ORDER BY TABLE_NAME
LIMIT #{offset}, #{pageSize}
```

注意：
- `TABLE_ROWS` 是 InnoDB 的**估算值**，UI 上加"约"
- `DATA_LENGTH` 只是数据字节，索引另算
- 系统日志类表（`operation_log`、`admin_login_log`）建议在服务端用黑名单排除，不交给前端过滤

表结构用：

```sql
SHOW FULL COLUMNS FROM `xxx`
```

### 4.4 标识符安全（最容易出事）

DDL 不能用参数占位符，只能"校验 + 反引号"：

```java
private static final Pattern IDENT = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

private String requireTable(String table) {
    if (table == null || !IDENT.matcher(table).matches()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "表名不合法");
    }
    if (!metadata.existsTable(table)) {   // 再查 information_schema 确认存在
        throw new BusinessException(HttpStatus.NOT_FOUND, "表不存在");
    }
    return table;
}
```

绝对不要提供"执行 SQL"输入框，也不要让前端传完整 DDL。

### 4.5 备份实现（不依赖 mysqldump）

容器里通常没有 `mysqldump`，用纯 Java：

1. 校验表名（同上）
2. 每张表先写 `SHOW CREATE TABLE` 的结构
3. 循环 `SELECT * FROM \`t\` LIMIT ?, 1000` 取数（MySQL 流式读取：`stmt.setFetchSize(Integer.MIN_VALUE)`）
4. 生成 INSERT，转义 `\` `'` `\n` `\r` `\0` `\x1a`
5. `GZIPOutputStream` 写文件，单文件超 20MB 滚到下一分卷
6. 文件名 `yyyyMMdd-HHmmss-<part>.sql.gz`
7. 目录由配置 `DB_BACKUP_DIR` 决定（默认 `./backup/db/`），**必须挂持久卷**
8. 备份记录写 `admin_db_backup`，状态 RUNNING / SUCCESS / FAILED
9. 异步执行（`@Async` 线程池 + taskId），前端轮询进度

恢复（P2，单独评估）：

- 只允许超管（权限码之外再加 `role_key=admin` 判断）
- 恢复前**强制自动全库备份一次**
- 按行末 `;\n` 拆分语句逐条执行（不要开 `allowMultiQueries`）
- 恢复期间建议进入维护模式，避免业务写入
- 失败要能看出停在哪条语句，不能只返回 500

### 4.6 安全红线

- 所有写操作加 `@OperationLog(module="system", action="DB_BACKUP" / "DB_OPTIMIZE" / "DB_RESTORE" / "DB_DELETE")`
- 备份目录不能被静态资源访问；下载必须鉴权
- 限制备份文件大小、数量、保留天数，防磁盘打满
- 恢复/删除仅超管
- `REPAIR TABLE` 对 InnoDB 基本无效，UI 上要标注
- `OPTIMIZE TABLE` 会锁表/重建表，提示低峰执行

### 4.7 备份记录表

```sql
CREATE TABLE IF NOT EXISTS admin_db_backup (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_name VARCHAR(128) NOT NULL COMMENT '不含分卷号的文件名基',
  part INT NOT NULL DEFAULT 1 COMMENT '分卷号',
  size_bytes BIGINT NOT NULL DEFAULT 0,
  compressed TINYINT NOT NULL DEFAULT 1,
  table_count INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'RUNNING/SUCCESS/FAILED',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_file_part (file_name, part),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库备份记录';
```

---

## 五、接口管理：前端怎么做

### 5.1 关键决策

**不照抄"扫描路由文件"**。两个可选方案：

- **方案 A（推荐）**：从网关聚合的 springdoc OpenAPI 同步接口清单，落库后再人工补充中文名 / 说明 / 示例 / 错误码，并提供调试。
- 方案 B：纯手工维护接口记录（CRMEB 式表单）。我们的接口文档本来就是自动生成的，手工再录一遍是重复劳动。

采用 A，人工字段作为"补充备注"叠加在自动同步结果上。

### 5.2 页面与路由

新增：
- `src/api/systemApi.js`
- `src/pages/system/api/ApiManage.vue`
- 调试抽屉 `ApiDebugDrawer.vue`（P2，本次不做）

```js
{
  path: 'api',
  name: 'SystemApiDocs',
  meta: { title: '接口管理', permission: 'system:api:list' },
  component: () => import('@/pages/system/api/ApiManage.vue'),
}
```

### 5.3 布局与交互

顶部：
- `<a-select>` 服务切换（auth / product / order / aftersale / user / file / monitor）
- `<a-input-search>` 接口名 / 路径搜索
- 「同步」按钮（`v-permission="'system:api:sync'"`），右侧显示"最近同步时间"

左栏（约 320px）：
- `<a-tree block-node :tree-data>`，`#title` 插槽：
  - 分组节点：分组名 + 接口数量
  - 接口节点：method 色块 + 接口名
- 方法配色沿用 CRMEB 观感：GET `#61affe` / POST `#49cc90` / PUT `#fca130` / DELETE `#f93e3e`

右栏：
- 头部：接口名 + method tag + path（`<a-typography-text copyable>`）+「编辑文档」（「调试」P2）
- `<a-descriptions>`：服务 / 分组 / 摘要 / 所需权限 / 是否公共 / 最近同步
- `<a-tabs>`：Query / Header / Body / Response，每个 tab 一张树形 `<a-table>`，列：参数名 / 类型 / 必填 / 说明
- 返回示例只读代码块；错误码 `<a-table>`
- 编辑态只允许改人工字段：中文名 / 描述 / 返回示例 / 错误码 / 是否公共；**method 和 path 只读**（同步覆盖）

调试抽屉（P2，本次不做）：
- 顶部固定 method + path，**不可编辑**（防 SSRF）
- Params / Headers / Body 三块；Body 支持 JSON 文本
- 发送后显示状态码、耗时、响应体（截断提示）、「复制 cURL」

### 5.4 前端 API 契约

```js
// src/api/systemApi.js
import axios from './request.js'

export const getApiServices = () => axios.get('/api/admin/system/api-docs/services')
export const syncApiDocs = () => axios.post('/api/admin/system/api-docs/sync')
export const getApiTree = (params) => axios.get('/api/admin/system/api-docs/tree', { params })
export const getApiDoc = (id) => axios.get(`/api/admin/system/api-docs/${id}`)
export const updateApiDoc = (id, data) => axios.put(`/api/admin/system/api-docs/${id}`, data)
// P2：export const debugApiDoc = (id, data) => axios.post(`/api/admin/system/api-docs/${id}/debug`, data)
```

同步返回：

```jsonc
{
  "success": true,
  "data": { "added": 12, "updated": 87, "missing": 2, "total": 101, "syncedAt": "2026-11-08 10:20:31" }
}
```

---

## 六、接口管理：后端怎么做

### 6.1 代码位置与类

```
ecommerce.auth
├── controller/system/AdminApiDocController.java
├── service/system/ApiDocService.java
├── service/system/ApiDocSyncService.java
├── mapper/AdminApiDocMapper.java
└── vo/ApiGroupVO.java / ApiDocVO.java / ApiSyncResultVO.java
```

### 6.2 接口清单

| 方法 | 路径 | 权限码 |
|---|---|---|
| GET | `/api/admin/system/api-docs/services` | `system:api:list` |
| POST | `/api/admin/system/api-docs/sync` | `system:api:sync` |
| GET | `/api/admin/system/api-docs/tree?service&keyword` | `system:api:list` |
| GET | `/api/admin/system/api-docs/{id}` | `system:api:list` |
| PUT | `/api/admin/system/api-docs/{id}` | `system:api:edit` |
| POST | `/api/admin/system/api-docs/{id}/debug` | `system:api:debug` | P2，本次不做 |

### 6.3 同步流程

1. `GET http://gateway:8080/v3/api-docs/swagger-config` 取 `urls[]`
2. 逐个 `GET http://gateway:8080{url}`（用 `common` 的 `HttpClientUtil`）
3. 用 `io.swagger.v3.core.util.Json.mapper().readValue(json, OpenAPI.class)` 解析（common 已带 swagger-core）
4. 遍历 `paths` × operations，组装字段：
   - `service_name`：来自 swagger-config 的 name / url 前缀
   - `group_name`：`tags[0]`，没有就用 path 第一段
   - `http_method` / `path` / `summary` / `description`
   - `query_params` / `header_params` / `body_params` / `response_params`
   - `require_perm`：见 6.6
5. 以 `(service_name, http_method, path)` 为唯一键 upsert
6. 本次未出现的记录标记 `missing=1`，**不要直接删**（网关一次抖动会清空文档），人工确认后再清理
7. 返回 `{added, updated, missing, total, syncedAt}`

实现注意：
- RestClient 超时 5s，单次同步总超时 30s
- 单个服务解析失败只跳过该服务，不影响其它服务
- 已人工编辑的字段在 upsert 时**不覆盖**（SQL 只更新机器字段）
- 生产提示：各服务 `springdoc.api-docs.enabled` 默认 false。要么只在内网/开发环境开启，要么服务侧打开但网关不暴露 swagger-ui，仅允许 auth-service 内网调用
- 同步失败不影响浏览：已入库文档照常展示，页面显示"最近同步时间 + 失败原因"

### 6.4 数据表

```sql
CREATE TABLE IF NOT EXISTS admin_api_doc (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_name VARCHAR(64) NOT NULL,
  group_name VARCHAR(128) NOT NULL,
  api_name VARCHAR(128) NOT NULL COMMENT '人工中文名，可空，空则显示 summary',
  http_method VARCHAR(10) NOT NULL,
  path VARCHAR(255) NOT NULL,
  summary VARCHAR(255) NULL COMMENT 'OpenAPI summary',
  description TEXT NULL,
  require_perm VARCHAR(100) NULL,
  is_public TINYINT NOT NULL DEFAULT 0,
  query_params JSON NULL,
  header_params JSON NULL,
  body_params JSON NULL,
  response_params JSON NULL,
  response_example JSON NULL,
  error_codes JSON NULL,
  missing TINYINT NOT NULL DEFAULT 0,
  sync_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_service_method_path (service_name, http_method, path),
  KEY idx_group (service_name, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口文档（OpenAPI 同步 + 人工补充）';
```

参数用 JSON 存整棵树（前端直接渲染），与 CRMEB 的 `query/header/request/response` 四个 JSON 字段思路一致，比拆参数表简单。

### 6.5 调试的安全设计（P2，本次不做）

CRMEB 的调试是"同源任意 path 直发"，在我们这里等于开放 SSRF + 用管理员 token 打任意接口。必须收紧：

- path 只能来自库里的记录，前端不传 URL
- 只允许 `/api/admin/**`；拒绝 `/api/login` 等公共写接口
- 透传当前管理员 token；日志中请求头脱敏
- 超时 5s，响应体截断 64KB
- 生产默认关闭：配置 `system.api.debug-enabled: false`
- 每次调试写 `@OperationLog(module="system", action="API_DEBUG")`，只记 path/method/状态码，不记 body
- 只支持 GET/POST/PUT/DELETE，不支持 multipart 上传

### 6.6 可选增强：权限码可视化

在 `common/OpenApiConfig` 的自定义 `OperationCustomizer` 里，把 `@RequirePerm` 写入 `operation.extensions["x-require-perm"]`，同步时落库。

价值：
- 接口详情直接展示"调这个接口需要什么权限码"
- 与 `admin_menu.perms` 比对，找出：
  - 后端有、菜单没配（权限漏配，前端永远 403）
  - 菜单配了、后端没有（死权限码，配了没用）

对我们这种 RBAC 项目，比 CRMEB 的"是否公共"更有用。

---

## 七、菜单与权限 SQL

`admin_menu` 的系统管理父节点是 `8`；本地与生产 `MAX(id)` 均为 1017，850~860 区间空闲。实际执行脚本：`e-commerce-boot/docs/sql/20261108_system_db_api.sql`（幂等，可重复执行）。

P0 只落 850~857（恢复/备注/调试暂不建按钮，后续加功能时再补）：

```sql
-- 菜单：850 数据库管理、851 接口管理（parent_id=8）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status)
VALUES
  (850, 8, '数据库管理', 'C', '/system/database', NULL, 'system:db:list', 'DatabaseOutlined', 6, 1, 1),
  (851, 8, '接口管理',   'C', '/system/api',      NULL, 'system:api:list', 'ApiOutlined',     7, 1, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), update_time = NOW();

-- 按钮权限（P0）
INSERT INTO admin_menu (id, parent_id, menu_name, menu_type, perms, sort_order, visible, status)
VALUES
  (852, 850, '发起备份', 'F', 'system:db:backup',   1, 1, 1),
  (853, 850, '优化表',   'F', 'system:db:optimize', 2, 1, 1),
  (854, 850, '下载备份', 'F', 'system:db:download', 3, 1, 1),
  (855, 850, '删除备份', 'F', 'system:db:delete',   4, 1, 1),
  (856, 851, '同步接口', 'F', 'system:api:sync',    1, 1, 1),
  (857, 851, '编辑接口', 'F', 'system:api:edit',    2, 1, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  perms = VALUES(perms), sort_order = VALUES(sort_order), visible = VALUES(visible),
  status = VALUES(status), update_time = NOW();

-- 超管授权
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.id IN (850, 851, 852, 853, 854, 855, 856, 857)
WHERE r.role_key = 'admin';
```

---

## 八、落地顺序

1. 建表 SQL + 菜单 seed（本地执行后再同步生产）
2. 后端数据库管理 P0：表清单 / 字段 / 备份 / 下载 / 删除 / 优化
3. 前端数据库管理页面 + API + 路由 + 菜单权限
4. 联调重点：空库、大表、备份中断、无权限、并发点击
5. 后端接口管理：同步 + 树查询 + 详情 + 人工编辑
6. 前端接口管理页面：左树右详情
7. （P2，暂缓）调试功能：默认关闭 + 审计
8. 发版 + 生产只读验证（只在本地跑备份/恢复写操作）

---

## 九、验收清单

数据库管理：
- [ ] 表清单分页、搜索、大小/行数展示正确
- [ ] 表结构字段与 `SHOW FULL COLUMNS` 一致
- [ ] 多表备份生成 `*.sql.gz`，可分卷，记录状态正确
- [ ] 备份可下载（带 token，非 401）、可删除
- [ ] 未选表时操作被前端拦截
- [ ] 无 `system:db:*` 权限的账号 403
- [ ] 优化/修复有二次确认，且提示锁表风险
- [ ] 日志里能看到备份/优化/删除的审计记录

接口管理：
- [ ] 同步能从网关拉到所有服务的 OpenAPI
- [ ] 树按服务/分组正确展示，method 颜色正确
- [ ] 详情参数树、示例、错误码与 OpenAPI 一致
- [ ] 人工编辑的字段在再次同步后不被覆盖
- [ ] 同步失败时已入库文档仍可浏览

---

## 十、实施记录（2026-11-08）

已完成（含发版）：
- 前端：`src/pages/system/database/DatabaseManage.vue`、`src/pages/system/api/ApiManage.vue`、`src/api/systemDatabase.js`、`src/api/systemApi.js`，路由已注册 `/system/database`、`/system/api`；`npm run build` 通过。
- 后端：`auth-service` 的 controller / service / mapper / VO / DTO 与 `AdminApiDocMapper.xml`，`mvn -pl auth-service -am -DskipTests compile` 通过。
- SQL：`e-commerce-boot/docs/sql/20261108_system_db_api.sql` 已在本地执行并校验（建表 2 张、菜单 850~857、超管授权 8 条）。
- 生产库：执行前已备份 `/www/backup/e_commerce_db_2026-11-08_2041.sql`（2.4M，含 Dump completed 校验）；同一份 SQL 已在生产执行并校验，结果与本地一致。
- 生产 compose 已补 `auth-service` 的 `./backup/db:/data/backup/db` 挂载与 `DB_BACKUP_DIR`、`GATEWAY_URL`，服务器旧文件备份为 `docker-compose.yml.bak-20261108-2045`。
- auth-service 已发布：`ccr.ccs.tencentyun.com/jiemi-ecommerce/auth-service:20261108-30bd176`，容器 running；运行时挂载与 `DB_BACKUP_DIR`、`GATEWAY_URL`、`SPRINGDOC_ENABLED` 均已确认。
- 前端已发布到 `/www/wwwroot/jiemids.com`（自动快照备份在 `/www/backup/web`），`ApiManage`、`DatabaseManage` 产物公网 200。

待办：
- 生产联调：数据库列表/字段/备份/下载/删除/优化；接口同步/树/详情/编辑；无权限账号 403。
- 生产 `.env` 已置 `SPRINGDOC_ENABLED=true` 并重启 7 个 Java 服务；`api.jiemids.com` 的 Nginx 已拒绝 `/v3/api-docs`、`/swagger-ui`、`/webjars` 及各 `/*-service/` 文档路径，公网 404，Docker 内网经网关访问仍为 200。

---

## 十一、架构调整：monitor-service → system-service（2026-11-08）

拆分原则：`auth-service` 只保留登录认证（登录/退出/验证码/当前用户信息/jwt）；后台「系统管理 + 系统监控」统一由 `system-service` 承载。

- 目录/模块：`monitor-service` 改名为 `system-service`，包名 `ecommerce.monitor` → `ecommerce.system`，artifactId 与 Nacos 服务名均为 `system-service`，端口沿用 `9008`。
- 迁移内容：用户、角色、菜单、登录日志、操作日志、日志清理/导出、数据库管理、接口管理全部从 `auth-service` 迁入 `system-service`；监控侧 Cache/OnlineUser/Server/Task 控制器保留在同服务。`system-service` 补齐 mybatis、mapper XML、poi、spring-security-crypto 等依赖。
- 网关：`/api/admin/system/**`、`/api/admin/monitor/**` 均指向 `lb://system-service`；`auth-service` 仅保留 `/api/login`、`/api/logout`、`/api/admin/info`、`/api/auth/captcha`；OpenAPI 聚合地址改为 `/system-service/v3/api-docs`。前端 API 路径与页面无需改动。
- 部署：`deploy/docker-compose.yml`、`docker-compose.override.yml`、`release.sh` 服务名同步改名；生产已启动 `system-service` 并下线旧 `monitor-service` 容器；`system-service` 保留 docker.sock 与 `/data/backup/db` 持久卷。
- 数据迁移：`docs/sql/20261108_rename_monitor_to_system.sql`
  - `admin_monitor_task.logCleanup` 的 `task_group` 改为 `system-service`（否则手动执行找不到服务）；
  - `admin_api_doc` 中 `monitor-service` 及 `auth-service` 下 `/api/admin/system%` 的历史文档改归 `system-service`，保留人工编辑字段；随后已触发一次 OpenAPI 同步，`missing=0`、无重复服务。
  - 生产执行前备份：`/www/backup/e_commerce_db_20261108-214603.sql`（2.9M）。
- 发布镜像：`auth-service` / `gateway` / `system-service` 均为 `ccr.ccs.tencentyun.com/jiemi-ecommerce/*:20261108-30bd176-dirty`。
- 验证：本地整仓 `mvn -DskipTests compile` 通过；生产用超管权限令牌冒烟，用户/角色/菜单/日志/数据库/接口文档/监控 13 个端点全部 200；`/api/admin/info` 菜单树正常；公网 `/system-service/v3/api-docs` 为 404，内网为 200。
