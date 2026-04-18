package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.BookModelFacts;
import dev.erst.fingrind.contract.protocol.CurrencyFacts;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanExecutionFacts;
import dev.erst.fingrind.contract.protocol.PreflightFacts;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;

/** Shared descriptor builders for the public machine-readable FinGrind contract. */
final class MachineContractSupport {
  private MachineContractSupport() {}

  static ContractResponse.BookModelDescriptor bookModel() {
    BookModelFacts bookModel = ProtocolCatalog.bookModel();
    return new ContractResponse.BookModelDescriptor(
        bookModel.boundary(),
        bookModel.entityScope(),
        bookModel.filesystem(),
        bookModel.credential(),
        bookModel.initialization(),
        bookModel.accountRegistry(),
        bookModel.migration(),
        bookModel.currencyScope());
  }

  static List<ContractDiscovery.CommandDescriptor> commandDescriptors() {
    return ProtocolCatalog.operations().stream()
        .map(MachineContractSupport::commandDescriptor)
        .toList();
  }

  private static ContractDiscovery.CommandDescriptor commandDescriptor(
      ProtocolOperation operation) {
    return new ContractDiscovery.CommandDescriptor(
        operation.id().wireName(),
        operation.aliases(),
        operation.options(),
        operation.executionMode().wireValue(),
        operation.analysisSummary());
  }

  static List<ContractDiscovery.ExitCodeDescriptor> exitCodes() {
    return List.of(
        new ContractDiscovery.ExitCodeDescriptor(0, "successful command"),
        new ContractDiscovery.ExitCodeDescriptor(1, "runtime or environment failure"),
        new ContractDiscovery.ExitCodeDescriptor(2, "invalid request or deterministic rejection"),
        new ContractDiscovery.ExitCodeDescriptor(
            3, "valid execute-plan request whose assertion step failed"));
  }

  static ContractRequestShapes.RequestInputDescriptor requestInput() {
    return new ContractRequestShapes.RequestInputDescriptor(
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.bookPassphraseOptions(),
        ProtocolOptions.REQUEST_FILE,
        ProtocolOptions.STDIN_TOKEN,
        "single SQLite book file for one entity",
        List.of(
            "key-file route: one UTF-8 passphrase file for the selected encrypted book; the file must be a regular non-symlink file protected by POSIX owner-only permissions (0400 or 0600) or a Windows owner-only ACL",
            "standard-input route: read one UTF-8 passphrase payload from standard input; this cannot be combined with --request-file -, and rekey-book cannot use standard input for both current and replacement secrets",
            "interactive-prompt route: prompt for the passphrase on the controlling terminal without echo; replacement prompt entry requires confirmation",
            "all passphrase routes strip one trailing LF or CRLF, reject empty secrets, and reject control characters so one secret remains reproducible across file, stdin, and prompt usage"),
        List.of(
            "request JSON must be one object document",
            "unknown request fields are rejected at every object level",
            "duplicate JSON object keys are rejected",
            "legacy forbidden fields such as correction, reversal.kind, provenance.recordedAt, and provenance.sourceChannel remain hard-broken"));
  }

  static ContractRequestShapes.RequestShapesDescriptor requestShapes() {
    return new ContractRequestShapes.RequestShapesDescriptor(
        postEntryShape(), declareAccountShape(), ledgerPlanShape());
  }

