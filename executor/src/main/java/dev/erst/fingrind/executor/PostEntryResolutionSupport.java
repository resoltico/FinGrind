package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.FinancingAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.InventoryAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.InventoryPostingResolution;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RealizedForeignExchangeAdmissionPolicy;
import java.util.List;
import java.util.Optional;

/** Post-tax entry resolution owner for typed-entry and reversal request expansion. */
public final class PostEntryResolutionSupport {
  private static final InventoryAdmissionPolicy INVENTORY_ADMISSION_POLICY =
      new InventoryAdmissionPolicy();
  private static final AccrualCutoffAdmissionPolicy ACCRUAL_CUTOFF_ADMISSION_POLICY =
      new AccrualCutoffAdmissionPolicy();
  private static final LatvianPayrollAdmissionPolicy LATVIAN_PAYROLL_ADMISSION_POLICY =
      new LatvianPayrollAdmissionPolicy();
  private static final LatvianPayrollSettlementAdmissionPolicy
      LATVIAN_PAYROLL_SETTLEMENT_ADMISSION_POLICY = new LatvianPayrollSettlementAdmissionPolicy();
  private static final FixedAssetAdmissionPolicy FIXED_ASSET_ADMISSION_POLICY =
      new FixedAssetAdmissionPolicy();
  private static final FinancingAdmissionPolicy FINANCING_ADMISSION_POLICY =
      new FinancingAdmissionPolicy();
  private static final RealizedForeignExchangeAdmissionPolicy
      REALIZED_FOREIGN_EXCHANGE_ADMISSION_POLICY = new RealizedForeignExchangeAdmissionPolicy();

  private PostEntryResolutionSupport() {}

  /** Resolves tax, reversal, and inventory-owned facts for one caller-authored entry. */
  public static ResolutionOutcome resolve(BookkeepingEntry entry, PostingValidationStore book) {
    return resolveAfterTaxValidation(entry, book, List.of(), 0);
  }

