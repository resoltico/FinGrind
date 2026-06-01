from __future__ import annotations

STANDARD_LIST_POSTINGS_TEXT = (
    "Postings\n"
    "========\n\n"
    "Returned postings : 2\n\n"
    "2026-04-08 | Direct | posting-2\n"
    "Recorded at      : 2026-04-08 10:00:00 UTC\n"
    "Debit total      : 4.00\n\n"
    "2026-04-07 | Direct | posting-1\n"
    "Recorded at      : 2026-04-07 10:00:00 UTC\n"
    "Debit total      : 10.00\n"
)
STANDARD_ACCOUNT_BALANCE_TEXT = "Account Balance\nAccount : 1000\nNet     : 6.00\n"
STANDARD_TRIAL_BALANCE_TEXT = "Trial Balance\nAs of : 2026-04-08\n1000 | 6.00\n"
STANDARD_PERIOD_SUMMARY_TEXT = "Period Summary\nPosting count : 2\n"


def pdf_export_stderr(reported_path: str) -> str:
    return (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        f"Message  : Wrote the requested PDF report artifact to {reported_path}\n"
        "Argument : --pdf-out\n"
    )
