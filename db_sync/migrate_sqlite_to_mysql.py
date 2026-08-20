"""Migrate the current Easy Store SQLite database to MySQL 8.

The script is plan-only by default. It uses the sqlite3-to-mysql CLI for
schema/data transfer, then adds MySQL indexes derived from Easy Store queries.

Examples:
  $env:EASY_STORE_MYSQL_PASSWORD = 'your-password'
  python db_sync/migrate_sqlite_to_mysql.py --execute

  # Refresh an already migrated MySQL database.
  python db_sync/migrate_sqlite_to_mysql.py --execute --replace
"""

import argparse
import getpass
import os
import site
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = PROJECT_ROOT / "easy_store_boot" / "db" / "easy_store.db"
PASSWORD_ENV = "EASY_STORE_MYSQL_PASSWORD"

# (table name, index name, index columns/expression)
# These indexes complement the indexes transferred by sqlite3-to-mysql.
BUSINESS_INDEXES: Sequence[Tuple[str, str, str]] = (
    ("app_account_settle", "idx_es_settle_del_type", "`is_del`, `type_id`, `id`"),
    ("app_account_settle_type", "idx_es_settle_type_title", "`title`(100)"),
    ("app_customer", "idx_es_customer_del_status_category",
     "`is_del`, `status`, `category_id`, `id`"),
    ("app_customer", "idx_es_customer_del_status_level",
     "`is_del`, `status`, `level_id`, `id`"),
    ("app_customer_category", "idx_es_customer_category_del_parent",
     "`is_del`, `parent_id`, `root`, `id`"),
    ("app_customer_level", "idx_es_customer_level_del_title", "`is_del`, `title`(100)"),
    ("app_customer_quote", "idx_es_quote_customer_goods_del",
     "`customer_id`(100), `goods_id`(100), `is_del`"),
    ("app_customer_quote", "idx_es_quote_goods_customer_del",
     "`goods_id`(100), `customer_id`(100), `is_del`"),
    ("app_goods", "idx_es_goods_del_status_category",
     "`is_del`, `status`, `category_id`, `id`"),
    ("app_goods", "idx_es_goods_del_supplier", "`is_del`, `supplier_id`, `id`"),
    ("app_goods", "idx_es_goods_title", "`title`(100)"),
    ("app_goods", "idx_es_goods_code", "`goods_code`(50)"),
    ("app_goods", "idx_es_goods_py", "`py_code`(100)"),
    ("app_goods_category", "idx_es_goods_category_del_parent",
     "`is_del`, `parent_id`, `root`, `id`"),
    ("app_home_menu", "idx_es_home_menu_active_sort",
     "`is_del`, `status`, `root_only`, `group_code`(50), `sort_no`, `id`"),
    ("app_income_expense_item", "idx_es_income_item_active_type",
     "`is_del`, `disabled`, `item_type`(50), `id`"),
    ("app_income_expense_item", "idx_es_income_item_name_type",
     "`name`(100), `item_type`(50), `is_del`"),
    ("app_income_expense_record", "idx_es_income_record_del_time",
     "`is_del`, `create_time`, `id`"),
    ("app_income_expense_record", "idx_es_income_record_del_item_time",
     "`is_del`, `fund_item`(100), `create_time`, `id`"),
    ("app_income_expense_record", "idx_es_income_record_del_cashier_time",
     "`is_del`, `cashier_id`(100), `create_time`, `id`"),
    ("app_income_expense_record", "idx_es_income_record_del_party_time",
     "`is_del`, `counterparty`(100), `create_time`, `id`"),
    ("app_operation_log", "idx_es_operation_log_time_id", "`operate_time`, `id`"),
    ("app_operation_log", "idx_es_operation_log_operator_time",
     "`operator_id`(100), `operate_time`, `id`"),
    ("app_payment_amount_item", "idx_es_payment_amount_order_id", "`order_id`, `id`"),
    ("app_payment_settle_item", "idx_es_payment_settle_payment_settle",
     "`payment_id`, `settle_id`, `id`"),
    ("app_payment_voucher", "idx_es_payment_del_status_time",
     "`is_del`, `status`, `create_time`, `id`"),
    ("app_payment_voucher", "idx_es_payment_del_status_supplier_time",
     "`is_del`, `status`, `supplier_id`, `create_time`, `id`"),
    ("app_payment_voucher", "idx_es_payment_del_status_cashier_time",
     "`is_del`, `status`, `cashier_id`, `create_time`, `id`"),
    ("app_payment_voucher", "idx_es_payment_order_no", "`order_no`(50)"),
    ("app_purchase_order", "idx_es_purchase_del_status_time",
     "`is_del`, `status`, `create_time`, `id`"),
    ("app_purchase_order", "idx_es_purchase_del_status_supplier_time",
     "`is_del`, `status`, `supplier_id`, `create_time`, `id`"),
    ("app_purchase_order", "idx_es_purchase_del_status_cashier_time",
     "`is_del`, `status`, `cashier_id`, `create_time`, `id`"),
    ("app_purchase_order", "idx_es_purchase_del_status_settle_time",
     "`is_del`, `status`, `settle_id`, `create_time`, `id`"),
    ("app_purchase_order", "idx_es_purchase_order_no", "`order_no`(50)"),
    ("app_purchase_order_item", "idx_es_purchase_item_order_del",
     "`order_id`, `is_del`, `id`"),
    ("app_purchase_order_item", "idx_es_purchase_item_goods_del_order",
     "`goods_id`, `is_del`, `order_id`"),
    ("app_purchase_order_item", "idx_es_purchase_item_category_del_order",
     "`category_id`, `is_del`, `order_id`"),
    ("app_receive_payment_amount_item", "idx_es_receive_amount_order_id", "`order_id`, `id`"),
    ("app_receive_payment_settle_item", "idx_es_receive_settle_payment_settle",
     "`payment_id`, `settle_id`, `id`"),
    ("app_receive_payment_voucher", "idx_es_receive_del_status_time",
     "`is_del`, `status`, `create_time`, `id`"),
    ("app_receive_payment_voucher", "idx_es_receive_del_status_customer_time",
     "`is_del`, `status`, `customer_id`, `create_time`, `id`"),
    ("app_receive_payment_voucher", "idx_es_receive_del_status_cashier_time",
     "`is_del`, `status`, `cashier_id`, `create_time`, `id`"),
    ("app_receive_payment_voucher", "idx_es_receive_order_no", "`order_no`(50)"),
    ("app_role", "idx_es_role_del_status", "`is_del`, `status`, `id`"),
    ("app_role_menu", "idx_es_role_menu_menu_role", "`menu_id`, `role_id`"),
    ("app_role_permission", "idx_es_role_permission_code_role",
     "`permission_code`(100), `role_id`"),
    ("app_sale_order", "idx_es_sale_del_status_time",
     "`is_del`, `status`, `create_time`, `id`"),
    ("app_sale_order", "idx_es_sale_del_status_customer_time",
     "`is_del`, `status`, `customer_id`, `create_time`, `id`"),
    ("app_sale_order", "idx_es_sale_del_status_cashier_time",
     "`is_del`, `status`, `cashier_id`, `create_time`, `id`"),
    ("app_sale_order", "idx_es_sale_del_status_settle_time",
     "`is_del`, `status`, `settle_id`, `create_time`, `id`"),
    ("app_sale_order", "idx_es_sale_order_no", "`order_no`(50)"),
    ("app_sale_order_item", "idx_es_sale_item_order_del", "`order_id`, `is_del`, `id`"),
    ("app_sale_order_item", "idx_es_sale_item_goods_del_order",
     "`goods_id`, `is_del`, `order_id`"),
    ("app_sale_order_item", "idx_es_sale_item_category_del_order",
     "`category_id`, `is_del`, `order_id`"),
    ("app_stock_check", "idx_es_stock_check_del_time", "`is_del`, `create_time`, `id`"),
    ("app_stock_check", "idx_es_stock_check_del_cashier_time",
     "`is_del`, `cashier_id`(100), `create_time`, `id`"),
    ("app_stock_check_item", "idx_es_stock_check_item_check_del",
     "`check_id`, `is_del`, `id`"),
    ("app_stock_check_item", "idx_es_stock_check_item_goods_del",
     "`goods_id`(100), `is_del`, `check_id`"),
    ("app_supplier", "idx_es_supplier_del_status", "`is_del`, `status`, `id`"),
    ("app_unit", "idx_es_unit_name", "`name`(50)"),
    ("app_user", "idx_es_user_del_status_role", "`is_del`, `status`, `role_id`, `id`"),
    ("app_user", "idx_es_user_root_del", "`is_root`, `is_del`, `id`"),
)

