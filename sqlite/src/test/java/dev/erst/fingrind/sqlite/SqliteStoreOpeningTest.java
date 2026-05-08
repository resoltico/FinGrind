package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the priming-handoff ownership boundary around store opening. */
class SqliteStoreOpeningTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void ownershipTransfer_reportsCleanupFailureWhenStoreCloseThrows() throws Exception {
    List<String> cleanupReports = new ArrayList<>();
    try (SqliteBookPassphrase bookPassphrase =
        SqliteBookPassphrase.fromCharacters(
            "ownership transfer cleanup", TEST_BOOK_KEY.toCharArray())) {
      ContractDecision<SqlitePostingFactStore> decision =
          SqliteStoreOpening.openResolved(
              tempDirectory.resolve("ownership-transfer-close.sqlite"),
              bookPassphrase,
              SqliteStoreAccessMode.READ_WRITE_CREATE,
              ThrowingOwnershipTransferStore::new,
              (action, exception) -> cleanupReports.add(action + "|" + exception.getMessage()));
      assertTrue(decision instanceof ContractDecision.Rejected<SqlitePostingFactStore>);
    }
    assertEquals(
        List.of(
            "closing one SQLite session during priming handoff|Simulated ownership-transfer close failure."),
        cleanupReports);
  }

  /** Test-only opening seam that rejects priming and then fails on ownership-transfer cleanup. */
  private static final class ThrowingOwnershipTransferStore extends SqlitePostingFactStore {
    ThrowingOwnershipTransferStore(
        Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteStoreAccessMode accessMode) {
      super(bookPath, bookPassphrase, accessMode);
    }

    @Override
    ContractDecision<SqlitePostingFactStore> prime() {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
              "Simulated priming rejection.", null, null));
    }

    @Override
    public void close() {
      throw new IllegalStateException("Simulated ownership-transfer close failure.");
    }
  }
}
