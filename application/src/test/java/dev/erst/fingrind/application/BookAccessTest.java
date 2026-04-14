package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for the protected-book access tuple. */
class BookAccessTest {
  @Test
  void constructor_retainsBookAndKeyPaths() {
    Path bookFilePath = Path.of("books", "acme.sqlite");
    Path bookKeyFilePath = Path.of("keys", "acme.book-key");

    BookAccess access = new BookAccess(bookFilePath, bookKeyFilePath);

    assertEquals(bookFilePath, access.bookFilePath());
    assertEquals(bookKeyFilePath, access.bookKeyFilePath());
  }
}