# SQLite declared this as a separate unique index rather than a table constraint.
# Document numbers intentionally use ordinary indexes because old records can
# contain multiple empty/NULL numbers and MySQL cannot preserve that uniqueness.
UNIQUE_INDEXES: Sequence[Tuple[str, str, str]] = (
    ("app_user", "uq_es_user_name", "`user_name`(100)"),
)

ORDER_UPDATE_COLUMNS: Sequence[Tuple[str, str]] = (
    ("app_sale_order", "update_by"),
    ("app_purchase_order", "update_by"),
    ("app_receive_payment_voucher", "update_by"),
    ("app_payment_voucher", "update_by"),
    ("app_sale_order", "update_time"),
    ("app_purchase_order", "update_time"),
    ("app_receive_payment_voucher", "update_time"),
    ("app_payment_voucher", "update_time"),
)


def quote_identifier(identifier: str) -> str:
    return "`" + identifier.replace("`", "``") + "`"


def sqlite_tables(source: Path) -> List[str]:
    with sqlite3.connect(str(source)) as connection:
        rows = connection.execute(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
            ORDER BY name
            """
        ).fetchall()
    return [row[0] for row in rows]


def source_tables(source: Path) -> List[str]:
    return [table_name for table_name in sqlite_tables(source) if not table_name.startswith("_")]


def ensure_order_update_columns(source: Path) -> int:
    """Add order update metadata before sqlite3-to-mysql copies the schema."""
    changed = 0
    with sqlite3.connect(str(source)) as connection:
        for table_name, column_name in ORDER_UPDATE_COLUMNS:
            columns = {
                row[1]
                for row in connection.execute(
                    "PRAGMA table_info({})".format(quote_identifier(table_name))
                ).fetchall()
            }
            if column_name in columns:
                continue
            connection.execute(
                "ALTER TABLE {} ADD COLUMN {} {}".format(
                    quote_identifier(table_name),
                    quote_identifier(column_name),
                    "TEXT" if column_name == "update_by" else "DATETIME",
                )
            )
            changed += 1
        connection.commit()
    return changed


def normalize_datetime_values(source: Path, target: Path, tables: Iterable[str]) -> Tuple[int, int]:
    """Copy SQLite data, normalize dates, and remove incompatible SQLite indexes."""
    normalized_count = 0
    removed_indexes = 0
    with sqlite3.connect(str(source)) as source_connection:
        with sqlite3.connect(str(target)) as target_connection:
            source_connection.backup(target_connection)
            for table_name in tables:
                columns = target_connection.execute(
                    "PRAGMA table_info({})".format(quote_identifier(table_name))
                ).fetchall()
                for _, column_name, column_type, *_ in columns:
                    type_name = (column_type or "").upper()
                    if "DATE" not in type_name and "TIME" not in type_name:
                        continue
                    cursor = target_connection.execute(
                        """
                        UPDATE {table_name}
                        SET {column_name} = CASE
                            WHEN {column_name} >= 100000000000
                                THEN strftime('%Y-%m-%d %H:%M:%f',
                                              {column_name} / 1000.0, 'unixepoch')
                            ELSE strftime('%Y-%m-%d %H:%M:%f',
                                          {column_name}, 'unixepoch')
                        END
                        WHERE typeof({column_name}) IN ('integer', 'real')
                          AND {column_name} >= 1000000000
                        """.format(
                            table_name=quote_identifier(table_name),
                            column_name=quote_identifier(column_name),
                        )
                    )
                    normalized_count += cursor.rowcount
            indexes = target_connection.execute(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                  AND sql IS NOT NULL
                """
            ).fetchall()
            for (index_name,) in indexes:
                target_connection.execute("DROP INDEX {}".format(quote_identifier(index_name)))
                removed_indexes += 1
            target_connection.commit()
    return normalized_count, removed_indexes


