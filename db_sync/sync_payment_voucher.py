import argparse
import shutil
import sqlite3
import sys
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
from pathlib import Path

from sync_receive_payment_voucher import (
    AMOUNT_ITEM_ID_OFFSET,
    SETTLE_ITEM_ID_OFFSET,
    TARGET_DB,
    append_note,
    bill_datetime,
    configure_stdout,
    connect_readonly,
    connect_target,
    format_money,
    money,
    require_file,
    require_tables,
    table_exists,
)


SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
BACKUP_DIR = Path(__file__).resolve().parent / "backups"
PAYMENT_PREFIX = "FKD"


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_payment_voucher_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def inspect_source(conn: sqlite3.Connection) -> None:
    payment_total = conn.execute(
        """
        SELECT COUNT(1)
        FROM funds
        WHERE COALESCE(is_del, 0) = 0 AND code LIKE ?
        """,
        (f"{PAYMENT_PREFIX}%",),
    ).fetchone()[0]
    linked_total = conn.execute(
        """
        SELECT COUNT(1)
        FROM billfunds bill
        INNER JOIN funds fund ON fund.md = bill.md
        WHERE COALESCE(fund.is_del, 0) = 0
          AND COALESCE(bill.is_del, 0) = 0
          AND fund.code LIKE ?
          AND bill.bill_type = 2
        """,
        (f"{PAYMENT_PREFIX}%",),
    ).fetchone()[0]
    print("source tables:")
    print(f"  - funds ({PAYMENT_PREFIX} payments): {payment_total}")
    print(f"  - billfunds (linked purchase settlements): {linked_total}")
    print("field mapping:")
    print("  funds.id         -> app_payment_voucher.id")
    print("  funds.code       -> app_payment_voucher.order_no")
    print("  funds.company_id -> app_payment_voucher.supplier_id")
    print("  funds.out_amt    -> app_payment_voucher.amount")
    print("  funds.acct_id    -> app_payment_settle_item.settle_id")
    print("  billfunds        -> app_payment_amount_item")
    print("  old employees    -> ignored; cashier always uses target ROOT user")


def load_source_vouchers(
    conn: sqlite3.Connection,
    start_date: str | None,
    end_date: str | None,
) -> list[sqlite3.Row]:
    clauses = ["COALESCE(is_del, 0) = 0", "code LIKE ?"]
    params: list[str] = [f"{PAYMENT_PREFIX}%"]
    if start_date:
        clauses.append("opt_on >= ?")
        params.append(start_date)
    if end_date:
        clauses.append("opt_on <= ?")
        params.append(end_date)
    return conn.execute(
        f"""
        SELECT id, opt_on, code, company_id, acct_id, in_amt, out_amt,
               small_change_amt, remark, md, create_at, revise_at
        FROM funds
        WHERE {' AND '.join(clauses)}
        ORDER BY id
        """,
        params,
    ).fetchall()


def load_source_amount_items(
    conn: sqlite3.Connection, voucher_ids: set[int]
) -> list[sqlite3.Row]:
    if not voucher_ids:
        return []
    placeholders = ",".join("?" for _ in voucher_ids)
    return conn.execute(
        f"""
        SELECT bill.id, bill.bill_id, bill.amt, bill.remark,
               fund.id AS voucher_id, fund.company_id
        FROM billfunds bill
        INNER JOIN funds fund ON fund.md = bill.md
        WHERE COALESCE(bill.is_del, 0) = 0
          AND COALESCE(fund.is_del, 0) = 0
          AND bill.bill_type = 2
          AND fund.id IN ({placeholders})
        ORDER BY fund.id, bill.id
        """,
        sorted(voucher_ids),
    ).fetchall()


def load_target_ids(
    conn: sqlite3.Connection, table_name: str
) -> set[int]:
    rows = conn.execute(
        f"SELECT id FROM {table_name} WHERE COALESCE(is_del, 0) = 0"
    ).fetchall()
    return {int(row["id"]) for row in rows}


