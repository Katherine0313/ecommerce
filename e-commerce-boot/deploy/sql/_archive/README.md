# 历史迁移脚本归档

本目录下的脚本**均已执行完毕，不可重复运行**。它们依赖已被删除的
`product_sku.spec_json` / `product_sku.spec_key` 字段，再执行会报
`Unknown column`。

| 脚本 | 用途 | 执行环境 | 状态 |
|---|---|---|---|
| `20261125_sku_spec_rel.sql` | 建中间表 + 导出待回填清单 | MySQL 8.0 | ✅ 已完成 |
| `migrate_sku_spec_rel.sh` | 从 spec_json 回填中间表（生产用） | MySQL 5.7 | ✅ 已完成 |
| `migrate_sku_spec_rel.py` | 从 spec_json 回填中间表（Python 版） | MySQL 5.7 | ✅ 已完成 |

## 当前有效迁移脚本

请使用上一级目录中的脚本：

- `20261125_add_is_default.sql` — 新增 `product_sku.is_default` 字段（两个库都已执行）
- `20261125_migrate_sku_spec_rel_mysql8.sql` — 中间表回填（MySQL 8.0 / 本地库）

## SKU 规格存储演进

```
旧：product_sku.spec_json (json) + spec_key (varchar) + UNIQUE(product_id, spec_key)
新：product_sku_spec_rel (sku_id, spec_group_id, spec_value_id, spec_value, sort)
    + product_sku.is_default（单规格商品的默认 SKU 标记）
```

规格选择统一由中间表承载；`sku_code` 回归"商家编码"单一语义。