def find_sqlite_to_mysql(command_override: str = "") -> str:
    if command_override:
        command = Path(command_override)
        if command.is_file():
            return str(command)
        raise RuntimeError("sqlite3mysql executable not found: {}".format(command))

    command = shutil.which("sqlite3mysql")
    if command:
        return command
    candidates = (
        Path(sys.executable).resolve().parent / "Scripts" / "sqlite3mysql.exe",
        Path(site.getuserbase()) / "Scripts" / "sqlite3mysql.exe",
        Path(site.getusersitepackages()).parent / "Scripts" / "sqlite3mysql.exe",
    )
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError(
        "sqlite3mysql was not found. Install it with: pip install sqlite3-to-mysql"
    )


def get_mysql_connector():
    try:
        import mysql.connector
    except ImportError as error:
        raise RuntimeError(
            "mysql-connector-python is required to add indexes and validate the import. "
            "Install it with: pip install mysql-connector-python"
        ) from error
    return mysql.connector


def mysql_connection(args: argparse.Namespace, password: str, database: bool = True):
    connector = get_mysql_connector()
    config = {
        "host": args.host,
        "port": args.port,
        "user": args.user,
        "password": password,
        "autocommit": True,
    }
    if database:
        config["database"] = args.database
    return connector.connect(**config)


