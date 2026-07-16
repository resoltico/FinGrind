package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared published surface for caller-authored bookkeeping entry variants. */
@FunctionalInterface
public interface BookkeepingEntrySurface {
  /** Returns the stable caller-authored entry kind. */
  default BookkeepingEntryKind entryKind() {
    return BookkeepingEntrySurfaceSupport.entryKind((BookkeepingEntry) this);
  }

  /** Returns the effective date carried by this caller-authored entry. */
  LocalDate effectiveDate();

  /**
   * Returns the caller-derivable journal skeleton implied by this entry variant.
   *
   * <p>Costed sales, tax-bearing entries, payroll settlements, and reversals require executor-owned
   * resolution facts before the completed journal can be derived.
   */
  default JournalEntry journalEntry() {
    return BookkeepingEntrySurfaceSupport.journalEntry((BookkeepingEntry) this);
  }

  /** Returns the canonical durable posting kind implied by this entry variant. */
  default PostingKind postingKind() {
    return BookkeepingEntrySurfaceSupport.postingKind((BookkeepingEntry) this);
  }

  /** Returns the durable posting-origin vocabulary implied by this entry variant. */
  default PostingOriginKind postingOriginKind() {
    return BookkeepingEntrySurfaceSupport.postingOriginKind((BookkeepingEntry) this);
  }

  /** Returns the durable posting lineage implied by this entry variant. */
  default PostingLineage postingLineage() {
    return BookkeepingEntrySurfaceSupport.postingLineage((BookkeepingEntry) this);
  }

  /** Returns the caller-authored journal lines carried or implied by this entry variant. */
  default List<JournalLine> lines() {
    return journalEntry().lines();
  }

  /** Returns the optional owned foreign-exchange facts retained for this entry. */
  default @Nullable ForeignExchangeDetails foreignExchangeDetails() {
    return null;
  }
}
