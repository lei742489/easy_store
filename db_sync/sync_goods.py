import argparse
import shutil
import sqlite3
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

SOURCE_TABLE = "products"
TARGET_ROOT_CATEGORY_ID = 1

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


def decimal_value(value) -> Decimal:
    if value is None or str(value).strip() == "":
        return Decimal("0")
    return Decimal(str(value))


def money_value(value) -> float:
    return float(decimal_value(value).quantize(Decimal("0.0001"), ROUND_HALF_UP))


def integer_stock(value) -> tuple[int, bool]:
    raw = decimal_value(value)
    rounded = int(raw.quantize(Decimal("1"), ROUND_HALF_UP))
    return rounded, raw != Decimal(rounded)


def backup_target(db_path: Path, suffix: str) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_{suffix}_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source_table(conn: sqlite3.Connection) -> None:
    columns = conn.execute(f'PRAGMA table_info("{SOURCE_TABLE}")').fetchall()
    print(f"source table: {SOURCE_TABLE}")
    print("source columns:")
    for col in columns:
        print(f"  - {col['name']}: {col['type']}")
    print("field mapping:")
    print("  products.id       -> app_goods.id")
    print("  products.name     -> app_goods.title")
    print("  products.pinyin   -> app_goods.py_code")
    print("  products.code     -> app_goods.goods_code")
    print("  products.ptype_id -> app_goods.category_id")
    print("  products.unit     -> app_goods.unit")
    print("  products.cur_stock-> app_goods.stock/init_stock")
    print("  products.last_prc -> app_goods.cost_price/stock_cost basis")
    print("  products.pur_prc  -> app_goods.pur_prc")
    print("  products.sale_prc -> app_goods.sale_prc")
    print("  products.trade_prc-> app_goods.trade_prc")
    print("  products.is_stop  -> app_goods.status")


def load_supplier_titles(conn: sqlite3.Connection) -> dict[int, str]:
    if not table_exists(conn, "product_suppliers"):
        return {}
    rows = conn.execute(
        """
        SELECT product_id, name
        FROM product_suppliers
        WHERE COALESCE(is_del, 0) = 0
        ORDER BY id
        """
    ).fetchall()
    titles: dict[int, str] = {}
    for row in rows:
        product_id = int(row["product_id"] or 0)
        if product_id and product_id not in titles:
            titles[product_id] = row["name"] or ""
    return titles


def load_source_goods(
    conn: sqlite3.Connection,
    include_stopped: bool,
) -> list[sqlite3.Row]:
    if not table_exists(conn, SOURCE_TABLE):
        raise RuntimeError(f"source table not found: {SOURCE_TABLE}")

    stopped_filter = "" if include_stopped else "AND COALESCE(is_stop, 0) = 0"
    return conn.execute(
        f"""
        SELECT id, name, pinyin, code, unit, ptype_id, pur_prc, sale_prc,
               trade_prc, cur_stock, init_stock, init_prc, last_prc,
               min_stock, max_stock, remark, is_stop, create_at, revise_at
        FROM products
        WHERE COALESCE(is_del, 0) = 0
        {stopped_filter}
        ORDER BY name, id
        """
    ).fetchall()


def load_target_category_ids(conn: sqlite3.Connection) -> set[int]:
    rows = conn.execute(
        "SELECT id FROM app_goods_category WHERE COALESCE(is_del, 0) = 0"
    ).fetchall()
    return {int(row[0]) for row in rows}