def target_tables(args: argparse.Namespace, password: str) -> List[str]:
    with mysql_connection(args, password) as connection:
        cursor = connection.cursor()
        cursor.execute("SHOW TABLES")
        return [str(row[0]) for row in cursor.fetchall()]


def ensure_mysql_order_update_columns(args: argparse.Namespace, password: str) -> int:
    """Add order update metadata to an already migrated MySQL database."""
    changed = 0
    with mysql_connection(args, password) as connection:
        cursor = connection.cursor()
        for table_name, column_name in ORDER_UPDATE_COLUMNS:
            cursor.execute(
                """
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = %s AND table_name = %s AND column_name = %s
                """,
                (args.database, table_name, column_name),
            )
            if cursor.fetchone()[0]:
                continue
            column_type = "VARCHAR(100)" if column_name == "update_by" else "DATETIME"
            cursor.execute(
                "ALTER TABLE {} ADD COLUMN {} {}".format(
                    quote_identifier(table_name),
                    quote_identifier(column_name),
                    column_type,
                )
            )
            changed += 1
    return changed


def reset_target_tables(args: argparse.Namespace, password: str) -> int:
    """Drop only Easy Store tables left by a previous failed or test migration."""
    table_names = [
        table_name for table_name in target_tables(args, password)
        if table_name.startswith("app_") or table_name.startswith("_app_")
    ]
    if not table_names:
        return 0
    with mysql_connection(args, password) as connection:
        cursor = connection.cursor()
        cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
        for table_name in table_names:
            cursor.execute("DROP TABLE {}".format(quote_identifier(table_name)))
        cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    return len(table_names)


def repair_zero_primary_key_tables(
    args: argparse.Namespace, password: str, source: Path, tables: Iterable[str]
) -> List[str]:
    """Restore tables containing SQLite id=0 rows lost to MySQL AUTO_INCREMENT."""
    repaired_tables: List[str] = []
    with sqlite3.connect(str(source)) as sqlite_connection:
        sqlite_connection.row_factory = sqlite3.Row
        with mysql_connection(args, password) as mysql_connection_instance:
            mysql_cursor = mysql_connection_instance.cursor()
            mysql_cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
            mysql_cursor.execute(
                "SET SESSION sql_mode = CONCAT(@@SESSION.sql_mode, ',NO_AUTO_VALUE_ON_ZERO')"
            )
            try:
                for table_name in tables:
                    columns = sqlite_connection.execute(
                        "PRAGMA table_info({})".format(quote_identifier(table_name))
                    ).fetchall()
                    column_names = [column["name"] for column in columns]
                    if "id" not in column_names:
                        continue
                    has_zero_id = sqlite_connection.execute(
                        "SELECT COUNT(1) FROM {} WHERE id = 0".format(
                            quote_identifier(table_name)
                        )
                    ).fetchone()[0]
                    if not has_zero_id:
                        continue

                    rows = sqlite_connection.execute(
                        "SELECT {} FROM {}".format(
                            ", ".join(quote_identifier(name) for name in column_names),
                            quote_identifier(table_name),
                        )
                    ).fetchall()
                    mysql_cursor.execute("DELETE FROM {}".format(quote_identifier(table_name)))
                    placeholders = ", ".join(["%s"] * len(column_names))
                    mysql_cursor.executemany(
                        "INSERT INTO {table_name} ({columns}) VALUES ({placeholders})".format(
                            table_name=quote_identifier(table_name),
                            columns=", ".join(quote_identifier(name) for name in column_names),
                            placeholders=placeholders,
                        ),
                        [tuple(row[name] for name in column_names) for row in rows],
                    )
                    repaired_tables.append(table_name)
            finally:
                mysql_cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
    return repaired_tables