  private static ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape() {
    return new ContractRequestShapes.PostEntryRequestShapeDescriptor(
        List.of(
            requiredField(
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                "ISO-8601 local date that makes the journal entry effective."),
            requiredField(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one currency."),
            requiredField(
                ProtocolPostEntryFields.TopLevel.PROVENANCE,
                "Caller-supplied request provenance captured before commit."),
            optionalField(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Optional reversal target descriptor for additive reversal postings."),
            forbiddenField(
                ProtocolPostEntryFields.TopLevel.CORRECTION,
                "Legacy correction payloads are hard-broken and no longer accepted.")),
        List.of(
            requiredField(
                ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE,
                "Declared book-local account code for this journal line."),
            requiredField(
                ProtocolPostEntryFields.JournalLine.SIDE,
                "Journal side that carries the line amount."),
            requiredField(
                ProtocolPostEntryFields.JournalLine.CURRENCY_CODE,
                "Three-letter ISO currency code shared by every line in the entry."),
            requiredField(
                ProtocolPostEntryFields.JournalLine.AMOUNT,
                "Plain decimal string greater than zero without exponent notation.")),
        List.of(
            requiredField(
                ProtocolPostEntryFields.Provenance.ACTOR_ID,
                "Stable identifier of the actor that initiated the request."),
            requiredField(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
                "Actor classification from the live actorType enum vocabulary."),
            requiredField(
                ProtocolPostEntryFields.Provenance.COMMAND_ID,
                "Caller-generated command identity for this request."),
            requiredField(
                ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
                "Book-local idempotency key used to detect duplicate commit attempts."),
            requiredField(
                ProtocolPostEntryFields.Provenance.CAUSATION_ID,
                "Caller-supplied causation identifier for upstream traceability."),
            optionalField(
                ProtocolPostEntryFields.Provenance.CORRELATION_ID,
                "Optional correlation identifier for joining related calls."),
            forbiddenField(
                ProtocolPostEntryFields.Provenance.RECORDED_AT,
                "Committed audit timestamps are generated by FinGrind, not caller input."),
            forbiddenField(
                ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
                "Committed source channel is generated by FinGrind, not caller input.")),
        List.of(
            requiredField(
                ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID,
                "Existing committed posting identifier that this request reverses."),
            requiredField(
                ProtocolPostEntryFields.Reversal.REASON,
                "Human-readable operator explanation attached to this reversal."),
            forbiddenField(
                ProtocolPostEntryFields.Reversal.KIND,
                "Legacy reversal-kind routing is removed; FinGrind is reversal-only.")),
        List.of(
            enumVocabulary("lineSide", JournalLine.EntrySide.wireValues()),
            enumVocabulary("actorType", ActorType.wireValues())));
  }

  private static ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccountShape() {
    return new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
        List.of(
            requiredField(
                ProtocolDeclareAccountFields.ACCOUNT_CODE,
                "Book-local account code used by journal lines."),
            requiredField(
                ProtocolDeclareAccountFields.ACCOUNT_NAME,
                "Non-blank display name for the declared account."),
            requiredField(
                ProtocolDeclareAccountFields.NORMAL_BALANCE,
                "Side of the journal equation that increases the account.")),
        List.of(enumVocabulary("normalBalance", NormalBalance.wireValues())));
  }

  private static ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape() {
    return new ContractRequestShapes.LedgerPlanRequestShapeDescriptor(
        List.of(
            requiredField(
                ProtocolLedgerPlanFields.Plan.PLAN_ID, "Caller-supplied plan identifier."),
            requiredField(
                ProtocolLedgerPlanFields.Plan.STEPS,
                "Ordered non-empty array of executable ledger-plan steps.")),
        List.of(
            requiredField(
                ProtocolLedgerPlanFields.Step.STEP_ID,
                "Caller-supplied step identifier unique inside the plan."),
            requiredField(
                ProtocolLedgerPlanFields.Step.KIND,
                "Canonical ledger-plan step kind for this step."),
            optionalField(
                ProtocolLedgerPlanFields.Step.POSTING,
                "Posting request payload for post-entry and preflight-entry steps."),
            optionalField(
                ProtocolLedgerPlanFields.Step.DECLARE_ACCOUNT,
                "Account declaration payload for declare-account steps."),
            optionalField(
                ProtocolLedgerPlanFields.Step.QUERY,
                "Query payload for list and account-balance steps."),
            optionalField(
                ProtocolLedgerPlanFields.Step.ASSERTION,
                "Assertion payload for first-class assertion steps."),
            optionalField(
                ProtocolLedgerPlanFields.Step.POSTING_ID,
                "Posting identifier for get-posting steps.")),
        List.of(
            optionalField(
                ProtocolLedgerPlanFields.Query.ACCOUNT_CODE,
                "Optional account filter for list-postings, required account for account-balance."),
            optionalField(
                ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_FROM,
                "Inclusive ISO-8601 effective-date lower bound."),
            optionalField(
                ProtocolLedgerPlanFields.Query.EFFECTIVE_DATE_TO,
                "Inclusive ISO-8601 effective-date upper bound."),
            optionalField(ProtocolLedgerPlanFields.Query.LIMIT, "Page size for list steps."),
            optionalField(ProtocolLedgerPlanFields.Query.OFFSET, "Page offset for list steps.")),
        List.of(
            requiredField(
                ProtocolLedgerPlanFields.Assertion.KIND,
                "Canonical assertion kind nested inside an assert step."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.ACCOUNT_CODE,
                "Account code consumed by account assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.POSTING_ID,
                "Posting identifier consumed by posting-existence assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_FROM,
                "Inclusive ISO-8601 effective-date lower bound for balance assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.EFFECTIVE_DATE_TO,
                "Inclusive ISO-8601 effective-date upper bound for balance assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.CURRENCY_CODE,
                "Currency bucket expected by account-balance assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.NET_AMOUNT,
                "Plain decimal expected net amount for account-balance assertions."),
            optionalField(
                ProtocolLedgerPlanFields.Assertion.BALANCE_SIDE,
                "Expected normal-balance side for account-balance assertions.")),
        List.of(LedgerStepKind.OPEN_BOOK.wireValue(), LedgerStepKind.DECLARE_ACCOUNT.wireValue()),
        List.of(
            LedgerStepKind.INSPECT_BOOK.wireValue(),
            LedgerStepKind.LIST_ACCOUNTS.wireValue(),
            LedgerStepKind.GET_POSTING.wireValue(),
            LedgerStepKind.LIST_POSTINGS.wireValue(),
            LedgerStepKind.ACCOUNT_BALANCE.wireValue()),
        List.of(LedgerStepKind.PREFLIGHT_ENTRY.wireValue(), LedgerStepKind.POST_ENTRY.wireValue()),
        LedgerStepKind.ASSERT.wireValue(),
        LedgerAssertionKind.wireValues(),
        planExecution());
  }

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return new ContractResponse.ResponseModelDescriptor(
        ProtocolCatalog.successStatuses(),
        ProtocolCatalog.rejectionStatuses(),
        ProtocolStatuses.ERROR,
        rejectionDescriptors(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "idempotencyKey", "The caller-supplied idempotency key from the rejected request."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "status", "Literal runtime or invalid-request error status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine error code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the error."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing the invocation."),
            new ContractResponse.FieldDescriptor(
                "argument", "Optional argument name associated with the failure.")));
  }

