import sqlite3
from pprint import pprint


DB = r"D:\Workspaces\2025\easy_store_boot\easy_store_boot\db\easy_store.db"
NAME = "测试商品1"


def q(conn, sql, params=()):
    conn.row_factory = sqlite3.Row
    return [dict(row) for row in conn.execute(sql, params).fetchall()]


def main():
    conn = sqlite3.connect(DB)
    patterns = ["测试", "商品", "测试商品", "商品1", "测试商品1"]
    print("PATTERN COUNTS:")
    for key in patterns:
        count = conn.execute(
            "select count(*) from app_goods where title like ?",
            (f"%{key}%",),
        ).fetchone()[0]
        print(key, count)
    test_rows = q(
        conn,
        "select id, title, stock, stock_cost, cost_price, pur_prc, init_cost from app_goods where title like ? order by id",
        ("%测试%",),
    )
    print("TEST-LIKE GOODS:")
    for row in test_rows:
        pretty = dict(row)
        pretty["title"] = pretty["title"].encode("unicode_escape").decode("ascii")
        print(pretty)

    goods = q(
        conn,
        """
        select id, title, stock, stock_cost, cost_price, pur_prc, sale_prc, init_stock, init_cost
        from app_goods
        where title like ?
        order by id
        """,
        (f"%{NAME}%",),
    )
    print("GOODS:")
    pprint(goods)

    if not goods:
        return

    goods_id = goods[0]["id"]
    print("\nPURCHASE ITEMS:")
    pprint(
        q(
            conn,
            """
            select poi.id, poi.order_id, o.order_no, o.create_time, poi.quantity, poi.unit_price, poi.total_amount
            from app_purchase_order_item poi
            left join app_purchase_order o on o.id = poi.order_id
            where poi.goods_id = ?
            order by o.create_time, poi.id
            """,
            (goods_id,),
        )
    )

    print("\nSALE ITEMS:")
    pprint(
        q(
            conn,
            """
            select soi.id, soi.order_id, o.order_no, o.create_time, soi.quantity, soi.unit_price, soi.total_amount, soi.cost_price, soi.gross_profit
            from app_sale_order_item soi
            left join app_sale_order o on o.id = soi.order_id
            where soi.goods_id = ?
            order by o.create_time, soi.id
            """,
            (goods_id,),
        )
    )

    print("\nCOST ADJUSTMENTS:")
    pprint(
        q(
            conn,
            """
            select *
            from app_sale_cost_adjustment
            where goods_id = ?
            order by create_time, id
            """,
            (goods_id,),
        )
    )


if __name__ == "__main__":
    main()
