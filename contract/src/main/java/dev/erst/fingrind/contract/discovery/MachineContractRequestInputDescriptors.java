package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.InteractionLimits;
import java.util.List;

/** Builds request-input descriptors for the machine-readable contract. */
final class MachineContractRequestInputDescriptors {
  private MachineContractRequestInputDescriptors() {}

  static ContractRequestShapes.RequestInputDescriptor requestInput() {
    return new ContractRequestShapes.RequestInputDescriptor(
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.bookPassphraseOptions(),
        ProtocolOptions.REQUEST_FILE,
        List.of(
            operation(OperationId.DECLARE_ACCOUNT),
            operation(OperationId.PREFLIGHT_ENTRY),
            operation(OperationId.POST_ENTRY),
            operation(OperationId.EXECUTE_PLAN)),
        List.of(
            operation(OperationId.HELP),
            operation(OperationId.VERSION),
            operation(OperationId.CAPABILITIES),
            operation(OperationId.GENERATE_BOOK_KEY_FILE),
            operation(OperationId.OPEN_BOOK),
            operation(OperationId.REKEY_BOOK),
            operation(OperationId.CLOSE_PERIOD),
            operation(OperationId.INSPECT_BOOK),
            operation(OperationId.LIST_ACCOUNTS),
            operation(OperationId.GET_POSTING),
            operation(OperationId.LIST_POSTINGS),
            operation(OperationId.ACCOUNT_BALANCE),
            operation(OperationId.TRIAL_BALANCE),
            operation(OperationId.ACCOUNT_LEDGER),
            operation(OperationId.PERIOD_SUMMARY),
            operation(OperationId.FINANCIAL_POSITION),
            operation(OperationId.INCOME_STATEMENT),
            operation(OperationId.CHANGES_IN_EQUITY),
            operation(OperationId.PRINT_REQUEST_TEMPLATE),
            operation(OperationId.PRINT_PLAN_TEMPLATE)),
        ProtocolOptions.OUTPUT,
        List.of(
            "commands in capabilities.commands that advertise non-empty outputModes publish their interactive-terminal and redirected-stdout defaults on each command descriptor, unless callers select a different public value explicitly through --output",
            "commands listed in requestFileCommands accept one structured JSON request document through --request-file <path|->, while commands listed in directArgumentCommands accept typed CLI flags instead of a request document",
            "commands in capabilities.commands with empty outputModes still publish one fixed stdout contract through executionMode, so agents can distinguish fixed raw JSON from fixed JSON envelopes",
            "supported report commands also accept --pdf-out <path> and write one PDF artifact on successful execution without changing the command's selected stdout output mode; successful exports also report the normalized artifact path on the diagnostics stream",
            "successful discovery, administration, write, query, and report commands honor the selected output mode when they advertise one, while deterministic failures remain canonical JSON error envelopes so repair logic stays machine-readable"),
        ProtocolOptions.STDIN_TOKEN,
        "single SQLite book file for one entity",
        List.of(
            "key-file route: one UTF-8 passphrase file up to 4096 bytes for the selected encrypted book; the file must be a regular non-symlink file protected by POSIX owner-only permissions (0400 or 0600) or a Windows owner-only ACL, and it must live beneath an owner-only parent directory",
            "standard-input route: read one UTF-8 passphrase payload up to 4096 bytes from standard input; this cannot be combined with --request-file -, and "
                + operation(OperationId.REKEY_BOOK)
                + " cannot use standard input for both current and replacement secrets",
            "interactive-prompt route: prompt for the passphrase on the controlling terminal without echo; replacement prompt entry requires confirmation, and prompt input must also fit within the 4096-byte UTF-8 limit",
            "all passphrase routes strip one trailing LF or CRLF, reject empty secrets, reject control characters, and reject UTF-8 payloads larger than 4096 bytes so one secret remains reproducible across file, stdin, and prompt usage"),
        List.of(
            "request JSON must be one object document",
            "request JSON must fit within the "
                + InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
                + "-byte UTF-8 payload limit whether it comes from a file or standard input",
            "unknown request fields are rejected at every object level",
            "duplicate JSON object keys are rejected",
            "legacy forbidden fields such as correction, reversal.kind, provenance.recordedAt, and provenance.sourceChannel remain hard-broken"));
  }

  private static String operation(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }
}
