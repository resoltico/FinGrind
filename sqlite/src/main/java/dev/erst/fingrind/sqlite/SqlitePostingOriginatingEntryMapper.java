package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Rebuilds the published caller-authored entry from persisted posting-side SQLite facts. */
final class SqlitePostingOriginatingEntryMapper {
  private SqlitePostingOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase activeDatabase,
      SqliteNativeStatement postingRow,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingOriginKind postingOriginKind,
      @Nullable AppliedTax appliedTax,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    PostingOriginKind requiredPostingOriginKind =
        Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    if (requiredPostingOriginKind == PostingOriginKind.OPENING_POSITION) {
      return SqliteOpeningPositionOriginatingEntryMapper.originatingEntry(
          activeDatabase, postingRow, journalEntry);
    }
    BookkeepingEntry payrollEntry =
        SqliteLatvianPayrollOriginatingEntryMapper.originatingEntry(
            activeDatabase,
            new PostingId(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID)),
            requiredPostingOriginKind);
    if (payrollEntry != null) {
      return payrollEntry;
    }
    BookkeepingEntry accrualCutoffEntry =
        SqliteAccrualCutoffOriginatingEntryMapper.originatingEntry(
            activeDatabase,
            new PostingId(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID)),
            postingRow,
            journalEntry,
            requiredPostingOriginKind);
    if (accrualCutoffEntry != null) {
      return accrualCutoffEntry;
    }
    BookkeepingEntry fixedAssetEntry =
        SqliteFixedAssetOriginatingEntryMapper.originatingEntry(
            activeDatabase,
            new PostingId(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID)),
            postingRow,
            journalEntry,
            requiredPostingOriginKind);
    if (fixedAssetEntry != null) {
      return fixedAssetEntry;
    }
    BookkeepingEntry financingEntry =
        SqliteFinancingOriginatingEntryMapper.originatingEntry(
            activeDatabase,
            new PostingId(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID)),
            postingRow,
            journalEntry,
            requiredPostingOriginKind);
    if (financingEntry != null) {
      return financingEntry;
    }
    BookkeepingEntry realizedForeignExchangeEntry =
        SqliteRealizedForeignExchangeOriginatingEntryMapper.originatingEntry(
            activeDatabase,
            new PostingId(
                SqlitePostingMapper.requiredText(
                    postingRow, SqlitePostingColumnIndexes.COL_POSTING_ID)),
            postingRow,
            journalEntry,
            requiredPostingOriginKind,
            foreignExchangeDetails);
    if (realizedForeignExchangeEntry != null) {
      return realizedForeignExchangeEntry;
    }
    BookkeepingEntry salesEntry =
        SqliteSalesOriginatingEntryMapper.originatingEntry(
            requiredPostingOriginKind,
            postingRow,
            journalEntry,
            postingLineage,
            appliedTax,
            foreignExchangeDetails);
    if (salesEntry != null) {
      return salesEntry;
    }
    BookkeepingEntry inventoryEntry =
        SqliteInventoryOriginatingEntryMapper.originatingEntry(
            requiredPostingOriginKind,
            postingRow,
            journalEntry,
            postingLineage,
            appliedTax,
            foreignExchangeDetails);
    if (inventoryEntry != null) {
      return inventoryEntry;
    }
    return SqliteRoutineOriginatingEntryMapper.originatingEntry(
        requiredPostingOriginKind,
        postingRow,
        journalEntry,
        postingLineage,
        appliedTax,
        foreignExchangeDetails);
  }
}
