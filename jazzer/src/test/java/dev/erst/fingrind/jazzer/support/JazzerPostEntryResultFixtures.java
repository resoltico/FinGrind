package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.CliFuzzSyntheticAccountFixtures;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.JournalClassifier;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.StructuralContext;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared post-entry success fixtures aligned with the current resolved-journal contract. */
public final class JazzerPostEntryResultFixtures {
  private static final Set<String> INVOICE_EVIDENCE_TYPES = Set.of("invoice", "bill");
  private static final Set<String> CASH_SETTLEMENT_EVIDENCE_TYPES =
      Set.of(
          "cash-receipt",
          "bank-deposit",
          "card-settlement",
          "expense-receipt",
          "cash-disbursement",
          "bank-payment-confirmation",
          "capital-deposit",
          "distribution-payment",
          "owner-contribution",
          "owner-withdrawal");

  private JazzerPostEntryResultFixtures() {}

  /** Returns one accepted preflight fixture for the parsed command. */
  public static PreflightAccepted preflightAccepted(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    return new PreflightAccepted(
        command.requestProvenance().idempotencyKey(),
        CliFuzzFixtures.journalEntry(command).effectiveDate(),
        resolvedJournal(command));
  }

  /** Returns one committed fixture for the parsed command. */
  public static Committed committed(
      PostEntryCommand command, String postingId, boolean idempotentReplay) {
    Objects.requireNonNull(command, "command");
    return new Committed(
        fixturePostingId(postingId),
        command.requestProvenance().idempotencyKey(),
        CliFuzzFixtures.journalEntry(command).effectiveDate(),
        CliFuzzFixtures.fixedClock().instant(),
        idempotentReplay,
        resolvedJournal(command),
        null);
  }

  /** Returns the canonical synthetic posting identifier for one stable fixture label. */
  public static PostingId fixturePostingId(String fixtureLabel) {
    String checkedFixtureLabel = Objects.requireNonNull(fixtureLabel, "fixtureLabel");
    return new PostingId(
        java.util
            .UUID
            .nameUUIDFromBytes(
                ("fingrind-test-postingid:" + checkedFixtureLabel)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString());
  }

  /** Returns the resolved-journal payload expected on current success results. */
  public static ResolvedJournal resolvedJournal(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    BookkeepingEntry entry = command.entry();
    Map<AccountCode, DeclareAccountCommand> accountsByCode =
        CliFuzzSyntheticAccountFixtures.declarePostingAccountCommands(command).stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    DeclareAccountCommand::accountCode, declaration -> declaration));
    return new ResolvedJournal(
        CliFuzzFixtures.journalEntry(command),
        appliedTax(entry),
        entry.foreignExchangeDetails(),
        JournalClassifier.classify(
            CliFuzzFixtures.journalEntry(command),
            accountCode -> accountRole(accountCode, accountsByCode),
            evidenceClass(command),
            structuralContext(entry),
            java.util.Optional.empty()));
  }

  private static AccountRole accountRole(
      AccountCode accountCode, Map<AccountCode, DeclareAccountCommand> accountsByCode) {
    DeclareAccountCommand declaration =
        Objects.requireNonNull(
            accountsByCode.get(Objects.requireNonNull(accountCode, "accountCode")),
            () -> "Missing synthetic account declaration for " + accountCode.value());
    return AccountRole.from(declaration.accountType(), declaration.accountTaxonomy());
  }

  private static EvidenceClass evidenceClass(PostEntryCommand command) {
    Set<String> encounteredTypes =
        command.evidence().sourceDocuments().stream()
            .map(SourceDocumentReference::sourceDocumentType)
            .map(type -> type.value())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    if (encounteredTypes.stream().allMatch(INVOICE_EVIDENCE_TYPES::contains)) {
      return EvidenceClass.INVOICE;
    }
    if (encounteredTypes.stream().allMatch(CASH_SETTLEMENT_EVIDENCE_TYPES::contains)) {
      return EvidenceClass.CASH_SETTLEMENT;
    }
    return EvidenceClass.OTHER;
  }

  private static StructuralContext structuralContext(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.OpeningPosition _ -> new StructuralContext(Optional.empty(), true);
      case BookkeepingEntry.Reversal reversal ->
          new StructuralContext(
              Optional.of(reversal.reversal().reference().priorPostingId()), false);
      default -> StructuralContext.ordinary();
    };
  }

  private static @Nullable AppliedTax appliedTax(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale -> sale.appliedTax();
      case BookkeepingEntry.SaleOnCredit sale -> sale.appliedTax();
      case BookkeepingEntry.ExpenseSettled expense -> expense.appliedTax();
      case BookkeepingEntry.ExpenseOnCredit expense -> expense.appliedTax();
      default -> null;
    };
  }
}
