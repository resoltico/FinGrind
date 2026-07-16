package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.JournalClassifier;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.StructuralContext;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves classifier-ready journal facts from one caller-authored entry plus declared accounts.
 */
final class ResolvedJournalSupport {
  private static final Set<String> INVOICE_EVIDENCE_TYPES =
      Set.of("invoice", "bill", "supplier-invoice");
  private static final Set<String> CASH_SETTLEMENT_EVIDENCE_TYPES =
      Set.of(
          "cash-receipt",
          "bank-deposit",
          "card-settlement",
          "purchase-receipt",
          "expense-receipt",
          "cash-disbursement",
          "bank-payment-confirmation",
          "capital-deposit",
          "distribution-payment",
          "owner-contribution",
          "owner-withdrawal");

  private ResolvedJournalSupport() {}

  static boolean canResolveAllAccounts(
      Set<AccountCode> referencedAccounts, Map<AccountCode, RegisteredAccount> accounts) {
    Objects.requireNonNull(referencedAccounts, "referencedAccounts");
    Objects.requireNonNull(accounts, "accounts");
    return accounts.keySet().containsAll(referencedAccounts);
  }

  static ResolvedJournal resolve(
      BookkeepingEntry entry,
      AccountingEvidence evidence,
      Map<AccountCode, RegisteredAccount> accountsByCode) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(accountsByCode, "accountsByCode");
    JournalEntry expandedLines = entry.journalEntry();
    EvidenceClass evidenceClass = evidenceClass(evidence);
    StructuralContext structural = structuralContext(entry);
    ClassificationResult classification =
        JournalClassifier.classify(
            expandedLines,
            accountCode -> accountRole(accountCode, accountsByCode),
            evidenceClass,
            structural,
            assertedTypedEventClass(entry));
    return new ResolvedJournal(
        expandedLines,
        TaxValidationSupport.appliedTax(entry),
        entry.foreignExchangeDetails(),
        classification);
  }

  static EvidenceClass evidenceClass(AccountingEvidence evidence) {
    Objects.requireNonNull(evidence, "evidence");
    Set<String> encounteredTypes = new LinkedHashSet<>();
    evidence
        .sourceDocuments()
        .forEach(
            sourceDocument -> encounteredTypes.add(sourceDocument.sourceDocumentType().value()));
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

  private static Optional<dev.erst.fingrind.core.EconomicEventClass> assertedTypedEventClass(
      BookkeepingEntry entry) {
    return TypedEntryEventClassCatalog.classifierAssertedEventClass(entry.entryKind());
  }

  private static AccountRole accountRole(
      AccountCode accountCode, Map<AccountCode, RegisteredAccount> accountsByCode) {
    RegisteredAccount account =
        Objects.requireNonNull(
            accountsByCode.get(Objects.requireNonNull(accountCode, "accountCode")),
            () -> "Missing declared account for " + accountCode.value());
    return AccountRole.from(account.accountType(), account.accountTaxonomy());
  }
}