def normalize_goods(
    rows: list[sqlite3.Row],
    supplier_titles: dict[int, str],
    target_category_ids: set[int],
) -> tuple[list[dict], list[str], list[str]]:
    goods_list: list[dict] = []
    warnings: list[str] = []
    unit_names: set[str] = set()

    for row in rows:
        goods_id = int(row["id"])
        category_id = int(row["ptype_id"] or TARGET_ROOT_CATEGORY_ID)
        if category_id not in target_category_ids:
            warnings.append(
                f"goods id={goods_id} category id={category_id} missing, use root"
            )
            category_id = TARGET_ROOT_CATEGORY_ID

        stock, rounded = integer_stock(row["cur_stock"])
        if rounded:
            warnings.append(
                f"goods id={goods_id} stock {row['cur_stock']} rounded to {stock}"
            )

        min_stock, min_rounded = integer_stock(row["min_stock"])
        max_stock, max_rounded = integer_stock(row["max_stock"])
        if min_rounded:
            warnings.append(
                f"goods id={goods_id} min_stock {row['min_stock']} rounded to {min_stock}"
            )
        if max_rounded:
            warnings.append(
                f"goods id={goods_id} max_stock {row['max_stock']} rounded to {max_stock}"
            )

        cost_price = money_value(row["last_prc"])
        init_cost, _ = integer_stock(row["last_prc"])
        stock_cost = float(
            (Decimal(stock) * Decimal(str(cost_price))).quantize(
                Decimal("0.01"), ROUND_HALF_UP
            )
        )
        unit = (row["unit"] or "").strip()
        if unit:
            unit_names.add(unit)

        goods_list.append(
            {
                "id": goods_id,
                "title": row["name"] or "",
                "py_code": row["pinyin"] or "",
                "supplier_title": supplier_titles.get(goods_id) or row["name"] or "",
                "category_id": category_id,
                "goods_code": row["code"] or "",
                "img_url": "",
                "init_cost": init_cost,
                "init_stock": stock,
                "stock": stock,
                "unit": unit,
                "sale_prc": money_value(row["sale_prc"]),
                "trade_prc": money_value(row["trade_prc"]),
                "pur_prc": money_value(row["pur_prc"]),
                "max_stock": max_stock,
                "min_stock": min_stock,
                "supplier_id": None,
                "note": row["remark"] or "",
                "status": 0 if int(row["is_stop"] or 0) == 1 else 1,
                "create_time": normalize_datetime(row["create_at"]),
                "update_time": normalize_datetime(row["revise_at"]),
                "is_del": 0,
                "stock_cost": stock_cost,
                "cost_price": cost_price,
            }
        )

    return goods_list, warnings, sorted(unit_names)


def ensure_target_tables(conn: sqlite3.Connection) -> None:
    for table in ("app_goods", "app_purchase_order_item", "app_goods_category"):
        if not table_exists(conn, table):
            raise RuntimeError(f"target table not found: {table}")


def import_units(conn: sqlite3.Connection, unit_names: list[str]) -> None:
    if not table_exists(conn, "app_unit"):
        return
    for name in unit_names:
        conn.execute(
            "INSERT INTO app_unit (name) SELECT ? WHERE NOT EXISTS "
            "(SELECT 1 FROM app_unit WHERE name = ?)",
            (name, name),
        )
    reset_sequence(conn, "app_unit")


def import_goods(
    conn: sqlite3.Connection,
    goods_list: list[dict],
    replace: bool,
    sync_init_stock: bool,
    unit_names: list[str],
) -> None:
    ensure_target_tables(conn)
    import_units(conn, unit_names)

    if replace:
        incoming_ids = [item["id"] for item in goods_list]
        if incoming_ids:
            placeholders = ",".join("?" for _ in incoming_ids)
            conn.execute(
                f"UPDATE app_goods SET is_del = 1 WHERE id NOT IN ({placeholders})",
                incoming_ids,
            )
            if sync_init_stock:
                conn.execute(
                    "UPDATE app_purchase_order_item SET is_del = 1 "
                    f"WHERE COALESCE(is_init, 0) = 1 AND goods_id NOT IN ({placeholders})",
                    incoming_ids,
                )

    existing_delta = load_existing_stock_delta(conn) if sync_init_stock else {}
    for item in goods_list:
        upsert_goods(conn, item)
        if sync_init_stock:
            target_stock = int(item["stock"] or 0)
            init_quantity = target_stock - int(existing_delta.get(item["id"], 0))
            upsert_init_stock_item(conn, item, init_quantity)

    reset_sequence(conn, "app_goods")
    reset_sequence(conn, "app_purchase_order_item")


