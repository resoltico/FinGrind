from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import stat
import sys


def normalized_contains(args: argparse.Namespace) -> int:
    needle = re.sub(r"\s+", " ", args.needle).strip()
    haystack = re.sub(
        r"\s+",
        " ",
        pathlib.Path(args.file_path).read_text(encoding="utf-8"),
    ).strip()
    return 0 if needle in haystack else 1


def contract_value(args: argparse.Namespace) -> int:
    contract = json.load(sys.stdin)
    print(contract["runtimeSurface"][args.field])
    return 0


def assert_runtime_environment(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    payload = document["payload"]
    distribution = payload["runtime"]["runtimeDistribution"]
    runtime = payload["sqlite"]["runtime"]
    if distribution != args.expected_distribution:
        raise SystemExit(
            f"unexpected {args.label} runtime distribution: "
            f"{distribution} != {args.expected_distribution}"
        )
    status = runtime.get("status")
    if status != args.expected_status:
        raise SystemExit(
            f"unexpected {args.label} runtime status: {status!r} != {args.expected_status!r}"
        )
    if args.expected_provenance is not None:
        provenance = runtime.get("runtimeProvenance")
        if provenance != args.expected_provenance:
            raise SystemExit(
                f"unexpected {args.label} runtime provenance: "
                f"{provenance!r} != {args.expected_provenance}"
            )
    if args.issue_substring:
        issue = runtime.get("runtimeIssue", "")
        if not any(fragment in issue for fragment in args.issue_substring):
            raise SystemExit(
                f"{args.label} runtime issue omitted supported-launcher repair guidance"
            )
    return 0


def managed_runtime_failure_exit(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    payload = document["payload"]
    if payload["detail"] != "full":
        raise SystemExit("source-checkout launcher capabilities did not expose the full contract")
    response_model = payload["fullContract"]["responseModel"]
    for descriptor in response_model["errorDescriptors"]:
        if descriptor["code"] != "managed-runtime-failure":
            continue
        exit_code = descriptor.get("exitCode")
        if not isinstance(exit_code, int) or isinstance(exit_code, bool) or exit_code < 0:
            raise SystemExit(
                "source-checkout launcher capabilities published an invalid "
                "managed-runtime-failure exitCode"
            )
        print(exit_code)
        return 0
    raise SystemExit(
        "source-checkout launcher capabilities omitted the managed-runtime-failure error descriptor"
    )


def assert_request_template(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    if document["entryKind"] != "CASH_REVENUE":
        raise SystemExit(f"{args.label} request template did not expose CASH_REVENUE")
    if "postingKind" in document:
        raise SystemExit(f"{args.label} request template leaked retired postingKind")
    if args.forbid_lines and "lines" in document:
        raise SystemExit(f"{args.label} request template leaked retired journal lines")
    if args.require_evidence_fields:
        evidence = document["evidence"]["sourceDocuments"][0]
        for required_field in ("documentDate", "capturedAt", "storageLocator", "contentSha256"):
            if required_field not in evidence:
                raise SystemExit(
                    f"{args.label} request template omitted retained evidence field {required_field}"
                )
    return 0


def assert_plan_template(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    open_book = document["steps"][0]["openBook"]
    if "businessActivityTags" in open_book:
        raise SystemExit(
            "source-checkout launcher plan template leaked retired business activity tags"
        )
    if "accountingBasis" in open_book:
        raise SystemExit(
            "source-checkout launcher plan template leaked doctrine-owned identity fields into open-book input"
        )
    post_entry = document["steps"][1]["posting"]
    if post_entry["entryKind"] != "CASH_REVENUE":
        raise SystemExit("source-checkout launcher plan template did not expose typed post-entry")
    if "postingKind" in post_entry:
        raise SystemExit("source-checkout launcher plan template leaked retired postingKind")
    assertion = document["steps"][2]["assertion"]
    if assertion["accountCode"] != "cash":
        raise SystemExit(
            "source-checkout launcher plan template did not target the seeded cash account"
        )
    return 0


def corrupt_runtime_manifest(args: argparse.Namespace) -> int:
    manifest_path = pathlib.Path(args.manifest)
    lines = manifest_path.read_text(encoding="utf-8").splitlines()
    for index, line in enumerate(lines):
        if line.startswith("javaExecutable\t"):
            lines[index] = "javaExecutable\t/definitely/missing/fingrind-java"
            manifest_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
            return 0
    raise SystemExit("source-checkout runtime manifest omitted javaExecutable")


def assert_status_ok(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    if document["status"] != "ok":
        raise SystemExit(f"{args.label} did not return ok")
    return 0


def assert_owner_only_parent(args: argparse.Namespace) -> int:
    path = pathlib.Path(args.path)
    parent_mode = stat.S_IMODE(os.stat(path.parent).st_mode)
    if parent_mode != 0o700:
        raise SystemExit(f"{args.label} did not create an owner-only parent directory")
    return 0


def assert_open_book(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    payload = document["payload"]
    book_identity = payload["bookIdentity"]
    if document["status"] != "ok":
        raise SystemExit(f"{args.label} open-book did not return ok")
    if book_identity["entityName"] != args.entity_name:
        raise SystemExit(f"{args.label} open-book returned the wrong entity name")
    if book_identity["accountingKernelProfile"] != "internal-management-cash-bookkeeping-kernel":
        raise SystemExit(f"{args.label} open-book returned the wrong accounting kernel")
    if book_identity["accountingBasis"] != "CASH_BASIS":
        raise SystemExit(f"{args.label} open-book returned the wrong accounting basis")
    if book_identity["accountingFrameworkPosition"] != "NON_STATUTORY_INTERNAL_MANAGEMENT":
        raise SystemExit(f"{args.label} open-book returned the wrong framework posture")
    if book_identity["entityForm"] != "OWNER_MANAGED_SINGLE_ENTITY":
        raise SystemExit(f"{args.label} open-book returned the wrong entity form")
    if book_identity["bookTemplateId"] != "OWNER_MANAGED_SERVICE_CASH":
        raise SystemExit(f"{args.label} open-book returned the wrong book template")
    if book_identity["functionalCurrency"] != args.functional_currency:
        raise SystemExit(f"{args.label} open-book returned the wrong functional currency")
    if book_identity["fiscalYearStart"] != args.fiscal_year_start:
        raise SystemExit(f"{args.label} open-book returned the wrong fiscal year start")
    return 0


def assert_runtime_failure_envelope(args: argparse.Namespace) -> int:
    document = json.loads(pathlib.Path(args.document).read_text(encoding="utf-8"))
    if document["status"] != "error":
        raise SystemExit("raw java -jar open-book did not fail with an error envelope")
    if document["code"] != "managed-runtime-failure":
        raise SystemExit(
            "raw java -jar open-book did not classify the failure as managed-runtime-failure"
        )
    message = document["message"]
    hint = document.get("hint", "")
    if (
        ":cli:prepareSourceCheckoutCliRuntime" not in message
        and ":cli:prepareSourceCheckoutCliRuntime" not in hint
    ):
        raise SystemExit(
            "raw java -jar open-book did not report the source-checkout runtime recovery path"
        )
    if "supported launchers" not in message and "supported launchers" not in hint:
        raise SystemExit(
            "raw java -jar open-book did not direct the operator toward supported launchers"
        )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Shared Python assertions for source-checkout launcher shell regressions."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    normalized = subparsers.add_parser("normalized-contains")
    normalized.add_argument("needle")
    normalized.add_argument("file_path")
    normalized.set_defaults(handler=normalized_contains)

    contract = subparsers.add_parser("contract-value")
    contract.add_argument(
        "field",
        choices=("sourceCheckoutRuntimeDistribution", "directJavaRuntimeDistribution"),
    )
    contract.set_defaults(handler=contract_value)

    runtime = subparsers.add_parser("assert-runtime-environment")
    runtime.add_argument("--document", required=True)
    runtime.add_argument("--expected-distribution", required=True)
    runtime.add_argument("--expected-status", required=True)
    runtime.add_argument("--label", required=True)
    runtime.add_argument("--expected-provenance")
    runtime.add_argument("--issue-substring", action="append", default=[])
    runtime.set_defaults(handler=assert_runtime_environment)

    exit_code = subparsers.add_parser("managed-runtime-failure-exit")
    exit_code.add_argument("document")
    exit_code.set_defaults(handler=managed_runtime_failure_exit)

    request_template = subparsers.add_parser("assert-request-template")
    request_template.add_argument("--document", required=True)
    request_template.add_argument("--label", required=True)
    request_template.add_argument("--forbid-lines", action="store_true")
    request_template.add_argument("--require-evidence-fields", action="store_true")
    request_template.set_defaults(handler=assert_request_template)

    plan_template = subparsers.add_parser("assert-plan-template")
    plan_template.add_argument("document")
    plan_template.set_defaults(handler=assert_plan_template)

    manifest = subparsers.add_parser("corrupt-runtime-manifest")
    manifest.add_argument("manifest")
    manifest.set_defaults(handler=corrupt_runtime_manifest)

    status_ok = subparsers.add_parser("assert-status-ok")
    status_ok.add_argument("--document", required=True)
    status_ok.add_argument("--label", required=True)
    status_ok.set_defaults(handler=assert_status_ok)

    parent = subparsers.add_parser("assert-owner-only-parent")
    parent.add_argument("--path", required=True)
    parent.add_argument("--label", required=True)
    parent.set_defaults(handler=assert_owner_only_parent)

    open_book = subparsers.add_parser("assert-open-book")
    open_book.add_argument("--document", required=True)
    open_book.add_argument("--label", required=True)
    open_book.add_argument("--entity-name", required=True)
    open_book.add_argument("--functional-currency", required=True)
    open_book.add_argument("--fiscal-year-start", required=True)
    open_book.set_defaults(handler=assert_open_book)

    failure = subparsers.add_parser("assert-runtime-failure-envelope")
    failure.add_argument("document")
    failure.set_defaults(handler=assert_runtime_failure_envelope)

    return parser


def main(argv: list[str]) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.handler(args)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