def build_transfer_command(
    args: argparse.Namespace, source: Path, password: str, excluded_tables: Iterable[str]
) -> List[str]:
    command = [
        find_sqlite_to_mysql(args.sqlite3mysql),
        "--sqlite-file",
        str(source),
        "--mysql-database",
        args.database,
        "--mysql-user",
        args.user,
        "--mysql-password",
        password,
        "--mysql-host",
        args.host,
        "--mysql-port",
        str(args.port),
        "--mysql-charset",
        "utf8mb4",
        "--mysql-collation",
        "utf8mb4_unicode_ci",
        "--without-foreign-keys",
        "--ignore-duplicate-keys",
        "--chunk",
        str(args.chunk),
    ]
    excluded = list(excluded_tables)
    if excluded:
        command.extend(["--exclude-sqlite-tables", *excluded])
    if args.replace:
        command.extend([
            "--mysql-skip-create-tables",
            "--mysql-truncate-tables",
            "--mysql-insert-method",
            "UPDATE",
        ])
    return command


def display_command(command: Sequence[str], password: str) -> str:
    rendered = []
    for token in command:
        rendered.append("******" if token == password else token)
    return subprocess.list2cmdline(rendered)


def ensure_indexes(args: argparse.Namespace, password: str) -> int:
    created = 0
    with mysql_connection(args, password) as connection:
        cursor = connection.cursor()
        for unique, indexes in ((False, BUSINESS_INDEXES), (True, UNIQUE_INDEXES)):
            for table_name, index_name, columns in indexes:
                cursor.execute(
                    """
                    SELECT COUNT(1)
                    FROM information_schema.statistics
                    WHERE table_schema = %s AND table_name = %s AND index_name = %s
                    """,
                    (args.database, table_name, index_name),
                )
                if cursor.fetchone()[0]:
                    continue
                cursor.execute(
                    "CREATE {unique}INDEX {index_name} ON {table_name} ({columns})".format(
                        unique="UNIQUE " if unique else "",
                        index_name=quote_identifier(index_name),
                        table_name=quote_identifier(table_name),
                        columns=columns,
                    )
                )
                created += 1
    return created


