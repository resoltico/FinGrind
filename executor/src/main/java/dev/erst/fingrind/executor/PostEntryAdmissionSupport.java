package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntryModeSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEvidenceSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.InventoryEntrySemanticsViolations;
import java.util.List;
import java.util.Set;

/** Shared admission and evidence semantics for published post-entry validation. */
final class PostEntryAdmissionSupport {
  private static final Set<BookkeepingEntryKind> TRADING_INVENTORY_ENTRY_KINDS =
      Set.of(
          BookkeepingEntryKind.PURCHASE_SETTLED,
          BookkeepingEntryKind.PURCHASE_ON_CREDIT,
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
          BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
          BookkeepingEntryKind.INVENTORY_SHRINKAGE,
          BookkeepingEntryKind.INVENTORY_COUNT_INCREASE);

  private PostEntryAdmissionSupport() {}

  static void validateEvidence(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    ClassificationResult classification = resolvedJournal.classification();
    switch (classification.eventClass()) {
      case SETTLED_SALE, SETTLED_PURCHASE, SETTLED_EXPENSE, AR_SETTLEMENT, AP_SETTLEMENT -> {
        if (classification.evidenceClass() == dev.erst.fingrind.core.EvidenceClass.INVOICE) {
          violations.add(
              BookkeepingEvidenceSemanticsViolations.evidenceClassConflict(
                  selectorField,
                  selectorValue,
                  classification.evidenceClass(),
                  classification.eventClass()));
        }
      }
      case CREDIT_SALE, CREDIT_PURCHASE, CREDIT_EXPENSE -> {
        if (classification.evidenceClass()
            == dev.erst.fingrind.core.EvidenceClass.CASH_SETTLEMENT) {
          violations.add(
              BookkeepingEvidenceSemanticsViolations.evidenceClassConflict(
                  selectorField,
                  selectorValue,
                  classification.evidenceClass(),
                  classification.eventClass()));
        }
      }
      default -> {}
    }
  }

  static void validateAdmissionByVerbAndBasis(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntryKind entryKind,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      rawAdmission(violations, accountingBasis, selectorField, selectorValue, resolvedJournal);
      return;
    }
    if (accountingBasis != AccountingBasis.CASH) {
      return;
    }
    CashBasisEntryAdmission.appendViolation(
        violations, entryKind, selectorField, selectorValue, resolvedJournal.classification());
  }

  static void validateAdmissionByVerbAndBasisBeforeResolution(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntryKind entryKind,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue) {
    if (accountingBasis != AccountingBasis.CASH
        || entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      return;
    }
    CashBasisEntryAdmission.appendViolation(
        violations, entryKind, selectorField, selectorValue, null);
  }

  static void rawAdmission(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      AccountingBasis accountingBasis,
      String selectorField,
      String selectorValue,
      ResolvedJournal resolvedJournal) {
    ClassificationResult classification = resolvedJournal.classification();
    if (classification.eventClass() == EconomicEventClass.ADJUSTMENT) {
      if (accountingBasis == AccountingBasis.CASH && !classification.hasCashLine()) {
        violations.add(
            BookkeepingEntryModeSemanticsViolations.rawJournalRequiresCashLine(
                selectorField, selectorValue));
      }
      return;
    }
    if (classification.eventClass() == EconomicEventClass.COMPOUND_OPERATIONAL) {
      violations.add(
          BookkeepingEntryModeSemanticsViolations.rawJournalBundlesOperationalEvents(
              selectorField, selectorValue, classification.containedTypedEvents()));
      return;
    }
    violations.add(
        BookkeepingEntryModeSemanticsViolations.rawJournalShadowsTypedEvent(
            selectorField,
            selectorValue,
            classification.eventClass(),
            ProtocolCatalog.operationName(
                OperationId.forEconomicEventClass(classification.eventClass()))));
  }

  static void assertVerbClass(BookkeepingEntryKind entryKind, ResolvedJournal resolvedJournal) {
    EconomicEventClass expected = expectedTypedEventClass(entryKind);
    EconomicEventClass actual = resolvedJournal.classification().eventClass();
    if (actual != expected) {
      throw new ContractFailureException(
          ContractErrors.Descriptor.INTERNAL_DEFECT.failure(
              "Typed entry kind "
                  + entryKind.wireValue()
                  + " resolved to "
                  + actual.wireValue()
                  + " instead of "
                  + expected.wireValue()
                  + ".",
              "One typed bookkeeping command built a journal that resolved to a different published event class than the command contract promised. Report the defect; rerunning the same request will not repair it.",
              null));
    }
  }

  static EconomicEventClass expectedTypedEventClass(BookkeepingEntryKind entryKind) {
    return TypedEntryEventClassCatalog.requiredEventClass(entryKind);
  }

  static boolean canResolveResolvedJournal(BookkeepingEntry entry) {
    return PostEntryInventoryTaxResolutionReadiness.readiness(entry)
        .or(() -> PostEntryDeferredResolutionReadiness.readiness(entry))
        .orElse(true);
  }

  static void appendViolationOnce(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String code,
      java.util.function.Supplier<BookkeepingPostingRejection.EntrySemanticsViolation>
          violationSupplier) {
    if (violations.stream().anyMatch(violation -> code.equals(violation.code()))) {
      return;
    }
    violations.add(violationSupplier.get());
  }

  static void validateTradingTemplateEntryAdmission(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      BookkeepingEntry entry,
      BookTemplateId bookTemplateId,
      String selectorField,
      String selectorValue) {
    boolean tradingInventoryEntry = TRADING_INVENTORY_ENTRY_KINDS.contains(entry.entryKind());
    if (tradingInventoryEntry && bookTemplateId != BookTemplateId.OWNER_MANAGED_TRADING) {
      appendViolationOnce(
          violations,
          "verb-requires-trading-template",
          () ->
              InventoryEntrySemanticsViolations.verbRequiresTradingTemplate(
                  selectorField, selectorValue, bookTemplateId));
    }

    boolean saleEntry =
        entry instanceof dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.SaleSettled
            || entry
                instanceof dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.SaleOnCredit;
    if (!saleEntry) {
      return;
    }
    boolean hasInventoryRelief =
        entry instanceof dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.SaleSettled sale
            ? sale.inventoryRelief() != null
            : ((dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry.SaleOnCredit) entry)
                    .inventoryRelief()
                != null;
    if (bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING) {
      if (!hasInventoryRelief) {
        violations.add(
            InventoryEntrySemanticsViolations.tradingSaleRequiresInventoryRelief(
                selectorField, selectorValue));
      }
      return;
    }
    if (hasInventoryRelief) {
      violations.add(
          InventoryEntrySemanticsViolations.inventoryReliefRequiresTradingBook(
              selectorField, selectorValue, bookTemplateId));
    }
  }
}