def load_target_purchase_orders(conn: sqlite3.Connection) -> dict[int, dict]:
    rows = conn.execute(
        """
        SELECT id, order_no, supplier_id
        FROM app_purchase_order
        WHERE COALESCE(is_del, 0) = 0
        """
    ).fetchall()
    return {
        int(row["id"]): {
            "order_no": str(row["order_no"] or ""),
            "supplier_id": int(row["supplier_id"] or 0),
        }
        for row in rows
    }


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


def normalize_vouchers(
    rows: list[sqlite3.Row],
    supplier_ids: set[int],
    account_ids: set[int],
    root_user: dict,
) -> tuple[list[dict], list[str]]:
    vouchers: list[dict] = []
    warnings: list[str] = []
    code_counts = Counter(
        str(row["code"] or f"{PAYMENT_PREFIX}-SOURCE-{row['id']}")
        for row in rows
    )
    for row in rows:
        voucher_id = int(row["id"])
        supplier_id = int(row["company_id"] or 0)
        if supplier_id not in supplier_ids:
            warnings.append(
                f"payment id={voucher_id} supplier id={supplier_id} missing"
            )
            continue

        source_settle_id = int(row["acct_id"] or 0)
        settle_id = None
        if source_settle_id:
            if source_settle_id in account_ids:
                settle_id = source_settle_id
            else:
                warnings.append(
                    f"payment id={voucher_id} settle account id="
                    f"{source_settle_id} missing"
                )

        payment_amount = money(row["out_amt"])
        income_amount = money(row["in_amt"])
        if abs(income_amount) >= 0.005:
            warnings.append(
                f"payment id={voucher_id} has in_amt={format_money(income_amount)}; "
                "only out_amt is imported as payment amount"
            )

        source_order_no = row["code"] or f"{PAYMENT_PREFIX}-SOURCE-{voucher_id}"
        order_no = source_order_no
        if code_counts[source_order_no] > 1:
            order_no = f"{source_order_no}-D{voucher_id}"
            warnings.append(
                f"payment id={voucher_id} duplicate source order_no="
                f"{source_order_no}; imported as {order_no}"
            )

        vouchers.append(
            {
                "id": voucher_id,
                "order_no": order_no,
                "supplier_id": supplier_id,
                "cashier_id": root_user["id"],
                "cashier_name": root_user["name"],
                "status": 1,
                "amount": payment_amount,
                "note": append_note(row["remark"], row["small_change_amt"]),
                "create_time": bill_datetime(row["opt_on"], row["create_at"]),
                "settle_id": settle_id,
                "settle_amount": payment_amount,
            }
        )
    return vouchers, warnings


def normalize_amount_items(
    rows: list[sqlite3.Row],
    voucher_map: dict[int, dict],
    purchase_orders: dict[int, dict],
) -> tuple[list[dict], list[str]]:
    items: list[dict] = []
    warnings: list[str] = []
    total_by_voucher: dict[int, Decimal] = defaultdict(lambda: Decimal("0"))

    for row in rows:
        voucher_id = int(row["voucher_id"])
        voucher = voucher_map.get(voucher_id)
        if voucher is None:
            continue
        purchase_id = int(row["bill_id"] or 0)
        purchase_order = purchase_orders.get(purchase_id)
        if purchase_order is None:
            warnings.append(
                f"payment id={voucher_id} settlement bill id={purchase_id} "
                "not found in target purchases"
            )
            continue
        if purchase_order["supplier_id"] != voucher["supplier_id"]:
            warnings.append(
                f"payment id={voucher_id} supplier id={voucher['supplier_id']} "
                f"does not match purchase id={purchase_id} supplier id="
                f"{purchase_order['supplier_id']}"
            )
            continue

        amount = money(row["amt"])
        total_by_voucher[voucher_id] += Decimal(str(amount))
        items.append(
            {
                "id": AMOUNT_ITEM_ID_OFFSET + int(row["id"]),
                "amount": amount,
                "create_time": voucher["create_time"],
                "note": row["remark"] or "",
                "order_no": purchase_order["order_no"],
                "order_id": voucher_id,
            }
        )

    for voucher_id, linked_total in total_by_voucher.items():
        voucher_amount = Decimal(str(voucher_map[voucher_id]["amount"]))
        if abs(linked_total - voucher_amount) > Decimal("0.01"):
            warnings.append(
                f"payment id={voucher_id} linked purchases total={linked_total:.2f} "
                f"differs from payment amount={voucher_amount:.2f}"
            )
    return items, warnings


