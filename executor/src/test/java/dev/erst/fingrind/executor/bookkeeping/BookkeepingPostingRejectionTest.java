package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PostingKind;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Constructor guards for local bookkeeping posting rejections. */
class BookkeepingPostingRejectionTest {
  @Test
  void openingBalanceWindowClosed_requiresBothBlockingFacts() throws Exception {
    InvocationTargetException nullPostingKind =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningBalanceWindowClosed.class
                    .getDeclaredConstructor(PostingKind.class, LocalDate.class)
                    .newInstance(null, LocalDate.parse("2026-04-07")));
    InvocationTargetException nullEffectiveDate =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.OpeningBalanceWindowClosed.class
                    .getDeclaredConstructor(PostingKind.class, LocalDate.class)
                    .newInstance(PostingKind.STANDARD, null));

    assertEquals(
        "firstBlockingPostingKind",
        assertInstanceOf(NullPointerException.class, nullPostingKind.getCause()).getMessage());
    assertEquals(
        "firstBlockingEffectiveDate",
        assertInstanceOf(NullPointerException.class, nullEffectiveDate.getCause()).getMessage());
  }
}
