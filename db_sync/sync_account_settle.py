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
SOURCE_TABLE = "accts"


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


def decimal_value(value) -> Decimal:
    if value is None or str(value).strip() == "":
        return Decimal("0")
    return Decimal(str(value))


def money(value) -> float:
    return float(decimal_value(value).quantize(Decimal("0.01"), ROUND_HALF_UP))


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_account_settle_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    columns = conn.execute(f'PRAGMA table_info("{SOURCE_TABLE}")').fetchall()
    print(f"source table: {SOURCE_TABLE}")
    print("field mapping:")
    print("  accts.id        -> app_account_settle.id")
    print("  accts.name      -> app_account_settle.name")
    print("  accts.tye       -> app_account_settle.type_id")
    print("  accts.bank_name -> app_account_settle.bank_name")
    print("  accts.bank_code -> app_account_settle.bank_card")
    print("  accts.init_amt  -> app_account_settle.init_prc")
    print("  accts.cur_amt   -> app_account_settle.cur_prc")
    print("  accts.remark    -> app_account_settle.note")
    print("source columns: " + ", ".join(row["name"] for row in columns))


def load_source_accounts(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    if not table_exists(conn, SOURCE_TABLE):
        raise RuntimeError(f"source table not found: {SOURCE_TABLE}")
    return conn.execute(
        """
        SELECT id, name, tye, bank_name, bank_code, init_amt, cur_amt, remark
        FROM accts
        WHERE COALESCE(is_del, 0) = 0
        ORDER BY id
        """
    ).fetchall()


def normalize_accounts(rows: list[sqlite3.Row]) -> list[dict]:
    accounts: list[dict] = []
    for row in rows:
        accounts.append(
            {
                "id": int(row["id"]),
                "name": row["name"] or "",
                "type_id": int(row["tye"] or 6),
                "bank_name": row["bank_name"] or "",
                "bank_card": row["bank_code"] or "",
                "init_prc": money(row["init_amt"]),
                "cur_prc": money(row["cur_amt"]),
                "note": row["remark"] or "",
                "is_del": 0,
            }
        )
    return accounts


def require_target_tables(conn: sqlite3.Connection, accounts: list[dict]) -> None:
    for table_name in ("app_account_settle", "app_account_settle_type"):
        if not table_exists(conn, table_name):
            raise RuntimeError(f"target table not found: {table_name}")

    valid_type_ids = {
        int(row[0])
        for row in conn.execute("SELECT id FROM app_account_settle_type").fetchall()
    }
    unknown_types = sorted(
        {account["type_id"] for account in accounts} - valid_type_ids
    )
    if unknown_types:
        raise RuntimeError(f"target account types missing: {unknown_types}")


def upsert_account(conn: sqlite3.Connection, account: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_account_settle
        SET name = :name,
            type_id = :type_id,
            bank_name = :bank_name,
            bank_card = :bank_card,
            init_prc = :init_prc,
            cur_prc = :cur_prc,
            note = :note,
            is_del = :is_del
        WHERE id = :id
        """,
        account,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_account_settle
            (id, name, type_id, bank_name, bank_card, init_prc, cur_prc, note, is_del)
        VALUES
            (:id, :name, :type_id, :bank_name, :bank_card, :init_prc, :cur_prc,
             :note, :is_del)
        """,
        account,
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


def import_accounts(
    conn: sqlite3.Connection,
    accounts: list[dict],
    replace: bool,
) -> None:
    require_target_tables(conn, accounts)
    incoming_ids = [account["id"] for account in accounts]
    if replace and incoming_ids:
        placeholders = ",".join("?" for _ in incoming_ids)
        conn.execute(
            f"UPDATE app_account_settle SET is_del = 1 WHERE id NOT IN ({placeholders})",
            incoming_ids,
        )
    for account in accounts:
        upsert_account(conn, account)
    reset_sequence(conn, "app_account_settle")


def print_preview(accounts: list[dict]) -> None:
    print(f"settle accounts to import: {len(accounts)}")
    for account in accounts:
        print(
            f"  id={account['id']:<3} type={account['type_id']} "
            f"balance={account['cur_prc']:<14} name={account['name']}"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync settle accounts from ZHJ accts to Easy Store."
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
        help="keep target accounts that do not exist in the source account list",
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
        source_accounts = load_source_accounts(source_conn)
    accounts = normalize_accounts(source_accounts)
    print_preview(accounts)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_accounts(target_conn, accounts, replace=not args.append)
        active_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_account_settle WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]

    print(f"import complete. active settle accounts: {active_total}")


if __name__ == "__main__":
    main()
