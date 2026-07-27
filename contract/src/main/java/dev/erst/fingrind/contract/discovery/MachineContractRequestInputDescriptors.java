package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptionSyntax;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Builds request-input descriptors for the machine-readable contract. */
final class MachineContractRequestInputDescriptors {
  private MachineContractRequestInputDescriptors() {}

  static ContractRequestShapes.RequestInputDescriptor requestInput() {
    return new ContractRequestShapes.RequestInputDescriptor(
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolOptionSyntax.BookAccess.bookPassphraseOptions(),
        ProtocolOptions.Request.FILE,
        LedgerStepKind.requestFileOperationIds().stream()
            .map(MachineContractRequestInputDescriptors::operation)
            .toList(),
        List.of(
            operation(OperationId.HELP),
            operation(OperationId.VERSION),
            operation(OperationId.CAPABILITIES),
            operation(OperationId.GENERATE_BOOK_KEY_FILE),
            operation(OperationId.GENERATE_ATTESTATION_KEY_FILE),
            operation(OperationId.INSPECT_ATTESTATION_KEY_FILE),
            operation(OperationId.OPEN_BOOK),
            operation(OperationId.REKEY_BOOK),
            operation(OperationId.INTERIM_RESULT_SWEEP),
            operation(OperationId.FISCAL_YEAR_CLOSE),
            operation(OperationId.INSPECT_BOOK),
            operation(OperationId.LIST_ACCOUNTS),
            operation(OperationId.GET_POSTING),
            operation(OperationId.LIST_POSTINGS),
            operation(OperationId.LIST_TAX_REGISTRATIONS),
            operation(OperationId.TAX_OBLIGATION),
            operation(OperationId.ACCOUNT_BALANCE),
            operation(OperationId.TRIAL_BALANCE),
            operation(OperationId.ACCOUNT_LEDGER),
            operation(OperationId.PERIOD_SUMMARY),
            operation(OperationId.FINANCIAL_POSITION),
            operation(OperationId.INCOME_STATEMENT),
            operation(OperationId.CHANGES_IN_EQUITY),
            operation(OperationId.PRINT_REQUEST_TEMPLATE),
            operation(OperationId.PRINT_PLAN_TEMPLATE)),
        ProtocolOptions.Presentation.OUTPUT,
        List.of(
            "commands that advertise selectable output modes default to text; an explicit --output on one command selects json, text, or csv where the command supports it",
            "commands in capabilities.commands that advertise non-empty outputModes publish their interactive-terminal and redirected-stdout defaults on each command descriptor, unless callers select a different public value explicitly through --output",
            "commands listed in requestFileCommands accept one structured JSON request document through --request-file <path|->, while commands listed in directArgumentCommands accept typed CLI flags instead of a request document",
            "commands in capabilities.commands with empty outputModes still publish one fixed stdout contract through executionMode, so agents can distinguish fixed raw JSON from fixed JSON envelopes",
            "supported report commands also accept --pdf-out <path>; PDF artifact creation is part "
                + "of the requested operation, so artifact-write failures return one deterministic "
                + "non-zero failure envelope instead of a successful report result, while successful "
                + "JSON exports publish the canonical physical artifact path and retainedStage under artifacts[], "
                + "successful text exports replace the full report body with one artifact confirmation "
                + "block on stdout, and --output csv cannot be combined with --pdf-out",
            "successful discovery, administration, write, query, and report commands honor the selected output mode when they advertise one; deterministic failures use JSON only for a valid explicit --output json and otherwise use the canonical text diagnostics renderer"),
        ProtocolOptions.Request.STDIN_TOKEN,
        "single SQLite book file for one entity",
        ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES,
        List.of(
            "key-file route: one UTF-8 passphrase file up to "
                + ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES
                + " bytes for the selected encrypted book; the file must be a regular non-symlink file protected by POSIX owner-only permissions (0400 or 0600) or a Windows owner-only ACL, and it must live beneath an owner-only parent directory",
            "standard-input route: read one UTF-8 passphrase payload up to "
                + ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES
                + " bytes from standard input; this cannot be combined with --request-file -, and "
                + operation(OperationId.REKEY_BOOK)
                + " cannot use standard input for both current and replacement secrets",
            "interactive-prompt route: prompt for the passphrase on the controlling terminal without echo; replacement prompt entry requires confirmation, and prompt input must also fit within the "
                + ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES
                + "-byte UTF-8 limit",
            "all passphrase routes strip one trailing LF or CRLF, reject empty secrets, reject control characters, and reject UTF-8 payloads larger than "
                + ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES
                + " bytes so one secret remains reproducible across file, stdin, and prompt usage"),
        ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES,
        List.of(
            "request JSON must be one object document",
            "request JSON must fit within the "
                + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
                + "-byte UTF-8 payload limit whether it comes from a file or standard input",
            "unknown request fields are rejected at every object level",
            "duplicate JSON object keys are rejected",
            "legacy forbidden fields such as correction, reversal.kind, provenance.recordedAt, and provenance.sourceChannel remain hard-broken"));
  }

  private static String operation(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }
}
