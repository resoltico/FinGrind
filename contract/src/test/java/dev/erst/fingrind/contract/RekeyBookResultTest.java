package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for {@link RekeyBookResult}. */
class RekeyBookResultTest extends ContractTestSupport {
  @Test
  void variants_validateNonNullState() {
    RekeyBookResult.Rekeyed rekeyed =
        new RekeyBookResult.Rekeyed(Path.of("book.sqlite"), attestationCommit());
    RekeyBookResult.Rejected rejected =
        new RekeyBookResult.Rejected(
            new BookMaintenanceRejection.SecretTargetOccupied(Path.of("book.new-key")));
    org.junit.jupiter.api.Assertions.assertEquals(
        Path.of("book.sqlite").toAbsolutePath().normalize(), rekeyed.bookFilePath());
    org.junit.jupiter.api.Assertions.assertEquals(
        new BookMaintenanceRejection.SecretTargetOccupied(Path.of("book.new-key")),
        rejected.rejection());
  }

  @Test
  void variants_rejectNullState() {
    assertThrows(
        NullPointerException.class,
        () -> new RekeyBookResult.Rekeyed(nullOf(), attestationCommit()));
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rejected(nullOf()));
  }
}