  static ResolutionOutcome resolveAfterTaxValidation(
      BookkeepingEntry entry,
      PostingValidationStore book,
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      int violationCountBeforeTaxValidation) {
    if (violations.size() != violationCountBeforeTaxValidation) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(entry), Optional.empty());
    }
    InventoryPostingResolution preTaxInventoryResolution;
    try {
      preTaxInventoryResolution =
          TaxPostingResolution.requiresInventoryQuantityResolution(entry)
              ? INVENTORY_ADMISSION_POLICY.resolve(entry, book)
              : InventoryPostingResolution.withoutInventory(entry);
    } catch (InventoryAdmissionPolicy.InventoryAdmissionFailure failure) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(entry), Optional.of(failure.rejection()));
    }
    BookkeepingEntry resolvedEntry =
        TaxPostingResolution.resolve(preTaxInventoryResolution.resolvedEntry(), book);
    if (!violations.isEmpty()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(resolvedEntry), Optional.empty());
    }
    Optional<BookkeepingPostingRejection> reversalRejection =
        reversalResolutionRejection(resolvedEntry, book);
    if (reversalRejection.isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(resolvedEntry), reversalRejection);
    }
    BookkeepingEntry inventoryScopedEntry = resolvedReversalEntry(resolvedEntry, book);
    AccrualCutoffAdmissionPolicy.Resolution accrualCutoffResolution =
        ACCRUAL_CUTOFF_ADMISSION_POLICY.resolve(
            inventoryScopedEntry, book, inventoryScopedEntry.entryKind().wireValue());
    if (accrualCutoffResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(inventoryScopedEntry),
          accrualCutoffResolution.rejection());
    }
    BookkeepingEntry accrualCutoffScopedEntry =
        java.util.Objects.requireNonNull(
            accrualCutoffResolution.entry(), "accepted accrual cut-off resolution entry");
    LatvianPayrollAdmissionPolicy.Resolution payrollResolution =
        LATVIAN_PAYROLL_ADMISSION_POLICY.resolve(
            accrualCutoffScopedEntry, book, accrualCutoffScopedEntry.entryKind().wireValue());
    if (payrollResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(accrualCutoffScopedEntry),
          payrollResolution.rejection());
    }
    BookkeepingEntry payrollScopedEntry =
        java.util.Objects.requireNonNull(
            payrollResolution.entry(), "accepted Latvian payroll resolution entry");
    LatvianPayrollSettlementAdmissionPolicy.Resolution payrollSettlementResolution =
        LATVIAN_PAYROLL_SETTLEMENT_ADMISSION_POLICY.resolve(
            payrollScopedEntry, book, payrollScopedEntry.entryKind().wireValue());
    if (payrollSettlementResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(payrollScopedEntry),
          payrollSettlementResolution.rejection());
    }
    BookkeepingEntry payrollSettlementScopedEntry =
        java.util.Objects.requireNonNull(
            payrollSettlementResolution.entry(),
            "accepted Latvian payroll settlement resolution entry");
    FixedAssetAdmissionPolicy.Resolution fixedAssetResolution =
        FIXED_ASSET_ADMISSION_POLICY.resolve(
            payrollSettlementScopedEntry,
            book,
            payrollSettlementScopedEntry.entryKind().wireValue());
    if (fixedAssetResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(payrollSettlementScopedEntry),
          fixedAssetResolution.rejection());
    }
    BookkeepingEntry fixedAssetScopedEntry =
        java.util.Objects.requireNonNull(
            fixedAssetResolution.entry(), "accepted fixed-asset resolution entry");
    FinancingAdmissionPolicy.Resolution financingResolution =
        FINANCING_ADMISSION_POLICY.resolve(
            fixedAssetScopedEntry, book, fixedAssetScopedEntry.entryKind().wireValue());
    if (financingResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(fixedAssetScopedEntry),
          financingResolution.rejection());
    }
    BookkeepingEntry financingScopedEntry =
        java.util.Objects.requireNonNull(
            financingResolution.entry(), "accepted financing resolution entry");
    RealizedForeignExchangeAdmissionPolicy.Resolution realizedForeignExchangeResolution =
        REALIZED_FOREIGN_EXCHANGE_ADMISSION_POLICY.resolve(
            financingScopedEntry, book, financingScopedEntry.entryKind().wireValue());
    if (realizedForeignExchangeResolution.rejection().isPresent()) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(financingScopedEntry),
          realizedForeignExchangeResolution.rejection());
    }
    BookkeepingEntry realizedForeignExchangeScopedEntry =
        java.util.Objects.requireNonNull(
            realizedForeignExchangeResolution.entry(),
            "accepted realized-foreign-exchange resolution entry");
    try {
      return new ResolutionOutcome(
          INVENTORY_ADMISSION_POLICY.resolve(realizedForeignExchangeScopedEntry, book),
          Optional.empty());
    } catch (InventoryAdmissionPolicy.InventoryAdmissionFailure failure) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(realizedForeignExchangeScopedEntry),
          Optional.of(failure.rejection()));
    }
  }

  private static Optional<BookkeepingPostingRejection> reversalResolutionRejection(
      BookkeepingEntry entry, PostingValidationStore book) {
    return entry instanceof BookkeepingEntry.Reversal reversal
        ? ReversalResolutionSupport.rejectionFor(reversal, book)
        : Optional.empty();
  }

  private static BookkeepingEntry resolvedReversalEntry(
      BookkeepingEntry entry, PostingValidationStore book) {
    return entry instanceof BookkeepingEntry.Reversal reversal
        ? ReversalResolutionSupport.resolve(reversal, book)
        : entry;
  }

  public record ResolutionOutcome(
      InventoryPostingResolution resolution, Optional<BookkeepingPostingRejection> rejection) {
    public ResolutionOutcome {
      java.util.Objects.requireNonNull(resolution, "resolution");
      java.util.Objects.requireNonNull(rejection, "rejection");
    }

    /** Returns the resolved entry that downstream acceptance and commit paths should use. */
    public BookkeepingEntry entry() {
      return resolution.resolvedEntry();
    }
  }
}
