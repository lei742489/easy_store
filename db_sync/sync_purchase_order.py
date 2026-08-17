import argparse
import shutil
import sqlite3
import sys
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
PURCHASE_ITEM_ID_OFFSET = 1_000_000

DATETIME_FORMATS = (
    "%Y-%m-%d %H:%M:%S.%f",
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%d",
)


def configure_stdout() -> None:
    try:
        sys.stdout.reconfigure(errors="backslashreplace")
    except AttributeError:
        pass


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
    backup_path = BACKUP_DIR / f"easy_store_before_purchase_order_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    print("source tables:")
    for table_name in ("purs", "puritems", "products", "companies", "accts"):
        count = conn.execute(f"SELECT COUNT(1) FROM {table_name}").fetchone()[0]
        print(f"  - {table_name}: {count}")
    print("field mapping:")
    print("  purs             -> app_purchase_order")
    print("  puritems         -> app_purchase_order_item")
    print("  purs.company_id  -> app_supplier.id")
    print("  purs.acct_id     -> app_account_settle.id")
    print("  old employees    -> ignored; cashier always uses target ROOT user")
    print(
        "  puritems.id      -> app_purchase_order_item.id "
        f"({PURCHASE_ITEM_ID_OFFSET} + source id)"
    )


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
               deduction_amt, disc, pay_amt, owe_amt, express_amt, remark,
               acct_id, create_at, revise_at, is_calc
        FROM purs
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
    clauses = ["COALESCE(p.is_del, 0) = 0", "COALESCE(i.is_del, 0) = 0"]
    params: list[str] = []
    if start_date:
        clauses.append("p.opt_on >= ?")
        params.append(start_date)
    if end_date:
        clauses.append("p.opt_on <= ?")
        params.append(end_date)

    return conn.execute(
        f"""
        SELECT i.id, i.pur_id, i.product_id, i.prc, i.qty, i.amt, i.remark,
               p.opt_on, p.create_at, p.revise_at
        FROM puritems i
        INNER JOIN purs p ON p.id = i.pur_id
        WHERE {' AND '.join(clauses)}
        ORDER BY i.pur_id, i.id
        """,
        params,
    ).fetchall()


