import argparse
import shutil
import sqlite3
from collections import defaultdict
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path


SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
TARGET_DB = (
    Path(__file__).resolve().parents[1]
    / "easy_store_boot"
    / "db"
    / "easy_store.db"
)
BACKUP_DIR = Path(__file__).resolve().parent / "backups"
RETAIL_CUSTOMER_ID = 2
UNCATEGORIZED_CUSTOMER_CATEGORY_ID = 1

DATETIME_FORMATS = (
    "%Y-%m-%d %H:%M:%S.%f",
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%d",
)


def connect_readonly(db_path: Path) -> sqlite3.Connection:
    uri = f"file:{db_path.as_posix()}?mode=ro"
    conn = sqlite3.connect(uri, uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def connect_target(db_path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn


def require_file(db_path: Path, label: str) -> None:
    if not db_path.exists():
        raise FileNotFoundError(f"{label} database not found: {db_path}")


def table_exists(conn: sqlite3.Connection, table_name: str) -> bool:
    row = conn.execute(
        "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = ?",
        (table_name,),
    ).fetchone()
    return bool(row and row[0])


def require_tables(conn: sqlite3.Connection, table_names: tuple[str, ...]) -> None:
    for table_name in table_names:
        if not table_exists(conn, table_name):
            raise RuntimeError(f"target table not found: {table_name}")


def current_timestamp() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def normalize_datetime(value) -> str:
    if value is None:
        return current_timestamp()

    text = str(value).strip().replace("T", " ")
    if not text:
        return current_timestamp()
    if text.endswith("Z"):
        text = text[:-1].strip()

    for date_format in DATETIME_FORMATS:
        try:
            parsed = datetime.strptime(text, date_format)
            return parsed.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        except ValueError:
            continue

    if "." not in text and len(text) == 19:
        return f"{text}.000"
    return text


def bill_datetime(opt_on, create_at) -> str:
    bill_date = str(opt_on or "").strip()
    if len(bill_date) == 10:
        return f"{bill_date} 00:00:00.000"
    return normalize_datetime(create_at)


def decimal_value(value) -> Decimal:
    if value is None or str(value).strip() == "":
        return Decimal("0")
    return Decimal(str(value))


def money(value) -> float:
    return float(decimal_value(value).quantize(Decimal("0.01"), ROUND_HALF_UP))


def integer_quantity(value) -> tuple[int, bool]:
    raw = decimal_value(value)
    rounded = int(raw.quantize(Decimal("1"), ROUND_HALF_UP))
    return rounded, raw != Decimal(rounded)


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_sale_order_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    print("source tables:")
    for table_name in ("sales", "saleitems", "companies", "accts", "stocks"):
        count = conn.execute(f"SELECT COUNT(1) FROM {table_name}").fetchone()[0]
        print(f"  - {table_name}: {count}")
    print("field mapping:")
    print("  sales            -> app_sale_order")
    print("  saleitems        -> app_sale_order_item")
    print("  companies        -> app_customer (sales dependencies)")
    print("  sales.acct_id    -> app_account_settle.id")
    print("  sales.username   -> ignored; cashier always uses target ROOT user")
    print("  stocks.cost_amt  -> historical item/order gross profit")


def load_source_orders(
    conn: sqlite3.Connection,
    start_date: str | None,
    end_date: str | None,
) -> list[sqlite3.Row]:
    clauses = ["COALESCE(is_del, 0) = 0"]
    params: list[str] = []
    if start_date:
        clauses.append("opt_on >= ?")
        params.append(start_date)
    if end_date:
        clauses.append("opt_on <= ?")
        params.append(end_date)

    return conn.execute(
        f"""
        SELECT id, opt_on, code, tye, company_id, bill_amt, disc_amt,
               deduction_amt, disc, pay_amt, owe_amt, change_amt, express_amt,
               remark, user_id, username, acct_id, create_at, revise_at, is_calc
        FROM sales
        WHERE {' AND '.join(clauses)}
        ORDER BY id
        """,
        params,
    ).fetchall()


def load_source_items(
    conn: sqlite3.Connection,
    start_date: str | None,
    end_date: str | None,
) -> list[sqlite3.Row]:
    clauses = ["COALESCE(s.is_del, 0) = 0", "COALESCE(i.is_del, 0) = 0"]
    params: list[str] = []
    if start_date:
        clauses.append("s.opt_on >= ?")
        params.append(start_date)
    if end_date:
        clauses.append("s.opt_on <= ?")
        params.append(end_date)

    return conn.execute(
        f"""
        SELECT i.id, i.sale_id, i.product_id, i.prc, i.qty, i.amt,
               i.remark, p.ptype_id, p.unit AS goods_unit,
               COALESCE(st.cost_amt, 0) AS cost_amt
        FROM saleitems i
        INNER JOIN sales s ON s.id = i.sale_id
        LEFT JOIN products p ON p.id = i.product_id
        LEFT JOIN (
            SELECT item_id, SUM(cost_amt) AS cost_amt
            FROM stocks
            WHERE COALESCE(is_del, 0) = 0 AND tye = 3
            GROUP BY item_id
        ) st ON st.item_id = i.id
        WHERE {' AND '.join(clauses)}
        ORDER BY i.sale_id, i.id
        """,
        params,
    ).fetchall()


def load_source_customers(
    conn: sqlite3.Connection,
    customer_ids: set[int],
) -> dict[int, sqlite3.Row]:
    if not customer_ids:
        return {}
    placeholders = ",".join("?" for _ in customer_ids)
    rows = conn.execute(
        f"""
        SELECT id, name, pinyin, linkman, mobile, tel, mail, postcode,
               birthday, addr, qq, remark, init_amt, cur_amt, disc, is_stop,
               create_at
        FROM companies
        WHERE COALESCE(is_del, 0) = 0 AND id IN ({placeholders})
        """,
        sorted(customer_ids),
    ).fetchall()
    return {int(row["id"]): row for row in rows}


def load_target_goods(conn: sqlite3.Connection) -> dict[int, dict]:
    rows = conn.execute(
        """
        SELECT id, category_id, unit, cost_price
        FROM app_goods
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {
        int(row["id"]): {
            "category_id": int(row["category_id"] or 1),
            "unit": (row["unit"] or "").strip(),
            "cost_price": money(row["cost_price"]),
        }
        for row in rows
    }


def load_target_account_ids(conn: sqlite3.Connection) -> set[int]:
    rows = conn.execute(
        """
        SELECT id
        FROM app_account_settle
        WHERE COALESCE(is_del, 0) = 0
        ORDER BY id
        """
    ).fetchall()
    return {int(row["id"]) for row in rows}


def load_target_root_user(conn: sqlite3.Connection) -> dict | None:
    row = conn.execute(
        """
        SELECT id, user_name, real_name, is_root
        FROM app_user
        WHERE COALESCE(is_del, 0) = 0
          AND COALESCE(status, 1) = 1
          AND COALESCE(is_root, 0) = 1
        ORDER BY id
        LIMIT 1
        """
    ).fetchone()
    if row is None:
        return None
    return {
        "id": int(row["id"]),
        "name": (row["real_name"] or row["user_name"] or "").strip(),
    }


def target_customer_ids(conn: sqlite3.Connection) -> set[int]:
    rows = conn.execute(
        "SELECT id FROM app_customer WHERE COALESCE(is_del, 0) = 0"
    ).fetchall()
    return {int(row[0]) for row in rows}


def normalize_customers(rows: dict[int, sqlite3.Row]) -> list[dict]:
    customers: list[dict] = []
    for customer_id, row in rows.items():
        customers.append(
            {
                "id": customer_id,
                "name": row["name"] or "",
                "contact_name": row["linkman"] or "",
                "mobile": row["mobile"] or "",
                "phone": row["tel"] or "",
                "mail": row["mail"] or "",
                "postal": row["postcode"] or "",
                "birthday": row["birthday"] or "",
                "address": row["addr"] or "",
                "qq": row["qq"] or "",
                "note": row["remark"] or "",
                "category_id": UNCATEGORIZED_CUSTOMER_CATEGORY_ID,
                "level_id": 0,
                "status": 0 if int(row["is_stop"] or 0) == 1 else 1,
                "discount": money(decimal_value(row["disc"]) * Decimal("100")),
                "create_time": normalize_datetime(row["create_at"]),
                "py_code": row["pinyin"] or "",
                "payable": money(row["cur_amt"]),
                "def_payable": money(row["init_amt"]),
                "is_del": 0,
            }
        )
    return customers


def normalize_orders(
    orders: list[sqlite3.Row],
    target_account_ids: set[int],
    source_customers: dict[int, sqlite3.Row],
    target_goods: dict[int, dict],
    root_user: dict,
    items_by_order: dict[int, list[sqlite3.Row]],
) -> tuple[list[dict], list[dict], list[str], set[int]]:
    normalized_orders: list[dict] = []
    normalized_items: list[dict] = []
    warnings: list[str] = []
    customer_ids: set[int] = set()
    for row in orders:
        source_order_id = int(row["id"])
        source_customer_id = int(row["company_id"] or 0)
        customer_id = source_customer_id or RETAIL_CUSTOMER_ID
        if customer_id not in source_customers:
            warnings.append(
                f"order id={source_order_id} customer id={customer_id} missing"
            )
            continue

        settle_id = None
        source_settle_id = int(row["acct_id"] or 0)
        if source_settle_id:
            if source_settle_id in target_account_ids:
                settle_id = source_settle_id
            else:
                warnings.append(
                    f"order id={source_order_id} settle account id={source_settle_id} missing"
                )

        item_rows = items_by_order.get(source_order_id, [])
        if not item_rows:
            warnings.append(f"order id={source_order_id} has no active items")
            continue

        discount_rate = money(decimal_value(row["disc"]) * Decimal("100"))
        item_cost_amount = Decimal("0")
        valid_item_count = 0
        for item_row in item_rows:
            goods_id = int(item_row["product_id"] or 0)
            goods = target_goods.get(goods_id)
            if goods is None:
                warnings.append(
                    f"sale item id={item_row['id']} goods id={goods_id} missing"
                )
                continue

            quantity, rounded = integer_quantity(item_row["qty"])
            if rounded:
                warnings.append(
                    f"sale item id={item_row['id']} quantity {item_row['qty']} rounded to {quantity}"
                )
            cost_amount = decimal_value(item_row["cost_amt"])
            if cost_amount == 0 and quantity != 0:
                cost_amount = (
                    Decimal(quantity) * decimal_value(goods["cost_price"])
                ).quantize(Decimal("0.01"), ROUND_HALF_UP)

            item_amount = decimal_value(item_row["amt"])
            gross_profit = (
                item_amount
                * decimal_value(discount_rate)
                / Decimal("100")
                - cost_amount
            )
            item_cost_amount += cost_amount
            normalized_items.append(
                {
                    "id": int(item_row["id"]),
                    "goods_id": goods_id,
                    "category_id": int(goods["category_id"] or 1),
                    "unit": goods["unit"] or (item_row["goods_unit"] or "").strip(),
                    "quantity": quantity,
                    "unit_price": money(item_row["prc"]),
                    "total_amount": money(item_row["amt"]),
                    "order_id": source_order_id,
                    "note": item_row["remark"] or "",
                    "create_time": bill_datetime(row["opt_on"], row["create_at"]),
                    "update_time": normalize_datetime(row["revise_at"]),
                    "is_del": 0,
                    "gross_profit": money(gross_profit),
                }
            )
            valid_item_count += 1

        if valid_item_count == 0:
            warnings.append(f"order id={source_order_id} has no importable items")
            continue

        paid_amount = money(row["pay_amt"])
        unpaid_amount = money(row["owe_amt"])
        payable_amount = money(decimal_value(paid_amount) + decimal_value(unpaid_amount))
        discounted_amount = money(row["disc_amt"])
        exact_gross_profit = money(
            decimal_value(discounted_amount) - item_cost_amount
        )

        normalized_orders.append(
            {
                "id": source_order_id,
                "order_no": row["code"] or f"XSD-SOURCE-{source_order_id}",
                "customer_id": customer_id,
                "order_type": 2 if int(row["tye"] or 1) == 2 else 1,
                "settle_id": settle_id,
                "cashier_id": root_user["id"],
                "cashier_name": root_user["name"],
                "status": 1 if int(row["is_calc"] or 0) == 1 else 0,
                "total_amount": money(row["bill_amt"]),
                "payable_amount": payable_amount,
                "paid_amount": paid_amount,
                "unpaid_amount": unpaid_amount,
                "freight_amount": money(row["express_amt"]),
                "discounted_amount": discounted_amount,
                "discount_rate": discount_rate,
                "note": row["remark"] or "",
                "create_time": bill_datetime(row["opt_on"], row["create_at"]),
                "update_time": normalize_datetime(row["revise_at"]),
                "is_del": 0,
                "gross_profit": exact_gross_profit,
            }
        )
        customer_ids.add(customer_id)

    return normalized_orders, normalized_items, warnings, customer_ids


def upsert_customer(conn: sqlite3.Connection, customer: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_customer
        SET name = :name,
            contact_name = :contact_name,
            mobile = :mobile,
            phone = :phone,
            mail = :mail,
            postal = :postal,
            birthday = :birthday,
            address = :address,
            qq = :qq,
            note = :note,
            category_id = :category_id,
            level_id = :level_id,
            status = :status,
            discount = :discount,
            create_time = :create_time,
            py_code = :py_code,
            payable = :payable,
            def_payable = :def_payable,
            is_del = :is_del
        WHERE id = :id
        """,
        customer,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_customer
            (id, name, contact_name, mobile, phone, mail, postal, birthday,
             address, qq, note, category_id, level_id, status, discount,
             create_time, py_code, payable, def_payable, is_del)
        VALUES
            (:id, :name, :contact_name, :mobile, :phone, :mail, :postal,
             :birthday, :address, :qq, :note, :category_id, :level_id,
             :status, :discount, :create_time, :py_code, :payable,
             :def_payable, :is_del)
        """,
        customer,
    )


def ensure_no_order_conflicts(conn: sqlite3.Connection, orders: list[dict]) -> None:
    for order in orders:
        row = conn.execute(
            "SELECT order_no, is_del FROM app_sale_order WHERE id = ?",
            (order["id"],),
        ).fetchone()
        if row is not None and int(row["is_del"] or 0) == 0 and row["order_no"] != order["order_no"]:
            raise RuntimeError(
                f"target sale order id={order['id']} conflicts with order_no={row['order_no']}"
            )


def upsert_order(conn: sqlite3.Connection, order: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_sale_order
        SET order_no = :order_no,
            customer_id = :customer_id,
            order_type = :order_type,
            settle_id = :settle_id,
            cashier_id = :cashier_id,
            cashier_name = :cashier_name,
            status = :status,
            total_amount = :total_amount,
            payable_amount = :payable_amount,
            paid_amount = :paid_amount,
            unpaid_amount = :unpaid_amount,
            freight_amount = :freight_amount,
            discounted_amount = :discounted_amount,
            discount_rate = :discount_rate,
            note = :note,
            create_time = :create_time,
            update_time = :update_time,
            is_del = :is_del,
            gross_profit = :gross_profit
        WHERE id = :id
        """,
        order,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_sale_order
            (id, order_no, customer_id, order_type, settle_id, cashier_id,
             cashier_name, status, total_amount, payable_amount, paid_amount,
             unpaid_amount, freight_amount, discounted_amount, discount_rate,
             note, create_time, update_time, is_del, gross_profit)
        VALUES
            (:id, :order_no, :customer_id, :order_type, :settle_id,
             :cashier_id, :cashier_name, :status, :total_amount,
             :payable_amount, :paid_amount, :unpaid_amount, :freight_amount,
             :discounted_amount, :discount_rate, :note, :create_time,
             :update_time, :is_del, :gross_profit)
        """,
        order,
    )


def upsert_item(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_sale_order_item
        SET goods_id = :goods_id,
            category_id = :category_id,
            unit = :unit,
            quantity = :quantity,
            unit_price = :unit_price,
            total_amount = :total_amount,
            order_id = :order_id,
            note = :note,
            create_time = :create_time,
            update_time = :update_time,
            is_del = :is_del,
            gross_profit = :gross_profit
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_sale_order_item
            (id, goods_id, category_id, unit, quantity, unit_price,
             total_amount, order_id, note, create_time, update_time, is_del,
             gross_profit)
        VALUES
            (:id, :goods_id, :category_id, :unit, :quantity, :unit_price,
             :total_amount, :order_id, :note, :create_time, :update_time,
             :is_del, :gross_profit)
        """,
        item,
    )


def reset_sequence(conn: sqlite3.Connection, table_name: str) -> None:
    if not table_exists(conn, "sqlite_sequence"):
        return
    row = conn.execute(f"SELECT MAX(id) FROM {table_name}").fetchone()
    max_id = int(row[0] or 0)
    conn.execute(
        "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
        (table_name, max_id),
    )


def rebuild_init_stock(conn: sqlite3.Connection, goods_ids: set[int]) -> None:
    if not goods_ids:
        return
    placeholders = ",".join("?" for _ in goods_ids)
    goods_rows = conn.execute(
        f"""
        SELECT id, stock
        FROM app_goods
        WHERE COALESCE(is_del, 0) = 0 AND id IN ({placeholders})
        """,
        sorted(goods_ids),
    ).fetchall()
    stocks = {int(row["id"]): int(row["stock"] or 0) for row in goods_rows}

    purchase_delta = load_quantity_map(
        conn,
        f"""
        SELECT i.goods_id, SUM(i.quantity) AS quantity
        FROM app_purchase_order_item i
        LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0
        WHERE i.is_del = 0
          AND COALESCE(i.is_init, 0) <> 1
          AND (i.order_id IS NULL OR o.status = 1)
          AND i.goods_id IN ({placeholders})
        GROUP BY i.goods_id
        """,
        sorted(goods_ids),
    )
    sale_delta = load_quantity_map(
        conn,
        f"""
        SELECT i.goods_id, SUM(i.quantity) AS quantity
        FROM app_sale_order_item i
        INNER JOIN app_sale_order o ON o.id = i.order_id
            AND o.is_del = 0 AND o.status = 1
        WHERE i.is_del = 0 AND i.goods_id IN ({placeholders})
        GROUP BY i.goods_id
        """,
        sorted(goods_ids),
    )
    stock_check_delta = load_quantity_map(
        conn,
        f"""
        SELECT i.goods_id, SUM(i.profit_loss_quantity) AS quantity
        FROM app_stock_check_item i
        INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0
        WHERE i.is_del = 0 AND i.goods_id IN ({placeholders})
        GROUP BY i.goods_id
        """,
        sorted(goods_ids),
    )

    for goods_id, stock in stocks.items():
        init_quantity = (
            stock
            + sale_delta.get(goods_id, 0)
            - purchase_delta.get(goods_id, 0)
            - stock_check_delta.get(goods_id, 0)
        )
        cursor = conn.execute(
            """
            UPDATE app_purchase_order_item
            SET quantity = ?
            WHERE goods_id = ? AND COALESCE(is_init, 0) = 1
            """,
            (init_quantity, goods_id),
        )
        if cursor.rowcount == 0:
            raise RuntimeError(
                f"init stock item missing for goods id={goods_id}; run sync_goods.py first"
            )


def load_quantity_map(
    conn: sqlite3.Connection,
    sql: str,
    params: list[int],
) -> dict[int, int]:
    rows = conn.execute(sql, params).fetchall()
    quantity_map: dict[int, int] = {}
    for row in rows:
        quantity, rounded = integer_quantity(row["quantity"])
        if rounded:
            raise RuntimeError(
                f"target stock quantity for goods id={row['goods_id']} is non-integer"
            )
        quantity_map[int(row["goods_id"])] = quantity
    return quantity_map


def import_sales(
    conn: sqlite3.Connection,
    customers: list[dict],
    orders: list[dict],
    items: list[dict],
    create_customers: bool,
) -> None:
    require_tables(
        conn,
        (
            "app_customer",
            "app_goods",
            "app_sale_order",
            "app_sale_order_item",
            "app_purchase_order_item",
            "app_purchase_order",
            "app_stock_check",
            "app_stock_check_item",
        ),
    )
    ensure_no_order_conflicts(conn, orders)
    if create_customers:
        for customer in customers:
            upsert_customer(conn, customer)
        reset_sequence(conn, "app_customer")

    for order in orders:
        upsert_order(conn, order)
    for item in items:
        upsert_item(conn, item)

    rebuild_init_stock(conn, {int(item["goods_id"]) for item in items})
    reset_sequence(conn, "app_sale_order")
    reset_sequence(conn, "app_sale_order_item")


def print_preview(
    customers: list[dict],
    orders: list[dict],
    items: list[dict],
    warnings: list[str],
) -> None:
    print(f"customers involved: {len(customers)}")
    print(f"sale orders to import: {len(orders)}")
    print(f"sale items to import: {len(items)}")
    print("latest 10 orders:")
    for order in orders[-10:]:
        print(
            f"  id={order['id']:<6} no={order['order_no']:<22} "
            f"type={order['order_type']} customer={order['customer_id']:<4} "
            f"total={order['total_amount']:<10} paid={order['paid_amount']:<10} "
            f"unpaid={order['unpaid_amount']:<10} gross={order['gross_profit']}"
        )
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")
        if len(warnings) > 50:
            print(f"  ... {len(warnings) - 50} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync sales orders from ZHJ sales/saleitems to Easy Store."
    )
    parser.add_argument("--source", type=Path, default=SOURCE_DB)
    parser.add_argument("--target", type=Path, default=TARGET_DB)
    parser.add_argument("--from-date", dest="start_date")
    parser.add_argument("--to-date", dest="end_date")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="write changes to target database; without this flag it only previews",
    )
    parser.add_argument(
        "--no-create-customers",
        action="store_true",
        help="require sale customers to already exist in target app_customer",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="skip target database backup when executing",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source = args.source.resolve()
    target = args.target.resolve()
    require_file(source, "source")
    require_file(target, "target")

    with connect_readonly(source) as source_conn:
        inspect_source(source_conn)
        source_orders = load_source_orders(
            source_conn, args.start_date, args.end_date
        )
        source_items = load_source_items(
            source_conn, args.start_date, args.end_date
        )
        source_customer_ids = {
            int(row["company_id"] or RETAIL_CUSTOMER_ID)
            for row in source_orders
        }
        source_customer_ids.add(RETAIL_CUSTOMER_ID)
        source_customers = load_source_customers(source_conn, source_customer_ids)

    items_by_order: dict[int, list[sqlite3.Row]] = defaultdict(list)
    for item in source_items:
        items_by_order[int(item["sale_id"])].append(item)

    with connect_target(target) as target_conn:
        target_goods = load_target_goods(target_conn)
        target_account_ids = load_target_account_ids(target_conn)
        root_user = load_target_root_user(target_conn)
        active_customer_ids = target_customer_ids(target_conn)
    if root_user is None:
        raise RuntimeError("no active ROOT user found in target app_user")

    customers = normalize_customers(source_customers)
    orders, items, warnings, referenced_customer_ids = normalize_orders(
        source_orders,
        target_account_ids,
        source_customers,
        target_goods,
        root_user,
        items_by_order,
    )
    missing_customer_ids = referenced_customer_ids - active_customer_ids
    if missing_customer_ids and args.no_create_customers:
        warnings.append(
            f"{len(missing_customer_ids)} sale customers are missing in target"
        )

    print_preview(customers, orders, items, warnings)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if args.no_create_customers and missing_customer_ids:
        raise RuntimeError(
            "target customer dependencies are missing; remove --no-create-customers "
            "or import customers first"
        )
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_sales(
                target_conn,
                customers,
                orders,
                items,
                create_customers=not args.no_create_customers,
            )
        order_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_sale_order WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]
        item_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_sale_order_item WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]
    print(f"import complete. active sale orders: {order_total}, items: {item_total}")


if __name__ == "__main__":
    main()
