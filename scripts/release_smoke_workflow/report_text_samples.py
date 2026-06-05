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
STANDARD_ACCOUNT_BALANCE_TEXT = (
    "Account Balance\n"
    "===============\n\n"
    "Account : Cash [cash]\n"
    "Range   : book start to current book horizon\n\n"
    "Currency | Debit total | Credit total | Net amount | Balance side\n"
    "---------+-------------+--------------+------------+-------------\n"
    "EUR      |       10.00 |         4.00 |       6.00 | Debit\n\n"
    "Context\n"
    "-------\n"
    "Entity              : Acme Studio\n"
    "Starter chart       : Owner-managed service starter chart\n"
    "Functional currency : EUR\n"
    "Fiscal year start   : 01-01\n"
    "Posting coverage    : All posting kinds\n"
    "Account type        : Asset\n"
    "Account role        : Ordinary\n"
    "Normal balance      : Debit\n"
    "Active              : Yes\n"
)
STANDARD_TRIAL_BALANCE_TEXT = "Trial Balance\nAs of : 2026-04-08\ncash | 6.00\n"
STANDARD_PERIOD_SUMMARY_TEXT = "Period Summary\nPosting count : 2\n"


def pdf_export_stderr(reported_path: str) -> str:
    return (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        f"Message  : Wrote the requested PDF report artifact to {reported_path}\n"
        "Argument : --pdf-out\n"
    )
