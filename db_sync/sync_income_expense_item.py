import argparse
import shutil
import sqlite3
import sys
from datetime import datetime
from pathlib import Path

from sync_receive_payment_voucher import (
    TARGET_DB,
    configure_stdout,
    connect_readonly,
    connect_target,
    normalize_datetime,
    require_file,
    table_exists,
)


SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
BACKUP_DIR = Path(__file__).resolve().parent / "backups"
SOURCE_TABLE = "projects"


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_income_expense_item_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    total = conn.execute(
        """
        SELECT COUNT(1)
        FROM projects
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchone()[0]
    print(f"source table: {SOURCE_TABLE} ({total} active rows)")
    print("field mapping:")
    print("  projects.id        -> app_income_expense_item.id")
    print("  projects.name      -> app_income_expense_item.name")
    print("  projects.tye       -> item_type (1 income, 2 expense)")
    print("  projects.is_profit -> participate_performance")
    print("  projects.is_stop   -> disabled")
    print("  projects.create_at -> create_time")
    print("  projects.revise_at -> update_time")


def load_source_items(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return conn.execute(
        """
        SELECT id, name, pinyin, tye, subtye, is_profit, is_stop,
               create_at, revise_at
        FROM projects
        WHERE COALESCE(is_del, 0) = 0
        ORDER BY id
        """
    ).fetchall()


def normalize_items(
    rows: list[sqlite3.Row],
) -> tuple[list[dict], list[str]]:
    items: list[dict] = []
    warnings: list[str] = []
    for row in rows:
        source_type = int(row["tye"] or 0)
        if source_type == 1:
            item_type = "income"
        elif source_type == 2:
            item_type = "expense"
        else:
            warnings.append(
                f"project id={row['id']} has unsupported type={source_type}"
            )
            continue

        name = str(row["name"] or "").strip()
        if not name:
            warnings.append(f"project id={row['id']} has empty name")
            continue

        items.append(
            {
                "id": int(row["id"]),
                "name": name,
                "item_type": item_type,
                "participate_performance": 1
                if int(row["is_profit"] or 0) == 1
                else 0,
                "disabled": 1 if int(row["is_stop"] or 0) == 1 else 0,
                "create_time": normalize_datetime(row["create_at"]),
                "update_time": normalize_datetime(row["revise_at"]),
                "is_del": 0,
                "pinyin": str(row["pinyin"] or "").strip(),
                "subtype": int(row["subtye"] or 0),
            }
        )
    return items, warnings


def ensure_no_name_conflicts(
    conn: sqlite3.Connection, items: list[dict]
) -> None:
    source_ids = {item["id"] for item in items}
    for item in items:
        row = conn.execute(
            """
            SELECT id
            FROM app_income_expense_item
            WHERE name = ? AND item_type = ?
              AND id <> ? AND COALESCE(is_del, 0) = 0
            """,
            (item["name"], item["item_type"], item["id"]),
        ).fetchone()
        if row is not None and int(row["id"]) not in source_ids:
            raise RuntimeError(
                f"target item name={item['name']} type={item['item_type']} "
                f"conflicts with id={row['id']}"
            )


def upsert_item(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_income_expense_item
        SET name = :name,
            item_type = :item_type,
            participate_performance = :participate_performance,
            disabled = :disabled,
            create_time = :create_time,
            update_time = :update_time,
            is_del = :is_del
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_income_expense_item
            (id, name, item_type, participate_performance, disabled,
             create_time, update_time, is_del)
        VALUES
            (:id, :name, :item_type, :participate_performance, :disabled,
             :create_time, :update_time, :is_del)
        """,
        item,
    )


def reset_sequence(conn: sqlite3.Connection) -> None:
    if not table_exists(conn, "sqlite_sequence"):
        return
    row = conn.execute(
        "SELECT MAX(id) FROM app_income_expense_item"
    ).fetchone()
    conn.execute(
        "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
        ("app_income_expense_item", int(row[0] or 0)),
    )


def import_items(conn: sqlite3.Connection, items: list[dict]) -> None:
    if not table_exists(conn, "app_income_expense_item"):
        raise RuntimeError("target table not found: app_income_expense_item")
    ensure_no_name_conflicts(conn, items)
    for item in items:
        upsert_item(conn, item)
    reset_sequence(conn)


def print_preview(items: list[dict], warnings: list[str]) -> None:
    income_total = sum(1 for item in items if item["item_type"] == "income")
    expense_total = len(items) - income_total
    performance_total = sum(
        1 for item in items if item["participate_performance"] == 1
    )
    print(f"income/expense items to import: {len(items)}")
    print(
        f"  income={income_total}, expense={expense_total}, "
        f"participate performance={performance_total}"
    )
    print("first 20 items:")
    for item in items[:20]:
        print(
            f"  id={item['id']:<3} type={item['item_type']:<7} "
            f"performance={item['participate_performance']} "
            f"disabled={item['disabled']} name={item['name']}"
        )
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync income/expense items from ZHJ projects to Easy Store."
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
        source_items = load_source_items(source_conn)
    items, warnings = normalize_items(source_items)
    print_preview(items, warnings)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_items(target_conn, items)
        active_total = target_conn.execute(
            """
            SELECT COUNT(1)
            FROM app_income_expense_item
            WHERE COALESCE(is_del, 0) = 0
            """
        ).fetchone()[0]

    print(f"import complete. active income/expense items: {active_total}")


if __name__ == "__main__":
    main()