def upsert_goods(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_goods
        SET title = :title,
            py_code = :py_code,
            supplier_title = :supplier_title,
            category_id = :category_id,
            goods_code = :goods_code,
            img_url = :img_url,
            init_cost = :init_cost,
            init_stock = :init_stock,
            stock = :stock,
            unit = :unit,
            sale_prc = :sale_prc,
            trade_prc = :trade_prc,
            pur_prc = :pur_prc,
            max_stock = :max_stock,
            min_stock = :min_stock,
            supplier_id = :supplier_id,
            note = :note,
            status = :status,
            create_time = :create_time,
            update_time = :update_time,
            is_del = :is_del,
            stock_cost = :stock_cost,
            cost_price = :cost_price
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return

    conn.execute(
        """
        INSERT INTO app_goods
            (id, title, py_code, supplier_title, category_id, goods_code,
             img_url, init_cost, init_stock, stock, unit, sale_prc, trade_prc,
             pur_prc, max_stock, min_stock, supplier_id, note, status,
             create_time, update_time, is_del, stock_cost, cost_price)
        VALUES
            (:id, :title, :py_code, :supplier_title, :category_id, :goods_code,
             :img_url, :init_cost, :init_stock, :stock, :unit, :sale_prc,
             :trade_prc, :pur_prc, :max_stock, :min_stock, :supplier_id,
             :note, :status, :create_time, :update_time, :is_del,
             :stock_cost, :cost_price)
        """,
        item,
    )


def load_existing_stock_delta(conn: sqlite3.Connection) -> dict[int, int]:
    delta: dict[int, int] = {}
    rows = conn.execute(
        """
        SELECT i.goods_id, SUM(i.quantity) AS quantity
        FROM app_purchase_order_item i
        LEFT JOIN app_purchase_order o ON o.id = i.order_id AND o.is_del = 0
        WHERE i.is_del = 0
          AND COALESCE(i.is_init, 0) <> 1
          AND (i.order_id IS NULL OR o.status = 1)
        GROUP BY i.goods_id
        """
    ).fetchall()
    for row in rows:
        add_delta(delta, row["goods_id"], row["quantity"])

    rows = conn.execute(
        """
        SELECT i.goods_id, -SUM(i.quantity) AS quantity
        FROM app_sale_order_item i
        INNER JOIN app_sale_order o ON o.id = i.order_id
            AND o.is_del = 0 AND o.status = 1
        WHERE i.is_del = 0
        GROUP BY i.goods_id
        """
    ).fetchall()
    for row in rows:
        add_delta(delta, row["goods_id"], row["quantity"])

    if table_exists(conn, "app_stock_check_item") and table_exists(conn, "app_stock_check"):
        rows = conn.execute(
            """
            SELECT i.goods_id, SUM(i.profit_loss_quantity) AS quantity
            FROM app_stock_check_item i
            INNER JOIN app_stock_check o ON o.id = i.check_id AND o.is_del = 0
            WHERE i.is_del = 0
            GROUP BY i.goods_id
            """
        ).fetchall()
        for row in rows:
            add_delta(delta, row["goods_id"], row["quantity"])

    return delta


def add_delta(delta: dict[int, int], goods_id, quantity) -> None:
    if goods_id is None:
        return
    key = int(goods_id)
    rounded, _ = integer_stock(quantity)
    delta[key] = delta.get(key, 0) + rounded


def upsert_init_stock_item(conn: sqlite3.Connection, item: dict, quantity: int) -> None:
    total_amount = float(
        (Decimal(quantity) * Decimal(str(item["cost_price"]))).quantize(
            Decimal("0.01"), ROUND_HALF_UP
        )
    )
    payload = {
        "goods_id": item["id"],
        "category_id": item["category_id"],
        "unit": item["unit"],
        "quantity": quantity,
        "unit_price": item["cost_price"],
        "total_amount": total_amount,
        "note": "init from sync_goods",
        "create_time": item["create_time"],
        "update_time": item["update_time"],
        "is_init": 1,
        "is_del": 0,
    }
    cursor = conn.execute(
        """
        UPDATE app_purchase_order_item
        SET category_id = :category_id,
            unit = :unit,
            quantity = :quantity,
            unit_price = :unit_price,
            total_amount = :total_amount,
            order_id = NULL,
            note = :note,
            create_time = :create_time,
            update_time = :update_time,
            is_init = :is_init,
            is_del = :is_del
        WHERE goods_id = :goods_id AND COALESCE(is_init, 0) = 1
        """,
        payload,
    )
    if cursor.rowcount:
        return

    conn.execute(
        """
        INSERT INTO app_purchase_order_item
            (goods_id, category_id, unit, quantity, unit_price, total_amount,
             order_id, note, create_time, update_time, is_init, is_del)
        VALUES
            (:goods_id, :category_id, :unit, :quantity, :unit_price,
             :total_amount, NULL, :note, :create_time, :update_time,
             :is_init, :is_del)
        """,
        payload,
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


def print_preview(goods_list: list[dict], warnings: list[str], unit_names: list[str]) -> None:
    print(f"goods to import: {len(goods_list)}")
    print(f"units to ensure: {len(unit_names)}")
    print("first 30 rows:")
    for row in goods_list[:30]:
        print(
            f"  id={row['id']:<5} stock={row['stock']:<5} "
            f"category={row['category_id']:<4} unit={row['unit']:<4} "
            f"pur={row['pur_prc']:<8} sale={row['sale_prc']:<8} "
            f"trade={row['trade_prc']:<8} title={row['title']}"
        )
    if len(goods_list) > 30:
        print(f"  ... {len(goods_list) - 30} more")
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")
        if len(warnings) > 50:
            print(f"  ... {len(warnings) - 50} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync goods from ZHJ products to Easy Store."
    )
    parser.add_argument("--source", type=Path, default=SOURCE_DB)
    parser.add_argument("--target", type=Path, default=TARGET_DB)
    parser.add_argument(
        "--execute",
        action="store_true",
        help="write changes to target database; without this flag it only previews",
    )
    parser.add_argument(
        "--append",
        action="store_true",
        help="keep existing target goods and upsert imported rows",
    )
    parser.add_argument(
        "--active-only",
        action="store_true",
        help="skip stopped source goods; default imports them as disabled",
    )
    parser.add_argument(
        "--no-init-stock",
        action="store_true",
        help="do not update app_purchase_order_item init stock rows",
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
        inspect_source_table(source_conn)
        rows = load_source_goods(source_conn, include_stopped=not args.active_only)
        supplier_titles = load_supplier_titles(source_conn)

    with connect_target(target) as target_conn:
        target_category_ids = load_target_category_ids(target_conn)

    goods_list, warnings, unit_names = normalize_goods(
        rows,
        supplier_titles,
        target_category_ids,
    )
    print_preview(goods_list, warnings, unit_names)
    print(f"target db: {target}")

    if not args.execute:
        print("dry-run only. add --execute to import.")
        return

    if not args.no_backup:
        backup_path = backup_target(target, "goods")
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_goods(
                target_conn,
                goods_list,
                replace=not args.append,
                sync_init_stock=not args.no_init_stock,
                unit_names=unit_names,
            )
        total = target_conn.execute(
            "SELECT COUNT(1) FROM app_goods WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]
        disabled = target_conn.execute(
            "SELECT COUNT(1) FROM app_goods WHERE COALESCE(is_del, 0) = 0 "
            "AND COALESCE(status, 1) <> 1"
        ).fetchone()[0]

    print(f"import complete. active target goods: {total}, disabled: {disabled}")


if __name__ == "__main__":
    main()
