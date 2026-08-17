import argparse
import shutil
import sqlite3
from datetime import datetime
from pathlib import Path


SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
TARGET_DB = (
    Path(__file__).resolve().parents[1]
    / "easy_store_boot"
    / "db"
    / "easy_store.db"
)
BACKUP_DIR = Path(__file__).resolve().parent / "backups"

SOURCE_TABLE = "ptypes"
SOURCE_ROOT_ID = 1
TARGET_ROOT_ID = 1
TARGET_ROOT_TITLE = "\u9ed8\u8ba4\u5206\u7c7b"
TARGET_ROOT_PY = "mrfl"
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

    text = str(value).strip()
    if not text:
        return current_timestamp()

    text = text.replace("T", " ")
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


def inspect_source_table(conn: sqlite3.Connection) -> None:
    columns = conn.execute(f'PRAGMA table_info("{SOURCE_TABLE}")').fetchall()
    print(f"source table: {SOURCE_TABLE}")
    print("source columns:")
    for col in columns:
        print(f"  - {col['name']}: {col['type']}")
    print("field mapping:")
    print("  ptypes.id       -> app_goods_category.id")
    print("  ptypes.pid      -> app_goods_category.parent_id")
    print("  ptypes.name     -> app_goods_category.title")
    print("  ptypes.pinyin   -> app_goods_category.py_code")
    print("  ptypes.create_at-> app_goods_category.create_time")


def load_source_categories(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    if not table_exists(conn, SOURCE_TABLE):
        raise RuntimeError(f"source table not found: {SOURCE_TABLE}")

    return conn.execute(
        """
        SELECT id, pid, name, pinyin, level, path_local, create_at, is_del
        FROM ptypes
        WHERE COALESCE(is_del, 0) = 0
        ORDER BY COALESCE(level, 0), id
        """
    ).fetchall()


def normalize_categories(rows: list[sqlite3.Row]) -> tuple[list[dict], list[str]]:
    active_ids = {int(row["id"]) for row in rows}
    normalized: list[dict] = []
    warnings: list[str] = []

    for row in rows:
        source_id = int(row["id"])
        if source_id == SOURCE_ROOT_ID:
            continue

        raw_parent_id = row["pid"]
        parent_id = int(raw_parent_id or TARGET_ROOT_ID)
        if parent_id == SOURCE_ROOT_ID:
            parent_id = TARGET_ROOT_ID
        elif parent_id not in active_ids:
            warnings.append(
                f"category id={source_id} parent id={parent_id} missing, use root"
            )
            parent_id = TARGET_ROOT_ID

        normalized.append(
            {
                "id": source_id,
                "title": row["name"] or "",
                "py_code": row["pinyin"] or "",
                "parent_id": parent_id,
                "root": 0,
                "create_time": normalize_datetime(row["create_at"]),
                "is_del": 0,
            }
        )

    return normalized, warnings


def backup_target(db_path: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_goods_category_{timestamp}.db"
    shutil.copy2(db_path, backup_path)
    return backup_path


def ensure_target_table(conn: sqlite3.Connection) -> None:
    if not table_exists(conn, "app_goods_category"):
        raise RuntimeError("target table not found: app_goods_category")


def import_categories(
    conn: sqlite3.Connection,
    categories: list[dict],
    replace: bool,
) -> None:
    ensure_target_table(conn)

    if replace:
        incoming_ids = [TARGET_ROOT_ID] + [category["id"] for category in categories]
        placeholders = ",".join("?" for _ in incoming_ids)
        conn.execute(
            f"DELETE FROM app_goods_category WHERE id NOT IN ({placeholders})",
            incoming_ids,
        )

    upsert_category(
        conn,
        {
            "id": TARGET_ROOT_ID,
            "title": TARGET_ROOT_TITLE,
            "py_code": TARGET_ROOT_PY,
            "parent_id": 0,
            "root": 1,
            "create_time": current_timestamp(),
            "is_del": 0,
        },
    )
    for category in categories:
        upsert_category(conn, category)

    row = conn.execute("SELECT MAX(id) FROM app_goods_category").fetchone()
    max_id = int(row[0] or 0)
    if table_exists(conn, "sqlite_sequence"):
        conn.execute(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
            ("app_goods_category", max_id),
        )


def upsert_category(conn: sqlite3.Connection, category: dict) -> None:
    cursor = conn.execute(
        """
        UPDATE app_goods_category
        SET title = :title,
            py_code = :py_code,
            parent_id = :parent_id,
            root = :root,
            is_del = :is_del,
            create_time = :create_time
        WHERE id = :id
        """,
        category,
    )
    if cursor.rowcount:
        return

    conn.execute(
        """
        INSERT INTO app_goods_category
            (id, title, py_code, parent_id, root, is_del, create_time)
        VALUES
            (:id, :title, :py_code, :parent_id, :root, :is_del, :create_time)
        """,
        category,
    )


def repair_target_category_dates(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        UPDATE app_goods_category
        SET create_time = create_time || '.000'
        WHERE create_time IS NOT NULL
          AND create_time <> ''
          AND instr(create_time, '.') = 0
          AND length(create_time) = 19
        """
    )
    conn.execute(
        """
        UPDATE app_goods_category
        SET create_time = create_time || ' 00:00:00.000'
        WHERE create_time IS NOT NULL
          AND create_time <> ''
          AND instr(create_time, ' ') = 0
          AND length(create_time) = 10
        """
    )


def print_preview(categories: list[dict], warnings: list[str]) -> None:
    print(f"categories to import: {len(categories)}")
    print("first 30 rows:")
    for row in categories[:30]:
        print(
            f"  id={row['id']:<4} parent={row['parent_id']:<4} "
            f"title={row['title']} py={row['py_code']}"
        )
    if len(categories) > 30:
        print(f"  ... {len(categories) - 30} more")
    if warnings:
        print("warnings:")
        for warning in warnings:
            print(f"  - {warning}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync goods categories from ZHJ ptypes to Easy Store."
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
        help="keep existing target categories and upsert imported rows",
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
        rows = load_source_categories(source_conn)

    categories, warnings = normalize_categories(rows)
    print_preview(categories, warnings)
    print(f"target db: {target}")

    if not args.execute:
        print("dry-run only. add --execute to import.")
        return

    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"backup created: {backup_path}")

    with connect_target(target) as target_conn:
        with target_conn:
            import_categories(
                target_conn,
                categories,
                replace=not args.append,
            )
            repair_target_category_dates(target_conn)
        total = target_conn.execute(
            "SELECT COUNT(1) FROM app_goods_category WHERE COALESCE(is_del, 0) = 0"
        ).fetchone()[0]

    print(f"import complete. active target categories: {total}")


if __name__ == "__main__":
    main()
