#!/usr/bin/env python3
"""
回填 product_sku_spec_rel：从 product_sku.spec_json 解析规格组合，
匹配 product_spec_group / product_spec_value，写入中间表。
字典里没有但 SKU 里有记录的值会自动补建 spec_value。
"""
import json
import mysql.connector
import sys

HOST, PORT = "127.0.0.1", 3306
USER, PWD, DB = "e_commerce_db", "123456", "e_commerce_db"


def main():
    conn = mysql.connector.connect(host=HOST, port=PORT, user=USER, password=PWD, database=DB)
    cur = conn.cursor()

    # 1. DDL（幂等）
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS product_sku_spec_rel (
          sku_id          BIGINT       NOT NULL COMMENT 'product_sku.id',
          spec_group_id   BIGINT       NOT NULL COMMENT 'product_spec_group.id',
          spec_value_id   BIGINT                DEFAULT NULL COMMENT 'product_spec_value.id',
          spec_value      VARCHAR(64)  NOT NULL COMMENT '冗余规格值字符串',
          sort            INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
          create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (sku_id, spec_group_id),
          KEY idx_rel_value (spec_value_id),
          KEY idx_rel_group (spec_group_id),
          KEY idx_rel_sku_value (sku_id, spec_value_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """
    )
    cur.execute("TRUNCATE TABLE product_sku_spec_rel")

    # 2. 预加载字典 { group_name: group_id }  + 预加载 { (group_id, value): value_id }
    cur.execute("SELECT id, name FROM product_spec_group WHERE status = 1")
    name_to_gid = {name: gid for gid, name in cur.fetchall()}

    cur.execute("SELECT id, group_id, value FROM product_spec_value")
    gid_val_to_vid = {(gid, val): vid for vid, gid, val in cur.fetchall()}

    # 3. 遍历 SKU
    cur.execute(
        "SELECT id, spec_json, COALESCE(create_time, NOW()) "
        "FROM product_sku "
        "WHERE spec_json IS NOT NULL AND spec_json <> '' AND spec_json <> '{}' AND spec_key <> '__DEFAULT__'"
    )
    sku_rows = cur.fetchall()
    print(f"待回填 SKU 数: {len(sku_rows)}")

    insert_sql = (
        "INSERT INTO product_sku_spec_rel (sku_id, spec_group_id, spec_value_id, spec_value, sort, create_time) "
        "VALUES (%s, %s, %s, %s, %s, %s) "
        "ON DUPLICATE KEY UPDATE spec_value_id = VALUES(spec_value_id), spec_value = VALUES(spec_value)"
    )

    stat = {"ok": 0, "group_missing": 0, "new_value_created": 0, "total_rows": 0}

    for sku_id, spec_json, create_time in sku_rows:
        try:
            spec_map = json.loads(spec_json) if spec_json else {}
        except Exception as e:
            print(f"[WARN] sku_id={sku_id} spec_json 解析失败: {e}, 原始={spec_json!r}")
            continue

        row_batch = []
        for gname, gvalue in spec_map.items():
            gid = name_to_gid.get(gname)
            if gid is None:
                stat["group_missing"] += 1
                print(f"[WARN] sku_id={sku_id} 规格名 '{gname}' 在字典中不存在，跳过")
                continue

            vid = gid_val_to_vid.get((gid, gvalue))
            if vid is None:
                # 自动补建 spec_value
                cur.execute(
                    "INSERT INTO product_spec_value (group_id, value, sort, create_time) "
                    "VALUES (%s, %s, 999, NOW())",
                    (gid, gvalue),
                )
                vid = cur.lastrowid
                gid_val_to_vid[(gid, gvalue)] = vid
                stat["new_value_created"] += 1
                print(f"[INFO] 补建 spec_value: group={gid} value='{gvalue}' -> id={vid}")

            # sort 用 group.sort
            cur.execute("SELECT sort FROM product_spec_group WHERE id = %s", (gid,))
            (sort,) = cur.fetchone() or (999,)

            row_batch.append((sku_id, gid, vid, gvalue, sort, create_time))

        if row_batch:
            cur.executemany(insert_sql, row_batch)
            stat["ok"] += 1
            stat["total_rows"] += len(row_batch)

        # 每 50 条 commit 一次
        if sku_id % 50 == 0:
            conn.commit()

    conn.commit()

    # 4. 汇总
    cur.execute("SELECT COUNT(*) FROM product_sku_spec_rel")
    (total_rel,) = cur.fetchone()
    cur.execute("SELECT COUNT(DISTINCT sku_id) FROM product_sku_spec_rel")
    (cover,) = cur.fetchone()
    cur.execute("SELECT COUNT(*) FROM product_sku_spec_rel WHERE spec_value_id IS NULL")
    (no_vid,) = cur.fetchone()
    print(f"回填完成")
    print(f"  成功 SKU: {stat['ok']}, 跳过(规格名缺失): {stat['group_missing']}, 补建 spec_value: {stat['new_value_created']}")
    print(f"  rel 总行数: {total_rel}, 覆盖 SKU: {cover}, spec_value_id 缺失: {no_vid}")

    cur.close()
    conn.close()


if __name__ == "__main__":
    sys.exit(main())
