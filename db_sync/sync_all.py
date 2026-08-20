import argparse
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
SOURCE_DB = Path(r"D:\Zhihuiji\zhjd\273a47085fb64eec90671e65bde00ab0.db")
TARGET_DB = SCRIPT_DIR.parents[0] / "easy_store_boot" / "db" / "easy_store.db"
BACKUP_DIR = SCRIPT_DIR / "backups"

# Keep this order: later documents depend on earlier master data and orders.
MIGRATION_STAGES = (
    ("goods-category", "货品分类", "sync_goods_category.py"),
    ("supplier", "供应商", "sync_supplier.py"),
    ("customer", "客户", "sync_customer.py"),
    ("account-settle", "结算账户", "sync_account_settle.py"),
    ("goods", "货品信息", "sync_goods.py"),
    ("income-expense-item", "收支项目", "sync_income_expense_item.py"),
    ("sale-order", "销售单", "sync_sale_order.py"),
    ("purchase-order", "进货单", "sync_purchase_order.py"),
    ("receive-payment-voucher", "收款单", "sync_receive_payment_voucher.py"),
    ("payment-voucher", "付款单", "sync_payment_voucher.py"),
    ("income-expense-record", "独立收支流水", "sync_income_expense_record.py"),
)


def require_file(path: Path, label: str) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"{label} not found: {path}")


def backup_target(target: Path) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = BACKUP_DIR / f"easy_store_before_full_migration_{timestamp}.db"
    shutil.copy2(target, backup_path)
    return backup_path


def print_plan(source: Path, target: Path) -> None:
    print("Easy Store full migration plan")
    print(f"source database: {source}")
    print(f"target database: {target}")
    print("stages:")
    for index, (_, title, script_name) in enumerate(MIGRATION_STAGES, start=1):
        print(f"  {index:>2}. {title} ({script_name})")


def run_stage(script_name: str, source: Path, target: Path) -> None:
    command = [
        sys.executable,
        str(SCRIPT_DIR / script_name),
        "--source",
        str(source),
        "--target",
        str(target),
        "--execute",
        "--no-backup",
    ]
    print(f"\n>>> running: {script_name}", flush=True)
    subprocess.run(command, cwd=SCRIPT_DIR, check=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run all ZHJ-to-Easy-Store migration scripts in dependency order. "
            "Without --execute this command only prints the plan."
        )
    )
    parser.add_argument("--source", type=Path, default=SOURCE_DB)
    parser.add_argument("--target", type=Path, default=TARGET_DB)
    parser.add_argument(
        "--execute",
        action="store_true",
        help="back up the target once and run every migration stage",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="skip the single full-database backup when used with --execute",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source = args.source.resolve()
    target = args.target.resolve()
    require_file(source, "source database")
    require_file(target, "target database")
    for _, _, script_name in MIGRATION_STAGES:
        require_file(SCRIPT_DIR / script_name, "migration script")

    print_plan(source, target)
    if not args.execute:
        print("\nplan only. add --execute to start the full migration.")
        return

    if not args.no_backup:
        backup_path = backup_target(target)
        print(f"\nfull migration backup created: {backup_path}")

    for stage_id, title, script_name in MIGRATION_STAGES:
        print(f"\n[{stage_id}] {title}", flush=True)
        run_stage(script_name, source, target)

    print("\nfull migration complete.")


if __name__ == "__main__":
    main()