def verify_counts(args: argparse.Namespace, password: str, source: Path, tables: Iterable[str]) -> None:
    with sqlite3.connect(str(source)) as sqlite_connection:
        sqlite_counts = {
            table_name: sqlite_connection.execute(
                "SELECT COUNT(1) FROM {}".format(quote_identifier(table_name))
            ).fetchone()[0]
            for table_name in tables
        }

    mismatches = []
    with mysql_connection(args, password) as connection:
        cursor = connection.cursor()
        for table_name, expected in sqlite_counts.items():
            cursor.execute("SELECT COUNT(1) FROM {}".format(quote_identifier(table_name)))
            actual = cursor.fetchone()[0]
            if actual != expected:
                mismatches.append((table_name, expected, actual))

    if mismatches:
        details = ", ".join(
            "{} (SQLite={}, MySQL={})".format(table, expected, actual)
            for table, expected, actual in mismatches
        )
        raise RuntimeError("row count validation failed: {}".format(details))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Transfer Easy Store SQLite data to MySQL 8 and add business indexes."
    )
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="easy_store")
    parser.add_argument(
        "--password",
        default=os.environ.get(PASSWORD_ENV),
        help="MySQL password. Defaults to the {} environment variable.".format(PASSWORD_ENV),
    )
    parser.add_argument("--chunk", type=int, default=5000)
    parser.add_argument(
        "--sqlite3mysql",
        default="",
        help="optional full path to the sqlite3mysql executable",
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="perform the migration; without this flag only the plan is printed",
    )
    parser.add_argument(
        "--replace",
        action="store_true",
        help="truncate and refresh an existing Easy Store MySQL schema",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="drop existing app_* tables before importing from scratch",
    )
    parser.add_argument(
        "--indexes-only",
        action="store_true",
        help="only add missing MySQL indexes and validate imported row counts",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source = args.source.resolve()
    if not source.is_file():
        raise FileNotFoundError("SQLite database not found: {}".format(source))
    if args.chunk < 1:
        raise ValueError("--chunk must be greater than zero")

    tables = source_tables(source)
    excluded_tables = [table_name for table_name in sqlite_tables(source) if table_name.startswith("_")]
    print("Easy Store SQLite -> MySQL 8 migration")
    print("source:", source)
    print("target: {}@{}:{}/{}".format(args.user, args.host, args.port, args.database))
    print("tables:", len(tables))
    print("business indexes:", len(BUSINESS_INDEXES) + len(UNIQUE_INDEXES))
    print("excluded SQLite backup tables: names starting with '_'")

    if not args.execute:
        print("\nPlan only. Set {} and add --execute to start.".format(PASSWORD_ENV))
        return

    if args.indexes_only and (args.reset or args.replace):
        raise ValueError("--indexes-only cannot be combined with --reset or --replace")

    password = args.password or getpass.getpass("MySQL password: ")
    source_columns_added = ensure_order_update_columns(source)
    existing = target_tables(args, password)
    if args.indexes_only:
        if not existing:
            raise RuntimeError("target database has no tables; run a full migration first")
        target_columns_added = ensure_mysql_order_update_columns(args, password)
        with tempfile.TemporaryDirectory(
            prefix="easy_store_mysql_", ignore_cleanup_errors=True
        ) as temporary_directory:
            repair_source = Path(temporary_directory) / "easy_store_normalized.db"
            normalize_datetime_values(source, repair_source, tables)
            repaired_tables = repair_zero_primary_key_tables(
                args, password, repair_source, tables
            )
        created = ensure_indexes(args, password)
        verify_counts(args, password, source, tables)
        print(
            "\nIndex validation complete. Repaired {} ID-zero tables, added {} missing "
            "indexes and {} metadata columns; row counts match.".format(
                len(repaired_tables), created, target_columns_added
            )
        )
        return

    if existing and not args.replace and not args.reset:
        raise RuntimeError(
            "target database already contains tables: {}. "
            "Use --replace for a complete prior migration or --reset to rebuild app_* tables."
            .format(", ".join(existing[:10]))
        )

    if args.reset:
        removed = reset_target_tables(args, password)
        print("Removed {} existing Easy Store tables.".format(removed))

    # SQLite/anti-virus processes can briefly retain the copied database on Windows.
    # Do not turn a completed import into a false failure because of temp cleanup.
    with tempfile.TemporaryDirectory(
        prefix="easy_store_mysql_", ignore_cleanup_errors=True
    ) as temporary_directory:
        transfer_source = Path(temporary_directory) / "easy_store_normalized.db"
        normalized_count, removed_indexes = normalize_datetime_values(
            source, transfer_source, tables
        )
        command = build_transfer_command(args, transfer_source, password, excluded_tables)
        print(
            "Added {} order metadata columns, normalized {} legacy numeric date values "
            "and removed {} SQLite indexes."
            .format(source_columns_added, normalized_count, removed_indexes)
        )
        print("\nRunning transfer:\n{}".format(display_command(command, password)), flush=True)
        try:
            subprocess.run(command, check=True)
        except subprocess.CalledProcessError as error:
            raise RuntimeError(
                "sqlite3mysql transfer failed with exit code {}. "
                "The source SQLite database was not modified.".format(error.returncode)
            ) from None
        repaired_tables = repair_zero_primary_key_tables(
            args, password, transfer_source, tables
        )
        if repaired_tables:
            print("Restored SQLite id=0 rows in: {}.".format(", ".join(repaired_tables)))

    created = ensure_indexes(args, password)
    verify_counts(args, password, source, tables)
    print("\nMigration complete. Added {} business indexes; row counts match.".format(created))


if __name__ == "__main__":
    main()
