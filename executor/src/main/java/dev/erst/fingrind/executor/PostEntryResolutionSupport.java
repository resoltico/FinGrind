package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.InventoryAdmissionPolicy;
import dev.erst.fingrind.executor.bookkeeping.InventoryPostingResolution;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.util.List;
import java.util.Optional;

/** Post-tax entry resolution owner for typed-entry and reversal request expansion. */
public final class PostEntryResolutionSupport {
  private static final InventoryAdmissionPolicy INVENTORY_ADMISSION_POLICY =
      new InventoryAdmissionPolicy();

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
    try {
      return new ResolutionOutcome(
          INVENTORY_ADMISSION_POLICY.resolve(inventoryScopedEntry, book), Optional.empty());
    } catch (InventoryAdmissionPolicy.InventoryAdmissionFailure failure) {
      return new ResolutionOutcome(
          InventoryPostingResolution.withoutInventory(inventoryScopedEntry),
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