def ensure_no_voucher_conflicts(
    conn: sqlite3.Connection, vouchers: list[dict]
) -> None:
    for voucher in vouchers:
        row = conn.execute(
            """
            SELECT order_no, is_del
            FROM app_payment_voucher
            WHERE id = ?
            """,
            (voucher["id"],),
        ).fetchone()
        if (
            row is not None
            and int(row["is_del"] or 0) == 0
            and row["order_no"] != voucher["order_no"]
        ):
            raise RuntimeError(
                f"target payment id={voucher['id']} conflicts with "
                f"order_no={row['order_no']}"
            )
        row = conn.execute(
            """
            SELECT id
            FROM app_payment_voucher
            WHERE order_no = ? AND id <> ? AND COALESCE(is_del, 0) = 0
            """,
            (voucher["order_no"], voucher["id"]),
        ).fetchone()
        if row is not None:
            raise RuntimeError(
                f"target payment order_no={voucher['order_no']} conflicts "
                f"with id={row['id']}"
            )


def upsert_voucher(conn: sqlite3.Connection, voucher: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_payment_voucher
        SET order_no = :order_no,
            supplier_id = :supplier_id,
            cashier_id = :cashier_id,
            cashier_name = :cashier_name,
            status = :status,
            amount = :amount,
            note = :note,
            create_time = :create_time,
            is_del = 0
        WHERE id = :id
        """,
        voucher,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_payment_voucher
            (id, order_no, supplier_id, cashier_id, cashier_name, status,
             amount, note, create_time, is_del)
        VALUES
            (:id, :order_no, :supplier_id, :cashier_id, :cashier_name,
             :status, :amount, :note, :create_time, 0)
        """,
        voucher,
    )


def upsert_settle_item(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_payment_settle_item
        SET settle_id = :settle_id,
            amount = :amount,
            payment_id = :payment_id,
            create_time = :create_time,
            note = :note
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_payment_settle_item
            (id, settle_id, amount, payment_id, create_time, note)
        VALUES
            (:id, :settle_id, :amount, :payment_id, :create_time, :note)
        """,
        item,
    )


def upsert_amount_item(conn: sqlite3.Connection, item: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_payment_amount_item
        SET amount = :amount,
            create_time = :create_time,
            note = :note,
            order_no = :order_no,
            order_id = :order_id
        WHERE id = :id
        """,
        item,
    )
    if cursor.rowcount:
        return
    conn.execute(
        """
        INSERT INTO app_payment_amount_item
            (id, amount, create_time, note, order_no, order_id)
        VALUES
            (:id, :amount, :create_time, :note, :order_no, :order_id)
        """,
        item,
    )


def reset_sequence(conn: sqlite3.Connection, table_name: str) -> None:
    if not table_exists(conn, "sqlite_sequence"):
        return
    row = conn.execute(f"SELECT MAX(id) FROM {table_name}").fetchone()
    conn.execute(
        "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
        (table_name, int(row[0] or 0)),
    )


