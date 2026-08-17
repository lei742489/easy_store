import argparse
import shutil
import sqlite3
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from zoneinfo import ZoneInfo

from sync_receive_payment_voucher import (
    TARGET_DB,
    configure_stdout,
    connect_readonly,
    connect_target,
    require_file,
    require_tables,
    table_exists,
)


SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
BACKUP_DIR = Path(__file__).resolve().parent / "backups"
MANUAL_PREFIX = "SZD"
MANUAL_SUBTYPE = 3
BUSINESS_ZONE = ZoneInfo("Asia/Shanghai")

DATETIME_FORMATS = (
    "%Y-%m-%d %H:%M:%S.%f",
    "%Y-%m-%d %H:%M:%S",
    "%Y-%m-%d",
)


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / (
        f"easy_store_before_income_expense_record_{timestamp}.db"
    )
    shutil.copy2(db_path, backup_path)
    return backup_path


def money(value) -> float:
    if value is None or str(value).strip() == "":
        return 0.0
    return float(
        Decimal(str(value)).quantize(Decimal("0.01"), ROUND_HALF_UP)
    )


def business_time(value) -> int:
    text = str(value or "").strip().replace("T", " ")
    for date_format in DATETIME_FORMATS:
        try:
            parsed = datetime.strptime(text, date_format)
            return int(
                parsed.replace(tzinfo=BUSINESS_ZONE).timestamp() * 1000
            )
        except ValueError:
            continue
    raise ValueError(f"unsupported business date: {value!r}")


def inspect_source(conn: sqlite3.Connection) -> None:
    row = conn.execute(
        """
        SELECT COUNT(1) AS total,
               COALESCE(SUM(in_amt), 0) AS income,
               COALESCE(SUM(out_amt), 0) AS expense
        FROM funds f
        INNER JOIN projects p ON p.id = f.project_id
        WHERE COALESCE(f.is_del, 0) = 0
          AND COALESCE(p.is_del, 0) = 0
          AND f.code LIKE ?
          AND p.subtye = ?
        """,
        (f"{MANUAL_PREFIX}%", MANUAL_SUBTYPE),
    ).fetchone()
    print("source tables: funds, projects, companies")
    print(
        f"  - {MANUAL_PREFIX} manual income/expense records: "
        f"{row['total']}"
    )
    print(
        f"  - income={money(row['income']):.2f}, "
        f"expense={money(row['expense']):.2f}"
    )
    print("filter:")
    print(
        "  only projects.subtye = 3 is imported; fixed sale, purchase, "
        "receivable, payable, and account-transfer items are excluded"
    )
    print("field mapping:")
    print("  funds.id         -> app_income_expense_record.id")
    print("  funds.code       -> order_no")
    print("  funds.acct_id    -> settle_id")
    print("  target ROOT      -> cashier_id, cashier_name")
    print("  funds.remark     -> summary")
    print("  companies.name   -> counterparty")
    print("  projects.name    -> fund_item")
    print("  funds.in_amt     -> income")
    print("  funds.out_amt    -> expense")
    print("  funds.opt_on     -> create_time (epoch milliseconds)")


def load_source_records(
    conn: sqlite3.Connection,
    start_date: str | None,
    end_date: str | None,
) -> list[sqlite3.Row]:
    clauses = [
        "COALESCE(f.is_del, 0) = 0",
        "COALESCE(p.is_del, 0) = 0",
        "f.code LIKE ?",
        "p.subtye = ?",
    ]
    params: list[object] = [f"{MANUAL_PREFIX}%", MANUAL_SUBTYPE]
    if start_date:
        clauses.append("f.opt_on >= ?")
        params.append(start_date)
    if end_date:
        clauses.append("f.opt_on <= ?")
        params.append(end_date)

    return conn.execute(
        f"""
        SELECT f.id, f.code, f.opt_on, f.company_id, f.acct_id,
               f.project_id, f.in_amt, f.out_amt, f.remark,
               p.name AS project_name, c.name AS company_name
        FROM funds f
        INNER JOIN projects p ON p.id = f.project_id
        LEFT JOIN companies c
               ON c.id = f.company_id AND COALESCE(c.is_del, 0) = 0
        WHERE {' AND '.join(clauses)}
        ORDER BY f.id
        """,
        params,
    ).fetchall()


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
        "name": str(row["real_name"] or row["user_name"] or "").strip(),
    }


def load_target_ids(conn: sqlite3.Connection, table_name: str) -> set[int]:
    rows = conn.execute(
        f"SELECT id FROM {table_name} WHERE COALESCE(is_del, 0) = 0"
    ).fetchall()
    return {int(row["id"]) for row in rows}


