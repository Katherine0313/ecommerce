# 后台 AI 模块 · 问题修复与增强方案

> 三块内容：① 文案助手场景扩展（新增 title 主标题、保留详情、支持新增商品）；② 知识库 Excel 批量导入；③ AI 生图。
> 现状均按代码核对，标注了改动文件。

## 范围与架构（2026-11-09 全仓探索后修订）

**本期范围：只做「电商后台管理系统」的 AI 模块。** 商城 H5 / 小程序的 AI 客服（`/api/mall/ai/**`）、人工客服通道、小程序本地兜底规则**不在本期**，本方案不改动它们的任何行为。

涉及的两个后端、三个前端的事实（已核对）：

| 层 | 事实 | 对本方案的意义 |
|---|---|---|
| Java `product-service` | `InternalAiController` 提供 `/internal/ai/**` 内部接口（`X-Internal-Token` 鉴权）：商品查询/搜索/**文案更新** | AI 写商品数据唯一通道 |
| Java 更新白名单 | **现仅 `name / subTitle / description(→body)`，无 `title`、无 `image`** | §1 主标题回填、§3 设为主图，**必须先扩 Java 白名单** |
| Python FastAPI | `app/api/ai_chat.py` 承载后台 AI 全部路由，`require_admin(..., 权限编码)` 校验；`internal_client.py` 走上述内部接口 | 新接口都加在这里 |
| 后台前端 | `src/pages/ai/` 已有 4 页：AI 对话（`ai:chat:use`）、文案助手（`ai:copy:generate`）、人工客服（`ai:human:use`）、知识库；`src/api/ai.js` 全部走 `/api/admin/ai/**` | 新功能挂现有页面，不新增菜单 |
| 商城/小程序 | AI 入口独立（`/api/mall/ai/**`），与后台接口无共用路径 | 后台改动不影响 C 端 |

---

## 0. 问题清单

| # | 问题 | 性质 |
|---|---|---|
| 1 | 文案助手缺主标题 `title` 场景；且必须填商品 ID，新增商品用不了 | 功能缺失 |
| 2 | 知识库不支持 Excel 上传、只能逐条录入 | 功能缺失 |
| 3 | 电商场景没有 AI 生图能力 | 功能缺失 |

---

## 1. 文案助手场景扩展（新增 title、保留详情、支持新增商品）

### 1.1 现状（已核对）

`products` 表 / `AdminProductVO` 字段：`name`(名称)、`subTitle`(副标题)、**`title`(主标题/抬头，≤30 字)**、`body`(图文详情富文本)。

当前 `copy.py` 的 `_SCENE_PROMPT` 与前端 `CopyAssistant.vue`：

| scene | 生成字段 | 回填字段 | 状态 |
|---|---|---|---|
| `title` | `name` | `products.name` | 保留，但场景名易误读（生成的是名称不是主标题） |
| `subtitle` | `subTitle` | `products.subTitle` | 保留 |
| `description` | `description` | `products.body`（富文本详情） | **保留**——详情文案 + §3 生成的详情图都进 body |
| `all` | 三个一起 | 同上 | 同步扩展 |

已拍板的两条变更：**① 新增主标题场景；② 支持新增商品**（现状必须填商品 ID 查库，新增商品没有 ID 用不了）。

### 1.2 场景与落库字段

| scene | 落库字段 | 约束（写进 prompt + 前端校验） |
|---|---|---|
| `name` | `products.name` | 商品名称，≤20 字（原 `title` 场景改名） |
| `subTitle` | `products.subTitle` | 副标题，≤30 字（原 `subtitle`） |
| `title` | `products.title` | **主标题（抬头），≤30 字**，一条一行、不堆关键词（新增）。已拍板长度口径统一为 30：AddGoods `maxLength` 100→30、EditModal 300→30，与 AI 硬截断一致 |
| `description` | `products.body` | 详情文案，150 字左右。**目标形态已拍板：纯文字段落 + 原图**（文案插头部/段落，原 `<img>` 不动，不做图文混排） |
| `all` | 以上四个 | JSON：`{"name","subTitle","title","description"}` |

### 1.3 新增商品支持（无 productId 模式）

现状：`copy.generate(product_id, ...)` 第一步 `internal_client.get_product(product_id)` 取商品信息拼 prompt，没有 ID 直接返回「商品不存在」。

方案：`POST /api/admin/ai/copy/generate` 入参二选一：

```
{ productId: 123, scene, requirement? }          // 已有商品（现状）
{ product: { name, categoryName?, price?, subTitle?, sellingPoints? }, scene, requirement? }  // 新增商品
```

- 后端：`productId` 优先；没有就用内联商品信息拼同一个 prompt，不再强制查库。返回 `found` 语义改为始终 true（内联模式无商品可查）
- 前端两个入口，**各自独立、互不绕行**（已拍板：加商品时不走「文案助手生成→回填」的弯路）：
  1. **AddGoods.vue 内嵌 AI 生成（主路径）**：表单区直接加「AI 生成」按钮组（名称/副标题/主标题/详情 四个小按钮 + 「全部生成」），把当前表单的分类/名称/卖点以**内联模式**调 `/copy/generate`。结果**一律先进预览区，运营逐项点「填入」才写入表单**（已拍板：不自动覆盖已填内容；description 填入时追加进富文本编辑器）；§3 的生图按钮也内嵌在这里，主图/详情图原地生成原地用。**整个过程不离开添加商品页，也不依赖商品已入库。**
  2. **CopyAssistant.vue 独立保留（辅助路径）**：服务“不在加商品时也要用 AI”的场景——给已有商品换文案、临时写一段宣传语。商品 ID 改为选填，不填时展示内联表单；无商品 ID 时「回填到商品」禁用，只能复制。

改动清单：

- **Java `InternalAiController`（前置，必改）**：更新白名单加 `title`——`CopyUpdateRequest` 加 `title` 字段，update 时 `dto.setTitle(...)`，映射 `products.title`；不加则主标题场景生成后无法回填
- `app/ai/copy.py`：`_SCENE_PROMPT` 场景 key 改为 `name/subTitle/title/description/all`；`generate()` 支持内联商品信息；`title` 生成后 30 字硬截断并记日志；`_parse_all` 加 `title` 字段
- `app/ai/tools.py` 的 `generate_copy` docstring **必须同步改成新 key**（它是 Agent 工具，LLM 按 docstring 传参，不改会一直用旧 key）；copy.py 同时保留旧 key 别名映射长期有效（`title`旧义→`name`、`subtitle`→`subTitle`），记废弃日志
- 前端 CopyAssistant.vue：场景下拉加「主标题」；ID 选填 + 内联表单；FIELD_DEFS 加 `title` 映射 `products.title`
  - **回填机制事实（已核对）**：CopyAssistant 的「回填」不走任何 AI 接口，是 `router.push('/goods/list?editId=xxx')` 跳商品编辑页由 Java 常规保存。因此 **`EditModal.vue` 必须有 title 主标题字段**（否则生成的主标题没有落点），需检查补充
- 前端 AddGoods.vue：加「AI 生成」入口（内联模式，预览区 + 逐项手动填入）

---

## 2. 知识库 Excel 批量导入

### 2.1 Python 后端现状（已逐文件核对）

| 文件 | 现状事实 | 对本方案的意义 |
|---|---|---|
| `scripts/import_kb.py` | **已有 CLI 版 Excel 导入**：openpyxl 读 `QandA.xlsx`（列序：分类/问题/答案/关键词），按 `(分类, 问题)` 唯一键 `ON DUPLICATE KEY UPDATE` upsert，支持 `--dry-run` 只统计不写库 | 解析 + 幂等逻辑现成，抽出来接口复用 |
| `requirements.txt` | **openpyxl 已在依赖里**（注释「仅导入脚本使用」） | 不用新增依赖，只改用途 |
| `app/api/ai_chat.py` | 已有 `/api/admin/ai/kb` 单条 CRUD，scope 校验 `客服/运营`，权限 `ai:chat:use` | 导入接口直接加在这个路由 |
| `app/kb/repo.py` | `load_entries(scope)` 按 scope 分缓存，`kb_cache_ttl` 默认 60 秒 | 导入后清缓存/等 TTL 过期即生效 |
| `app/kb/matcher.py` + `embedding.py` | 词面匹配 + bge-m3 语义兜底；embedding 懒加载补向量，失败降级词面 | AI 读库不读文件，已有兜底 |
| `app/main.py` | lifespan 启动时 `warm_up` 语义索引 | 导入后手动再触发一次 warm_up 即可 |
| `app/config.py` | `Settings` dataclass，全走环境变量 | 归档目录按同风格加 `KB_IMPORT_DIR` |
| 管理页 `src/pages/ai/KbManage.vue` | 只有单条 CRUD 弹窗 | 加「批量导入」入口 |

**结论：不是从零做，是把 `scripts/import_kb.py` 的能力「搬上管理后台」**，CLI 和接口共用一份解析代码。

### 2.2 总体流程（模板先行 → 预检 → 入库 → 文件归档）

```
KbManage.vue 点「批量导入」
  1) 先下载模板（xlsx，含示例行）——入口常驻，必须先按模板填
  2) 上传文件 → 后端先落盘归档（§2.6），再解析 + 预检（不落库）
       表头与模板对不上 → 直接抛异常，整单拒绝
       逐行校验 → 行级结果（ok / 失败原因 / 重复）
  3) 前端展示预检表格，确认后「确认导入」→ 批量 insert（一次事务）
  4) 返回统计：新增 N、跳过 M、失败 K
  5) 异步触发 embedding.warm_up()，语义检索立即覆盖新条目
```

### 2.3 Excel 模板列

模板由后端 `GET /api/admin/ai/kb/import/template` 用 openpyxl 内存生成并下载，表头固定为这 7 列（顺序固定）。
**前 4 列顺序与现有 `scripts/import_kb.py` 一致**（分类/问题/答案/关键词）：

| 列序 | 列 | 必填 | 说明 |
|---|---|---|---|
| 1 | 分类 | | 缺省「通用」，≤50 字 |
| 2 | 问题 | ✅ | ≤200 字 |
| 3 | 答案 | ✅ | ≤2000 字，支持换行 |
| 4 | 关键词 | | 逗号/顿号/分号/空格/竖线分隔（沿用脚本 `_split_keywords` 的兼容写法），≤200 字 |
| 5 | 优先级 | | 整数，默认 0 |
| 6 | 状态 | | 1 启用 / 0 停用，默认 1 |
| 7 | scope | | `客服` / `运营`，**留空则取导入弹窗选择的默认 scope**；填了以单元格为准 |

已拍板：**允许一个文件同时进两个库**（行内 scope 决定归属）；值不在 {客服, 运营} → 该行失败并注明原因。

### 2.3.1 前置 DDL 变更（需同步生产库）

现有唯一键 `uk_ai_kb_category_question (category, question)` **不含 scope**，跨库同名问答会互相覆盖。按决定改为：

```sql
ALTER TABLE ai_kb_qa
  DROP INDEX uk_ai_kb_category_question,
  ADD UNIQUE KEY uk_ai_kb_scope_category_question (scope, category, question);
```

不改这个键，「按 (scope, 分类, 问题) 判重」在现有结构上做不到。

### 2.4 表头校验（对不上就抛异常）

上传文件第一步先校验表头，校验不过**不解析任何数据行**：

- 缺列：如缺少「问题」/「答案」→ 400，message 明确「模板第 N 列应为『XX』，请使用最新模板」
- 多出未知列 / 列顺序不符 → 400，提示重新下载模板
- 表头含空白列、合并单元格 → 400
- 数据区为空（只有表头）→ 400
- 校验通过后才进入逐行预检

逐行预检规则（沿用 `import_kb.py` 的口径）：

- 问题/答案为空 → 该行失败并注明原因；超长 → 失败并给截断建议
- 重复判定：`(scope, 分类, 问题)` 与库内完全一致（依赖 §2.3.1 的新唯一键）→ 标记 `updated`（语义沿用脚本：**upsert 覆盖更新**答案/关键词/优先级/状态，`hit_count` 不清零），前端显示为「将更新已有条目」，无需勾选项
- scope 列非法值（非 客服/运营）→ 该行失败；留空 → 用弹窗默认 scope
- 表内自重复（同一文件里重复出现）→ 后者跳过，沿用脚本行为
- 限制：单次 ≤500 行、文件 ≤2MB；解析用 openpyxl（已在依赖），CSV 用标准库

### 2.5 接口（`app/api/ai_chat.py` 追加）

| 接口 | 说明 |
|---|---|
| `GET /api/admin/ai/kb/import/template` | 返回模板 xlsx（openpyxl 内存生成，不落盘） |
| `POST /api/admin/ai/kb/import/preview` | 上传文件 → 落盘归档 → 表头校验 → 逐行预检 → 行级结果（不落库） |
| `POST /api/admin/ai/kb/import/confirm` | 只传 `importFileId` → 后端**重读归档文件重新解析**（不信前端回传的 rows，防篡改）→ 批量 upsert |

代码组织（避免复制粘贴）：

- 把 `scripts/import_kb.py` 的 `load_rows / _split_keywords / upsert 逻辑` 抽到 `app/kb/importer.py`
- **importer 必须携带 scope 进 upsert**（现有脚本 INSERT 不带 scope，行全落默认 '客服'——这是已知坑，共用前必须修）
- CLI 脚本改为调 `app/kb/importer.py`（默认 scope=客服，行为不变）；三个 admin 接口也调同一模块
- `app/kb/repo.py` 加 `upsert_kb_entries_batch(rows)`：`INSERT INTO ai_kb_qa (scope, ...) ... ON DUPLICATE KEY UPDATE`，`executemany` 单事务，失败整体回滚

### 2.6 上传文件落地（文件放哪、AI 从哪读）

**两层的职责要分清：**

1. **Excel 文件本身 → 归档目录（只作留档/追溯）**
   - 存放位置：Python 服务本机 `upload/excel/kb/`（相对 Python 工作目录，与 Java 图片目录 `upload/image/` 平级；路径做成配置 `KB_IMPORT_DIR`，默认该值）
   - 命名：`{yyyyMMdd}_{uuid短}.xlsx`，保留原始文件不改内容
   - 每次 preview/confirm 都在返回里带 `importFileId`（即文件名），前端可见、可排查
   - 保留策略：目录定期清理，默认保留最近 30 天（脚本/接口任选其一，P1 再做界面化列表）
   - 文件**不是**知识库的数据源，AI 不直接读它
   - 配置：按 `config.py` 现有风格加 `kb_import_dir = _env("KB_IMPORT_DIR", "upload/excel/kb")`，启动时 `mkdir(parents=True, exist_ok=True)`

2. **AI 读取 → 数据库表 `ai_kb_qa`（唯一数据源）**
   - confirm 批量 upsert 进 `ai_kb_qa`，检索链路 `matcher.py`（词面）/ `embedding.py`（语义）读的都是这张表
   - 所以“导入完 AI 去哪读”的答案是：**读库，不读文件**；文件只是上传凭证与回溯依据
   - 生效时机：`repo.load_entries` 按 scope 有 60 秒缓存（`kb_cache_ttl`），confirm 成功后主动清该 scope 缓存，立即生效；不清理也最多延迟 60 秒
   - 语义索引：confirm 后异步 `embedding.warm_up(entries)`（与 `main.py` lifespan 启动预热同一函数）；即使失败，`semantic_match` 懒加载也会在首次检索时补向量，接口异常退化为词面检索，不影响主流程

### 2.7 前端（KbManage.vue 追加）

卡片头部加「批量导入」：选默认 scope（行内 scope 留空时用它）→ 上传 → 预检表格（行号/scope/问题/状态/原因）→ 确认导入 → 统计 + 失败明细。

### 2.8 取舍

- 不加导入审计表：追溯靠 §2.6 的归档文件；P1 要做界面化追溯再加 `ai_kb_import_log`
- 向量索引进程内存：多实例各自懒加载，可接受

---

## 3. AI 生图

### 3.1 为什么要

电商文案助手只出文字，运营做商品主图/营销海报/banner 仍然要美工。接入图像生成模型，和文案共用硅基流动账号（`SILICONFLOW_API_KEY` 已有），零新增供应商。

### 3.2 场景与模型

| 场景 | 用途 | 尺寸 |
|---|---|---|
| 商品主图 | 按商品名+卖点生成白底/场景图 | 1024×1024 |
| 详情图 | 插入 `body` 富文本，与 §1 的详情文案配套 | 1024×1024 |
| 营销海报 | 活动宣传图，含文案排版 | 1024×1536 |
| Banner | 首页轮播 | 按 image-spec 的 banner 规格 |

模型（已拍板）：默认 `Kwai-Kolors/Kolors`（中文电商图质量更好）；`black-forest-labs/FLUX.1-schnell` 作为前端「快速模式」可选项（更快更便宜）。走 `/v1/images/generations`（OpenAI 兼容接口）。

**前置条件已确认**：硅基流动账号已开通图像模型；Python 与 Java 同机/共享 `upload/` 目录，生成图可直接落盘被前端访问。

### 3.3 接口（`app/api/ai_chat.py` 追加）

```
POST /api/admin/ai/image/generate
权限: ai:copy:generate
入参: { productId?, prompt, scene, size?, count=1 }
出参: { images: [{ url }] }
```

- prompt 组装：商品场景自动拼「商品名 + 卖点 + 电商摄影风格」，也允许运营完全自定义；`productId` 选填——**新增商品没有 ID 也能生图**（用内联名称/卖点，与 §1.3 同口径）
- 生成图下载后落盘到 `upload/image/ai/` 目录（复用 Java `UploadServiceImpl` 的目录约定；Python 侧直接写文件 + 拼 url，不进 upload 接口——它不是表单上传）
- **Java 白名单前置（必改）**：`InternalAiController` 的 `CopyUpdateRequest` 加 `image` 字段（映射 `products.image`），否则「设为主图」无法回填；详情图插入 body 不受影响（body 已在白名单）
- 返回可访问 url，前端预览后：「设为主图」（回填 `products.image`，走 update_product 链路）/「插入详情」（给富文本编辑器用，新增商品时直接插进 AddGoods 的 Editor）/「下载」/「重新生成」

### 3.4 前端

- **AddGoods.vue 内嵌生图（主路径）**：主图上传区旁加「AI 生成主图」，详情编辑器工具栏加「AI 生成详情图」，原地生成 → 预览 → 一键采用，不跳转、不绕行
  - **Editor.vue 前置小改（已核对）**：wangEditor 组件当前无 `defineExpose`，需暴露 editor 实例（或封装 `insertHtml/insertImage`），父组件才能调 wangEditor 原生 `dangerouslyInsertHtml()` 插入详情图/文案
- CopyAssistant.vue 加 Tab：文案 / 图片（独立工具，供非加商品场景使用）。图片 Tab：prompt 输入 + 尺寸 + 生成 → 图片网格预览 → 「设为主图」/「下载」/「重新生成」
- 速率限制：同一运营每分钟 ≤ 5 次。已有 `app/services/rate_limit.py` 的 `check_and_consume(user_id)`（Redis 固定窗口，已连通）可复用结构，但窗口参数走 settings 固定值，**需加一个带窗口/次数参数的 `check_and_consume_with_window(user_id, window_seconds, limit)`**

### 3.5 风险

- 生成内容合规：prompt 加通用电商安全约束（不含人物肖像、品牌 logo 描述过滤）
- 成本：每次生成写 `ai_call_log`（scene=`generate_image`，记 model/尺寸/耗时），方便核算
- 权限：复用 `ai:copy:generate`（已核对：`admin_menu` id=1002 + `ai_tool` id=8 + admin/operator/service 角色授权均已就位），生图接口不新增权限编码

---

## 4. 存量商品详情纯图无文字 · 补全方案

### 4.1 现状

`products.body` 是富文本 HTML，但存量商品基本都是图片堆砌（`<img>` 串联），没有文字段落。前端商品详情页直接渲染 body，结果整页只有图：没有卖点文案、没有参数说明，搜索/SEO/用户阅读体验都差。

分三层处理：**存量批量补、新增防再发、前端兜底**。

### 4.2 存量：AI 批量回填营销文案（P1，后续再跑）

> 状态：2026-11-09 已用非 AI 脚本 `scripts/backfill_body_text.py` 把 137 个商品补了「主标题+副标题+参数」文字块（带 `data-bf-text` 标记，生产 SQL 见 `docs/sql/20261109_product_body_text_backfill.sql`）。
> 已拍板**保留本节作为 P1 增强**：脚本补的是参数罗列，后续用 AI 换成更营销化的详情段落。

新增管理端批量任务「详情文案升级」（`POST /api/admin/ai/copy/backfill-body`，权限 `ai:copy:generate`）：

```
入参: { productIds?: [], 缺省=全部命中商品, dryRun: true|false }
流程:
  1) 扫描 products，筛选条件：body 不含 data-ai-text 标记（即还没被 AI 升级过的）
     ——注意不能再按「纯文本<30字」筛：脚本补完后所有商品都有文字了
  2) 逐个调 §1 的 description 场景（name + subTitle + category 拼 prompt）生成营销段落
  3) 组装新 body：AI 段落插在脚本参数块之后、图片之前，带 data-ai-text="1" 标记；
     脚本块与原有 <img> 全部保留不动
  4) dryRun=true 只返回预览（productId/生成文案），不写库
  5) dryRun=false 走 update_product 已有链路批量更新，写 ai_call_log（scene=backfill_body）
前端: CopyAssistant 加 Tab「详情升级」→ 列出命中商品 → 预览生成结果 → 勾选确认批量回填
```

- 幂等：`data-ai-text` 标记，重复执行自动跳过，不重复插入
- 限量：单次 ≤50 个，防 LLM 批量跑飞；失败单个跳过不影响整体
- 成本：批量调 LLM 有费用，必须先 dryRun 预览再确认

### 4.3 新增：提交校验防再发

- `AddGoods.vue` / `EditModal.vue` 提交商品时校验：body 去标签纯文本 < 30 字 → 弹警告「商品详情缺少文字描述，建议补充」（**警告不拦截**，运营可继续提交）
- 配合 §1.3 的「AI 生成」按钮，新增页就能顺手生成详情文案，从源头杜绝纯图商品

### 4.4 前端兜底展示（不在本期）

商城详情页「无文字时补 title/subTitle/参数表」的兜底属于 C 端改动，超出本期范围（见顶部范围声明）。且存量数据已由脚本修复（§4.2 状态注），所有商品都有文字块，兜底非必需。如需做，另立 C 端迭代。

---

## 5. 验收清单

- [ ] Java `InternalAiController` 白名单已扩 `title` + `image`（不动其它字段），内部 Token 鉴权不受影响
- [ ] 文案场景为 name / subTitle / title / description / all；title ≤30 字回填 `products.title`；description 回填 `products.body`
- [ ] AI 助手对话里调 generate_copy（无论新旧 scene key）都能正常生成
- [ ] 新增商品无 ID 也能生成文案（内联模式），AddGoods 页可一键填入表单
- [ ] 新增商品无 ID 也能生图，图片可直接插入详情富文本
- [ ] 详情补全 dryRun 预览不写库；确认回填后原 `<img>` 全部保留、头部插入文字段落
- [ ] 重复执行补全任务不重复插入（幂等）；新增/编辑商品提交时纯图详情有警告提示
- [ ] DDL 已执行（生产库同步）：唯一键变为 `(scope, category, question)`，两库同名问答互不覆盖
- [ ] 模板可下载（7 列含 scope）；改表头/缺列/加列的文件上传即 400，且库内数据不变
- [ ] Excel 按模板一次导入 ≥100 条；scope 列可混导两个库、非法 scope 行失败、留空走默认；重复条目按 upsert 覆盖更新且 `hit_count` 保留；导入失败库内不变
- [ ] CLI（`scripts/import_kb.py`）与接口共用 `app/kb/importer.py`，同一份文件两条路径导入结果一致
- [ ] 上传文件归档到 `upload/excel/kb/`，preview/confirm 返回 importFileId
- [ ] 导入完成后新条目语义检索立即可命中（不重启，AI 读的是 `ai_kb_qa` 表）
- [ ] 生图返回可访问 url，可回填主图；每分钟限流生效
