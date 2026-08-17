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
SUPPLIER_TYPE = 2
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
    backup_path = BACKUP_DIR / f"easy_store_before_supplier_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    columns = conn.execute(f'PRAGMA table_info("{SOURCE_TABLE}")').fetchall()
    print(f"source table: {SOURCE_TABLE}")
    print("field mapping:")
    print("  companies.id        -> app_supplier.id")
    print("  companies.name      -> app_supplier.name")
    print("  companies.pinyin    -> app_supplier.py_code")
    print("  companies.linkman   -> app_supplier.contact_name")
    print("  companies.mobile/tel-> app_supplier.mobile/phone")
    print("  companies.cur_amt   -> app_supplier.payable")
    print("  companies.init_amt  -> app_supplier.def_payable")
    print("source columns: " + ", ".join(row["name"] for row in columns))


def load_source_suppliers(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    if not table_exists(conn, SOURCE_TABLE):
        raise RuntimeError(f"source table not found: {SOURCE_TABLE}")

    return conn.execute(
        """
        SELECT id, name, pinyin, linkman, mobile, tel, mail, postcode,
               addr, qq, remark, init_amt, cur_amt, is_stop, create_at, revise_at
        FROM companies
        WHERE COALESCE(is_del, 0) = 0
          AND tye = ?
        ORDER BY id
        """,
        (SUPPLIER_TYPE,),
    ).fetchall()


def normalize_suppliers(rows: list[sqlite3.Row]) -> list[dict]:
    suppliers: list[dict] = []
    for row in rows:
        suppliers.append(
            {
                "id": int(row["id"]),
                "name": row["name"] or "",
                "py_code": row["pinyin"] or "",
                "contact_name": row["linkman"] or "",
                "mobile": row["mobile"] or "",
                "phone": row["tel"] or "",
                "mail": row["mail"] or "",
                "postal": row["postcode"] or "",
                "address": row["addr"] or "",
                "qq": row["qq"] or "",
                "status": 0 if int(row["is_stop"] or 0) else 1,
                "note": row["remark"] or "",
                "payable": money(row["cur_amt"]),
                "def_payable": money(row["init_amt"]),
                "create_time": normalize_datetime(row["create_at"]),
                "is_del": 0,
            }
        )
    return suppliers


def upsert_supplier(conn: sqlite3.Connection, supplier: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_supplier
        SET name = :name,
            py_code = :py_code,
            contact_name = :contact_name,
            mobile = :mobile,
            phone = :phone,
            mail = :mail,
            postal = :postal,
            address = :address,
            qq = :qq,
            status = :status,
            note = :note,
            payable = :payable,
            def_payable = :def_payable,
            create_time = :create_time,
            is_del = :is_del
        WHERE id = :id
        """,
        supplier,
    )
    if cursor.rowcount:
        return

    conn.execute(
        """
        INSERT INTO app_supplier
            (id, name, py_code, contact_name, mobile, phone, mail, postal,
             address, qq, status, note, payable, def_payable, create_time, is_del)
        VALUES
            (:id, :name, :py_code, :contact_name, :mobile, :phone, :mail,
             :postal, :address, :qq, :status, :note, :payable, :def_payable,
             :create_time, :is_del)
        """,
        supplier,
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


def import_suppliers(conn: sqlite3.Connection, suppliers: list[dict]) -> None:
    if not table_exists(conn, "app_supplier"):
        raise RuntimeError("target table not found: app_supplier")
    for supplier in suppliers:
        upsert_supplier(conn, supplier)
    reset_sequence(conn, "app_supplier")


def print_preview(suppliers: list[dict]) -> None:
    print(f"suppliers to import: {len(suppliers)}")
    print("first 30 rows:")
    for supplier in suppliers[:30]:
        print(
            f"  id={supplier['id']:<5} status={supplier['status']} "
            f"payable={supplier['payable']:<10} name={supplier['name']}"
        )
    if len(suppliers) > 30:
        print(f"  ... {len(suppliers) - 30} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync suppliers from ZHJ companies to Easy Store."
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
        source_suppliers = load_source_suppliers(source_conn)
    suppliers = normalize_suppliers(source_suppliers)
    print_preview(suppliers)
    print(f"target db: {target}")

    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_suppliers(target_conn, suppliers)
        supplier_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_supplier WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]

    print(f"import complete. active suppliers: {supplier_total}")


if __name__ == "__main__":
    main()
