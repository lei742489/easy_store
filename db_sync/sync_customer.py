import argparse
import shutil
import sqlite3
import sys
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

SOURCE_TABLE = "companies"
CUSTOMER_TYPE = 1
UNCATEGORIZED_CATEGORY_ID = 1
UNCATEGORIZED_CATEGORY_TITLE = "\u672a\u5206\u7c7b"
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


def require_target_tables(conn: sqlite3.Connection) -> None:
    for table_name in ("app_customer", "app_customer_category"):
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


def decimal_value(value) -> Decimal:
    if value is None or str(value).strip() == "":
        return Decimal("0")
    return Decimal(str(value))


def money(value) -> float:
    return float(decimal_value(value).quantize(Decimal("0.01"), ROUND_HALF_UP))


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_customer_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    columns = conn.execute(f'PRAGMA table_info("{SOURCE_TABLE}")').fetchall()
    print(f"source table: {SOURCE_TABLE}")
    print("field mapping:")
    print("  companies.id        -> app_customer.id")
    print("  companies.name      -> app_customer.name")
    print("  companies.pinyin    -> app_customer.py_code")
    print("  companies.linkman   -> app_customer.contact_name")
    print("  companies.mobile/tel-> app_customer.mobile/phone")
    print("  companies.cur_amt   -> app_customer.payable")
    print("  companies.init_amt  -> app_customer.def_payable")
    print("  companies.disc * 100-> app_customer.discount")
    print(
        f"  fixed category      -> app_customer.category_id={UNCATEGORIZED_CATEGORY_ID}"
    )
    print(f"source columns: {', '.join(row['name'] for row in columns)}")


def load_source_customers(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    if not table_exists(conn, SOURCE_TABLE):
        raise RuntimeError(f"source table not found: {SOURCE_TABLE}")

    return conn.execute(
        """
        SELECT id, name, pinyin, linkman, mobile, tel, mail, postcode,
               birthday, addr, qq, remark, init_amt, cur_amt, disc, is_stop,
               create_at, revise_at
        FROM companies
        WHERE COALESCE(is_del, 0) = 0
          AND tye = ?
        ORDER BY id
        """,
        (CUSTOMER_TYPE,),
    ).fetchall()


def normalize_customers(rows: list[sqlite3.Row]) -> list[dict]:
    customers: list[dict] = []
    for row in rows:
        customers.append(
            {
                "id": int(row["id"]),
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
                "category_id": UNCATEGORIZED_CATEGORY_ID,
                "level_id": 0,
                "status": 0 if int(row["is_stop"] or 0) else 1,
                "discount": money(decimal_value(row["disc"]) * Decimal("100")),
                "create_time": normalize_datetime(row["create_at"]),
                "py_code": row["pinyin"] or "",
                "payable": money(row["cur_amt"]),
                "def_payable": money(row["init_amt"]),
                "is_del": 0,
            }
        )
    return customers


def upsert_uncategorized_category(conn: sqlite3.Connection) -> None:
    category = {
        "id": UNCATEGORIZED_CATEGORY_ID,
        "title": UNCATEGORIZED_CATEGORY_TITLE,
        "parent_id": 0,
        "root": 0,
        "create_time": current_timestamp(),
        "is_del": 0,
    }
    cursor = conn.execute(
        """
        UPDATE app_customer_category
        SET title = :title,
            parent_id = :parent_id,
            root = :root,
            create_time = :create_time,
            is_del = :is_del
        WHERE id = :id
        """,
        category,
    )
    if cursor.rowcount:
        return

    conn.execute(
        """
        INSERT INTO app_customer_category
            (id, title, parent_id, root, create_time, is_del)
        VALUES
            (:id, :title, :parent_id, :root, :create_time, :is_del)
        """,
        category,
    )


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


def reset_sequence(conn: sqlite3.Connection, table_name: str) -> None:
    if not table_exists(conn, "sqlite_sequence"):
        return
    row = conn.execute(f"SELECT MAX(id) FROM {table_name}").fetchone()
    max_id = int(row[0] or 0)
    conn.execute(
        "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
        (table_name, max_id),
    )


def import_customers(conn: sqlite3.Connection, customers: list[dict]) -> None:
    require_target_tables(conn)
    upsert_uncategorized_category(conn)
    for customer in customers:
        upsert_customer(conn, customer)
    reset_sequence(conn, "app_customer_category")
    reset_sequence(conn, "app_customer")


def print_preview(customers: list[dict]) -> None:
    print(f"customers to import: {len(customers)}")
    print(
        f"customer category: id={UNCATEGORIZED_CATEGORY_ID} "
        f"title={UNCATEGORIZED_CATEGORY_TITLE}"
    )
    print("first 30 rows:")
    for customer in customers[:30]:
        print(
            f"  id={customer['id']:<5} status={customer['status']} "
            f"discount={customer['discount']:<6} payable={customer['payable']:<10} "
            f"name={customer['name']}"
        )
    if len(customers) > 30:
        print(f"  ... {len(customers) - 30} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync customers from ZHJ companies to Easy Store."
    )
    parser.add_argument("--source", type=Path, default=SOURCE_DB)
    parser.add_argument("--target", type=Path, default=TARGET_DB)
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
        source_customers = load_source_customers(source_conn)
    customers = normalize_customers(source_customers)
    print_preview(customers)
    print(f"target db: {target}")

    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_customers(target_conn, customers)
        customer_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_customer WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]
        category = target_conn.execute(
            """
            SELECT id, title, is_del
            FROM app_customer_category
            WHERE id = ?
            """,
            (UNCATEGORIZED_CATEGORY_ID,),
        ).fetchone()

    print(
        f"import complete. active customers: {customer_total}; "
        f"category id={category['id']} title={category['title']} is_del={category['is_del']}"
    )


if __name__ == "__main__":
    main()