def load_target_goods(conn: sqlite3.Connection) -> dict[int, dict]:
    rows = conn.execute(
        """
        SELECT id, category_id, unit
        FROM app_goods
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {
        int(row["id"]): {
            "category_id": int(row["category_id"] or 1),
            "unit": (row["unit"] or "").strip(),
        }
        for row in rows
    }


def load_target_supplier_ids(conn: sqlite3.Connection) -> set[int]:
    rows = conn.execute(
        """
        SELECT id
        FROM app_supplier
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {int(row["id"]) for row in rows}


def load_target_account_ids(conn: sqlite3.Connection) -> set[int]:
    rows = conn.execute(
        """
        SELECT id
        FROM app_account_settle
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {int(row["id"]) for row in rows}


def load_target_root_user(conn: sqlite3.Connection) -> dict | None:
    row = conn.execute(
        """
        SELECT id, user_name, real_name
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


def normalize_orders(
    orders: list[sqlite3.Row],
    items_by_order: dict[int, list[sqlite3.Row]],
    target_goods: dict[int, dict],
    supplier_ids: set[int],
    account_ids: set[int],
    root_user: dict,
) -> tuple[list[dict], list[dict], list[str]]:
    normalized_orders: list[dict] = []
    normalized_items: list[dict] = []
    warnings: list[str] = []

    for row in orders:
        order_id = int(row["id"])
        supplier_id = int(row["company_id"] or 0)
        if supplier_id not in supplier_ids:
            warnings.append(
                f"order id={order_id} supplier id={supplier_id} missing"
            )
            continue

        item_rows = items_by_order.get(order_id, [])
        if not item_rows:
            warnings.append(f"order id={order_id} has no active items")
            continue

        settle_id = None
        source_settle_id = int(row["acct_id"] or 0)
        if source_settle_id:
            if source_settle_id in account_ids:
                settle_id = source_settle_id
            else:
                warnings.append(
                    f"order id={order_id} settle account id={source_settle_id} missing"
                )

        order_items: list[dict] = []
        for item_row in item_rows:
            goods_id = int(item_row["product_id"] or 0)
            goods = target_goods.get(goods_id)
            if goods is None:
                warnings.append(
                    f"purchase item id={item_row['id']} goods id={goods_id} missing"
                )
                continue

            quantity, rounded = integer_quantity(item_row["qty"])
            if rounded:
                warnings.append(
                    f"purchase item id={item_row['id']} quantity "
                    f"{item_row['qty']} rounded to {quantity}"
                )
            order_items.append(
                {
                    "id": PURCHASE_ITEM_ID_OFFSET + int(item_row["id"]),
                    "goods_id": goods_id,
                    "category_id": int(goods["category_id"] or 1),
                    "unit": goods["unit"],
                    "quantity": quantity,
                    "unit_price": money(item_row["prc"]),
                    "total_amount": money(item_row["amt"]),
                    "order_id": order_id,
                    "note": item_row["remark"] or "",
                    "create_time": bill_datetime(
                        item_row["opt_on"], item_row["create_at"]
                    ),
                    "update_time": normalize_datetime(item_row["revise_at"]),
                    "is_init": 0,
                    "is_del": 0,
                }
            )

        if not order_items:
            warnings.append(f"order id={order_id} has no importable items")
            continue

        total_amount = money(row["bill_amt"])
        discounted_amount = money(row["disc_amt"])
        freight_amount = money(row["express_amt"])
        payable_amount = money(
            decimal_value(discounted_amount) + decimal_value(freight_amount)
        )
        paid_amount = money(row["pay_amt"])
        unpaid_amount = money(row["owe_amt"])
        source_settlement_amount = money(
            decimal_value(paid_amount) + decimal_value(unpaid_amount)
        )
        if abs(payable_amount - source_settlement_amount) > 0.01:
            warnings.append(
                f"order id={order_id} payable {payable_amount} differs from "
                f"paid + unpaid {source_settlement_amount}"
            )

        normalized_orders.append(
            {
                "id": order_id,
                "order_no": row["code"] or f"JHD-SOURCE-{order_id}",
                "supplier_id": supplier_id,
                "order_type": 2 if int(row["tye"] or 1) == 2 else 1,
                "settle_id": settle_id,
                "cashier_id": root_user["id"],
                "cashier_name": root_user["name"],
                "status": 1 if int(row["is_calc"] or 0) == 1 else 0,
                "total_amount": total_amount,
                "payable_amount": payable_amount,
                "paid_amount": paid_amount,
                "unpaid_amount": unpaid_amount,
                "freight_amount": freight_amount,
                "discounted_amount": discounted_amount,
                "discount_rate": money(
                    decimal_value(row["disc"]) * Decimal("100")
                ),
                "note": row["remark"] or "",
                "create_time": bill_datetime(row["opt_on"], row["create_at"]),
                "update_time": normalize_datetime(row["revise_at"]),
                "is_del": 0,
            }
        )
        normalized_items.extend(order_items)

    return normalized_orders, normalized_items, warnings


def ensure_no_order_conflicts(conn: sqlite3.Connection, orders: list[dict]) -> None:
    for order in orders:
        row = conn.execute(
            "SELECT order_no, is_del FROM app_purchase_order WHERE id = ?",
            (order["id"],),
        ).fetchone()
        if (
            row is not None
            and int(row["is_del"] or 0) == 0
            and row["order_no"] != order["order_no"]
        ):
            raise RuntimeError(
                f"target purchase order id={order['id']} conflicts with "
                f"order_no={row['order_no']}"
            )


def upsert_order(conn: sqlite3.Connection, order: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_purchase_order
        SET order_no = :order_no,
            supplier_id = :supplier_id,
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
            is_del = :is_del
        WHERE id = :id
        """,
        order,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_purchase_order
            (id, order_no, supplier_id, order_type, settle_id, cashier_id,
             cashier_name, status, total_amount, payable_amount, paid_amount,
             unpaid_amount, freight_amount, discounted_amount, discount_rate,
             note, create_time, update_time, is_del)
        VALUES
            (:id, :order_no, :supplier_id, :order_type, :settle_id,
             :cashier_id, :cashier_name, :status, :total_amount,
             :payable_amount, :paid_amount, :unpaid_amount, :freight_amount,
             :discounted_amount, :discount_rate, :note, :create_time,
             :update_time, :is_del)
        """,
        order,
    )


def upsert_item(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_purchase_order_item
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
            is_init = :is_init,
            is_del = :is_del
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_purchase_order_item
            (id, goods_id, category_id, unit, quantity, unit_price, total_amount,
             order_id, note, create_time, update_time, is_init, is_del)
        VALUES
            (:id, :goods_id, :category_id, :unit, :quantity, :unit_price,
             :total_amount, :order_id, :note, :create_time, :update_time,
             :is_init, :is_del)
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


def import_purchase_orders(
    conn: sqlite3.Connection,
    orders: list[dict],
    items: list[dict],
) -> None:
    require_tables(
        conn,
        (
            "app_supplier",
            "app_goods",
            "app_account_settle",
            "app_purchase_order",
            "app_purchase_order_item",
            "app_sale_order",
            "app_sale_order_item",
            "app_stock_check",
            "app_stock_check_item",
        ),
    )
    ensure_no_order_conflicts(conn, orders)
    for order in orders:
        upsert_order(conn, order)
    for item in items:
        upsert_item(conn, item)
    rebuild_init_stock(conn, {int(item["goods_id"]) for item in items})
    reset_sequence(conn, "app_purchase_order")
    reset_sequence(conn, "app_purchase_order_item")


def print_preview(
    root_user: dict,
    orders: list[dict],
    items: list[dict],
    warnings: list[str],
) -> None:
    print(f"target ROOT cashier: id={root_user['id']} name={root_user['name']}")
    print(f"purchase orders to import: {len(orders)}")
    print(f"purchase items to import: {len(items)}")
    print("latest 10 orders:")
    for order in orders[-10:]:
        print(
            f"  id={order['id']:<6} no={order['order_no']:<22} "
            f"type={order['order_type']} supplier={order['supplier_id']:<4} "
            f"total={order['total_amount']:<10} freight={order['freight_amount']:<8} "
            f"paid={order['paid_amount']:<10} unpaid={order['unpaid_amount']}"
        )
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")
        if len(warnings) > 50:
            print(f"  ... {len(warnings) - 50} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync purchase orders from ZHJ purs/puritems to Easy Store."
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
        "--no-backup",
        action="store_true",
        help="skip target database backup when executing",
    )
    return parser.parse_args()


def main() -> None:
    configure_stdout()
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

    items_by_order: dict[int, list[sqlite3.Row]] = defaultdict(list)
    for item in source_items:
        items_by_order[int(item["pur_id"])].append(item)

    with connect_target(target) as target_conn:
        target_goods = load_target_goods(target_conn)
        supplier_ids = load_target_supplier_ids(target_conn)
        account_ids = load_target_account_ids(target_conn)
        root_user = load_target_root_user(target_conn)
    if root_user is None:
        raise RuntimeError("no active ROOT user found in target app_user")

    orders, items, warnings = normalize_orders(
        source_orders,
        items_by_order,
        target_goods,
        supplier_ids,
        account_ids,
        root_user,
    )
    print_preview(root_user, orders, items, warnings)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_purchase_orders(target_conn, orders, items)
        order_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_purchase_order WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]
        item_total = target_conn.execute(
            """
            SELECT COUNT(1)
            FROM app_purchase_order_item
            WHERE COALESCE(is_del, 0) = 0 AND COALESCE(is_init, 0) <> 1
            """
        ).fetchone()[0]
    print(
        f"import complete. active purchase orders: {order_total}, "
        f"historical items: {item_total}"
    )


if __name__ == "__main__":
    main()
