#!/usr/bin/env bash
# Shared runtime and fixture helpers for public container verification.

anonymous_docker() {
    docker --config "${docker_config_dir}" "$@"
}

mounted_container_run() {
    local image_ref=$1
    shift

    anonymous_docker run --rm --user "${docker_run_user}" -v "${report_root}:/work" "${image_ref}" "$@"
}

container_shell() {
    local image_ref=$1
    local shell_command=$2

    anonymous_docker run --rm --entrypoint /bin/sh "${image_ref}" -c "${shell_command}"
}

require_nonempty_container_file() {
    local image_ref=$1
    local container_path=$2
    local file_label=$3

    if ! container_shell "${image_ref}" "test -s '${container_path}'"; then
        die "published container ${image_ref} did not expose a non-empty ${file_label} at ${container_path}"
    fi
}

seed_public_fixture() {
    cat > "${report_root}/declare-cash.json" <<'JSON'
{"accountCode":"cash-reserve","accountName":"Cash Reserve","accountType":"ASSET","accountRole":"ORDINARY","accountNodeKind":"POSTABLE","financialPositionLineClassification":"CURRENT_ASSET","profitAndLossLineClassification":null}
JSON

    cat > "${report_root}/declare-revenue.json" <<'JSON'
{"accountCode":"misc-revenue","accountName":"Misc Revenue","accountType":"REVENUE","accountRole":"ORDINARY","accountNodeKind":"POSTABLE","financialPositionLineClassification":null,"profitAndLossLineClassification":"OTHER_REVENUE"}
JSON

cat > "${report_root}/posting.json" <<'JSON'
{
  "entryKind": "CASH_REVENUE",
  "effectiveDate": "2026-04-08",
  "cashAccountCode": "cash",
  "revenueAccountCode": "service-revenue",
  "amount": {
    "currencyCode": "EUR",
    "minorUnits": "1000"
  },
  "evidence": {
    "sourceDocuments": [
      {
        "sourceDocumentId": "release-protocol-cash-receipt-1",
        "sourceDocumentType": "cash-receipt",
        "documentDate": "2026-04-08",
        "capturedAt": "2026-04-08T10:15:30Z",
        "storageLocator": "vault://release-protocol/cash-receipt-1",
        "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      }
    ],
    "approvals": []
  },
  "provenance": {
    "actorId": "release-protocol",
    "actorType": "AGENT",
    "commandId": "release-protocol-posting",
    "idempotencyKey": "release-protocol-idem-1",
    "causationId": "release-protocol-cause-1"
  }
}
JSON
}
