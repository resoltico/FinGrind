package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.application.BookAdministrationRejection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for {@link RekeyBookResult}. */
class RekeyBookResultTest {
  @Test
  void records_preserveConstructorState() {
    RekeyBookResult.Rekeyed rekeyed = new RekeyBookResult.Rekeyed(Path.of("book.sqlite"));
    RekeyBookResult.Rejected rejected =
        new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());

    assertEquals(Path.of("book.sqlite"), rekeyed.bookFilePath());
    assertEquals(new BookAdministrationRejection.BookNotInitialized(), rejected.rejection());
  }

  @Test
  void records_rejectNullValues() {
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rekeyed(null));
    assertThrows(NullPointerException.class, () -> new RekeyBookResult.Rejected(null));
  }
}