def import_payment_vouchers(
    conn: sqlite3.Connection,
    vouchers: list[dict],
    amount_items: list[dict],
) -> None:
    require_tables(
        conn,
        (
            "app_supplier",
            "app_account_settle",
            "app_purchase_order",
            "app_payment_voucher",
            "app_payment_settle_item",
            "app_payment_amount_item",
        ),
    )
    ensure_no_voucher_conflicts(conn, vouchers)
    items_by_voucher: dict[int, list[dict]] = defaultdict(list)
    for item in amount_items:
        items_by_voucher[int(item["order_id"])].append(item)

    for voucher in vouchers:
        voucher_id = int(voucher["id"])
        upsert_voucher(conn, voucher)

        conn.execute(
            """
            DELETE FROM app_payment_settle_item
            WHERE payment_id = ? AND id >= ?
            """,
            (voucher_id, SETTLE_ITEM_ID_OFFSET),
        )
        if voucher["settle_id"] is not None:
            upsert_settle_item(
                conn,
                {
                    "id": SETTLE_ITEM_ID_OFFSET + voucher_id,
                    "settle_id": voucher["settle_id"],
                    "amount": voucher["settle_amount"],
                    "payment_id": voucher_id,
                    "create_time": voucher["create_time"],
                    "note": "",
                },
            )

        conn.execute(
            """
            DELETE FROM app_payment_amount_item
            WHERE order_id = ? AND id >= ?
            """,
            (voucher_id, AMOUNT_ITEM_ID_OFFSET),
        )
        for amount_item in items_by_voucher[voucher_id]:
            upsert_amount_item(conn, amount_item)

    reset_sequence(conn, "app_payment_voucher")
    reset_sequence(conn, "app_payment_settle_item")
    reset_sequence(conn, "app_payment_amount_item")


def print_preview(
    root_user: dict,
    vouchers: list[dict],
    amount_items: list[dict],
    warnings: list[str],
) -> None:
    print(f"target ROOT cashier: id={root_user['id']} name={root_user['name']}")
    print(f"payment vouchers to import: {len(vouchers)}")
    print(f"linked purchase settlement items to import: {len(amount_items)}")
    print("latest 10 payment vouchers:")
    for voucher in vouchers[-10:]:
        print(
            f"  id={voucher['id']:<6} no={voucher['order_no']:<22} "
            f"supplier={voucher['supplier_id']:<4} amount={voucher['amount']:<12} "
            f"settle={voucher['settle_id']}"
        )
    if warnings:
        print(f"warnings: {len(warnings)}")
        for warning in warnings[:50]:
            print(f"  - {warning}")
        if len(warnings) > 50:
            print(f"  ... {len(warnings) - 50} more")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync payment vouchers from ZHJ funds/billfunds to Easy Store."
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
        source_vouchers = load_source_vouchers(
            source_conn, args.start_date, args.end_date
        )
        source_amount_items = load_source_amount_items(
            source_conn, {int(row["id"]) for row in source_vouchers}
        )

    with connect_target(target) as target_conn:
        supplier_ids = load_target_ids(target_conn, "app_supplier")
        account_ids = load_target_ids(target_conn, "app_account_settle")
        purchase_orders = load_target_purchase_orders(target_conn)
        root_user = load_target_root_user(target_conn)
    if root_user is None:
        raise RuntimeError("no active ROOT user found in target app_user")

    vouchers, voucher_warnings = normalize_vouchers(
        source_vouchers, supplier_ids, account_ids, root_user
    )
    voucher_map = {int(voucher["id"]): voucher for voucher in vouchers}
    amount_items, amount_item_warnings = normalize_amount_items(
        source_amount_items, voucher_map, purchase_orders
    )
    warnings = voucher_warnings + amount_item_warnings
    print_preview(root_user, vouchers, amount_items, warnings)
    print(f"target db: {target}")
    if not args.execute:
        print("dry-run only. add --execute to import.")
        return
    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_payment_vouchers(target_conn, vouchers, amount_items)
        voucher_total = target_conn.execute(
            """
            SELECT COUNT(1)
            FROM app_payment_voucher
            WHERE COALESCE(is_del, 0) = 0
            """
        ).fetchone()[0]
        settle_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_payment_settle_item"
        ).fetchone()[0]
        amount_item_total = target_conn.execute(
            "SELECT COUNT(1) FROM app_payment_amount_item"
        ).fetchone()[0]

    print(f"import complete. active payment vouchers: {voucher_total}")
    print(f"payment settle items: {settle_total}")
    print(f"payment amount items: {amount_item_total}")


if __name__ == "__main__":
    main()