def load_target_item_names(conn: sqlite3.Connection) -> dict[int, str]:
    rows = conn.execute(
        """
        SELECT id, name
        FROM app_income_expense_item
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {int(row["id"]): str(row["name"] or "").strip() for row in rows}


def normalize_records(
    rows: list[sqlite3.Row],
    account_ids: set[int],
    item_names: dict[int, str],
    root_user: dict,
) -> tuple[list[dict], list[str], int]:
    records: list[dict] = []
    warnings: list[str] = []
    skipped = 0
    for row in rows:
        record_id = int(row["id"])
        settle_id = int(row["acct_id"] or 0)
        project_id = int(row["project_id"] or 0)
        income = money(row["in_amt"])
        expense = money(row["out_amt"])
        if income < 0:
            expense += -income
            income = 0.0
        if expense < 0:
            income += -expense
            expense = 0.0

        if settle_id not in account_ids:
            warnings.append(
                f"record id={record_id} skipped: settle account id={settle_id} "
                "does not exist in target"
            )
            skipped += 1
            continue
        fund_item = item_names.get(project_id, "")
        if not fund_item:
            warnings.append(
                f"record id={record_id} skipped: income/expense item "
                f"id={project_id} does not exist in target"
            )
            skipped += 1
            continue
        if fund_item != str(row["project_name"] or "").strip():
            warnings.append(
                f"record id={record_id}: source item={row['project_name']!r} "
                f"uses target item={fund_item!r}"
            )
        if (income <= 0 and expense <= 0) or (income > 0 and expense > 0):
            warnings.append(
                f"record id={record_id} skipped: invalid income={income:.2f}, "
                f"expense={expense:.2f}"
            )
            skipped += 1
            continue

        company_id = int(row["company_id"] or 0)
        counterparty = str(row["company_name"] or "").strip()
        if company_id and not counterparty:
            warnings.append(
                f"record id={record_id}: company id={company_id} is missing; "
                "counterparty is left empty"
            )
        summary = str(row["remark"] or "").strip() or fund_item
        try:
            create_time = business_time(row["opt_on"])
        except ValueError as error:
            warnings.append(f"record id={record_id} skipped: {error}")
            skipped += 1
            continue

        records.append(
            {
                "id": record_id,
                "order_no": str(
                    row["code"] or f"{MANUAL_PREFIX}-SOURCE-{record_id}"
                ).strip(),
                "settle_id": str(settle_id),
                "cashier_id": str(root_user["id"]),
                "cashier_name": root_user["name"],
                "summary": summary,
                "counterparty": counterparty,
                "fund_item": fund_item,
                "income": income,
                "expense": expense,
                "create_time": create_time,
                "is_del": 0,
            }
        )
    return records, warnings, skipped


def upsert_record(conn: sqlite3.Connection, record: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_income_expense_record
        SET order_no = :order_no,
            settle_id = :settle_id,
            cashier_id = :cashier_id,
            cashier_name = :cashier_name,
            summary = :summary,
            counterparty = :counterparty,
            fund_item = :fund_item,
            income = :income,
            expense = :expense,
            create_time = :create_time,
            is_del = :is_del
        WHERE id = :id
        """,
        record,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_income_expense_record
            (id, order_no, settle_id, cashier_id, cashier_name, summary,
             counterparty, fund_item, income, expense, create_time, is_del)
        VALUES
            (:id, :order_no, :settle_id, :cashier_id, :cashier_name, :summary,
             :counterparty, :fund_item, :income, :expense, :create_time, :is_del)
        """,
        record,
    )


def reset_sequence(conn: sqlite3.Connection) -> None:
    if not table_exists(conn, "sqlite_sequence"):
        return
    row = conn.execute(
        "SELECT MAX(id) FROM app_income_expense_record"
    ).fetchone()
    conn.execute(
        "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
        ("app_income_expense_record", int(row[0] or 0)),
    )


def import_records(
    conn: sqlite3.Connection, records: list[dict]
) -> None:
    require_tables(
        conn,
        (
            "app_income_expense_record",
            "app_income_expense_item",
            "app_account_settle",
            "app_user",
        ),
    )
    for record in records:
        upsert_record(conn, record)
    reset_sequence(conn)


def print_preview(
    root_user: dict,
    records: list[dict],
    warnings: list[str],
    skipped: int,
) -> None:
    income_total = sum(record["income"] for record in records)
    expense_total = sum(record["expense"] for record in records)
    print(f"target ROOT cashier: id={root_user['id']} name={root_user['name']}")
    print(f"manual records to import: {len(records)}")
    print(
        f"  income={income_total:.2f}, expense={expense_total:.2f}, "
        f"skipped={skipped}"
    )
    print("latest 10 records:")
    for record in records[-10:]:
        print(
            f"  id={record['id']:<6} no={record['order_no']:<22} "
            f"item={record['fund_item']:<16} income={record['income']:<12.2f} "
            f"expense={record['expense']:<12.2f} settle={record['settle_id']}"
        )
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")
        if len(warnings) > 50:
            print(f"  ... {len(warnings) - 50} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Sync standalone income/expense records from ZHJ funds to "
            "Easy Store. Only SZD records with projects.subtye = 3 are used."
        )
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
        source_records = load_source_records(
            source_conn, args.start_date, args.end_date
        )

    with connect_target(target) as target_conn:
        require_tables(
            target_conn,
            (
                "app_income_expense_record",
                "app_income_expense_item",
                "app_account_settle",
                "app_user",
            ),
        )
        account_ids = load_target_ids(target_conn, "app_account_settle")
        item_names = load_target_item_names(target_conn)
        root_user = load_target_root_user(target_conn)
    if root_user is None:
        raise RuntimeError("no active ROOT user found in target app_user")

    records, warnings, skipped = normalize_records(
        source_records, account_ids, item_names, root_user
    )
    print_preview(root_user, records, warnings, skipped)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_records(target_conn, records)
        row = target_conn.execute(
            """
            SELECT COUNT(1) AS total,
                   COALESCE(SUM(income), 0) AS income,
                   COALESCE(SUM(expense), 0) AS expense
            FROM app_income_expense_record
            WHERE COALESCE(is_del, 0) = 0
            """
        ).fetchone()
    print(
        "import complete. "
        f"active manual records={row['total']}, "
        f"income={money(row['income']):.2f}, "
        f"expense={money(row['expense']):.2f}"
    )


if __name__ == "__main__":
    main()
