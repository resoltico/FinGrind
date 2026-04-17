package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for {@link RekeyBookResult}. */
class RekeyBookResultTest {
  @Test
  void variants_validateNonNullState() {
    RekeyBookResult.Rekeyed rekeyed = new RekeyBookResult.Rekeyed(Path.of("book.sqlite"));
    RekeyBookResult.Rejected rejected =
        new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());

    org.junit.jupiter.api.Assertions.assertEquals(Path.of("book.sqlite"), rekeyed.bookFilePath());
    org.junit.jupiter.api.Assertions.assertEquals(
        new BookAdministrationRejection.BookNotInitialized(), rejected.rejection());
  }

  @Test
  void variants_rejectNullState() {
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rekeyed(null));
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rejected(null));
  }
}
