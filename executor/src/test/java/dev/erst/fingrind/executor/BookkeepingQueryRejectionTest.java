package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import org.junit.jupiter.api.Test;

/** Direct coverage for the local bookkeeping query rejection family. */
class BookkeepingQueryRejectionTest {
  @Test
  void wireCode_projectsEveryQueryRejectionVariant() {
    assertEquals(
        "query-book-not-initialized",
        BookkeepingQueryRejection.wireCode(new BookkeepingQueryRejection.BookNotInitialized()));
    assertEquals(
        "unknown-account",
        BookkeepingQueryRejection.wireCode(
            new BookkeepingQueryRejection.UnknownAccount(new AccountCode("1000"))));
    assertEquals(
        "posting-not-found",
        BookkeepingQueryRejection.wireCode(
            new BookkeepingQueryRejection.PostingNotFound(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals("query-book-not-initialized", BookkeepingQueryRejection.bookNotInitializedCode());
  }

  @Test
  void queryRejections_rejectNullInputs() {
    assertEquals(
        "rejection",
        assertThrows(NullPointerException.class, () -> BookkeepingQueryRejection.wireCode(nullOf()))
            .getMessage());
    assertEquals(
        "accountCode",
        assertThrows(
                NullPointerException.class,
                () -> new BookkeepingQueryRejection.UnknownAccount(nullOf()))
            .getMessage());
    assertEquals(
        "postingId",
        assertThrows(
                NullPointerException.class,
                () -> new BookkeepingQueryRejection.PostingNotFound(nullOf()))
            .getMessage());
  }
}