  private static List<ContractResponse.RejectionDescriptor> rejectionDescriptors() {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(
                BookAdministrationRejection.descriptors().stream(),
                BookQueryRejection.descriptors().stream()),
            PostingRejection.descriptors().stream())
        .toList();
  }

  static ContractResponse.AuditDescriptor audit() {
    return new ContractResponse.AuditDescriptor(
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_ID,
                "Stable identifier of the actor that initiated the request."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.ACTOR_TYPE,
                "Actor classification from the live actorType enum vocabulary."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.COMMAND_ID,
                "Caller-generated command identity for this request."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.IDEMPOTENCY_KEY,
                "Book-local idempotency key supplied by the caller."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.CAUSATION_ID,
                "Caller-supplied causation identifier."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.CORRELATION_ID,
                "Optional caller-supplied correlation identifier.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.RECORDED_AT,
                "Commit timestamp generated by FinGrind at durable write time."),
            new ContractResponse.FieldDescriptor(
                ProtocolPostEntryFields.Provenance.SOURCE_CHANNEL,
                "Committed request channel generated by FinGrind.")));
  }

  static ContractResponse.AccountRegistryDescriptor accountRegistry() {
    return new ContractResponse.AccountRegistryDescriptor(
        true,
        "redeclaration may update the display name and reactivate an inactive account, but will not amend normalBalance",
        List.of(
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_CODE, "Book-local account code to declare."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.ACCOUNT_NAME,
                "Non-blank display name for the account."),
            new ContractResponse.FieldDescriptor(
                ProtocolDeclareAccountFields.NORMAL_BALANCE,
                "Journal side that increases the account.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "accountCode", "Declared book-local account code."),
            new ContractResponse.FieldDescriptor(
                "accountName", "Current display name of the account."),
            new ContractResponse.FieldDescriptor("normalBalance", "Declared normal balance side."),
            new ContractResponse.FieldDescriptor(
                "active", "Whether the account currently accepts postings."),
            new ContractResponse.FieldDescriptor(
                "declaredAt", "Clock timestamp of the first declaration.")),
        List.of(enumVocabulary("normalBalance", NormalBalance.wireValues())));
  }

  static ContractResponse.ReversalDescriptor reversals() {
    return new ContractResponse.ReversalDescriptor(
        "reversal-only",
        List.of(
            "book-must-be-initialized",
            "every-line-account-must-be-declared-and-active",
            "target-must-exist-in-book",
            "reversal-object-must-carry-human-readable-reason",
            "one-reversal-per-target",
            "reversal-must-negate-target"));
  }

  static ContractResponse.PreflightDescriptor preflight() {
    PreflightFacts preflight = ProtocolCatalog.preflight();
    return new ContractResponse.PreflightDescriptor(
        preflight.semantics(), preflight.commitGuarantee(), preflight.description());
  }

  static ContractResponse.CurrencyDescriptor currencyModel() {
    CurrencyFacts currency = ProtocolCatalog.currency();
    return new ContractResponse.CurrencyDescriptor(
        currency.scope(), currency.multiCurrencyStatus(), currency.description());
  }

  static ContractResponse.PlanExecutionDescriptor planExecution() {
    PlanExecutionFacts facts = ProtocolCatalog.planExecution();
    return new ContractResponse.PlanExecutionDescriptor(
        facts.transactionMode(), facts.failurePolicy(), facts.journal(), facts.hardLimitations());
  }

  private static ContractRequestShapes.EnumVocabularyDescriptor enumVocabulary(
      String name, List<String> values) {
    return new ContractRequestShapes.EnumVocabularyDescriptor(name, values);
  }

  private static ContractRequestShapes.RequestFieldDescriptor requiredField(
      String name, String description) {
    return new ContractRequestShapes.RequestFieldDescriptor(name, "required", description);
  }

  private static ContractRequestShapes.RequestFieldDescriptor optionalField(
      String name, String description) {
    return new ContractRequestShapes.RequestFieldDescriptor(name, "optional", description);
  }

  private static ContractRequestShapes.RequestFieldDescriptor forbiddenField(
      String name, String description) {
    return new ContractRequestShapes.RequestFieldDescriptor(name, "forbidden", description);
  }
}
