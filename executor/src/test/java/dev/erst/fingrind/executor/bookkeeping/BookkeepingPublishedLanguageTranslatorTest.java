package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the bookkeeping published-language translator. */
class BookkeepingPublishedLanguageTranslatorTest {
  @Test
  void bookkeepingPublishedLanguageTranslator_translatesBookOpeningOutcomes() {
    Instant initializedAt = Instant.parse("2026-05-05T09:15:30Z");
    BookkeepingAdministrationRejection rejection =
        new BookkeepingAdministrationRejection.BookAlreadyInitialized();

    OpenBookResult opened =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Opened(initializedAt));
    OpenBookResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(rejection));

    assertEquals(
        initializedAt, assertInstanceOf(OpenBookResult.Opened.class, opened).initializedAt());
    assertEquals(
        new BookAdministrationRejection.BookAlreadyInitialized(),
        assertInstanceOf(OpenBookResult.Rejected.class, rejected).rejection());
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesAccountDeclarationOutcomes() {
    RegisteredAccount account =
        new RegisteredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-05-05T09:15:30Z"));
    BookkeepingAdministrationRejection rejection =
        new BookkeepingAdministrationRejection.NormalBalanceConflict(
            account.accountCode(), NormalBalance.DEBIT, NormalBalance.CREDIT);

    DeclareAccountResult declared =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Declared(account));
    DeclareAccountResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new AccountDeclarationOutcome.Rejected(rejection));

    assertEquals(
        BookkeepingPublishedLanguageTranslator.toPublished(account),
        assertInstanceOf(DeclareAccountResult.Declared.class, declared).account());
    assertEquals(
        new BookAdministrationRejection.NormalBalanceConflict(
            account.accountCode(), NormalBalance.DEBIT, NormalBalance.CREDIT),
        assertInstanceOf(DeclareAccountResult.Rejected.class, rejected).rejection());
  }

  @Test
  void
      bookkeepingPublishedLanguageTranslator_translatesBookContainsSchemaAndGuardsEmptyViolations() {
    OpenBookResult notInitialized =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookNotInitialized()));
    OpenBookResult rejected =
        BookkeepingPublishedLanguageTranslator.toPublished(
            new BookOpeningOutcome.Rejected(
                new BookkeepingAdministrationRejection.BookContainsSchema()));

    assertEquals(
        new BookAdministrationRejection.BookNotInitialized(),
        assertInstanceOf(OpenBookResult.Rejected.class, notInitialized).rejection());
    assertEquals(
        new BookAdministrationRejection.BookContainsSchema(),
        assertInstanceOf(OpenBookResult.Rejected.class, rejected).rejection());
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookkeepingPostingRejection.AccountStateViolations(java.util.List.of()));
    InvocationTargetException invocationTargetException =
        assertThrows(
            InvocationTargetException.class,
            () ->
                BookkeepingPostingRejection.AccountStateViolations.class
                    .getDeclaredConstructor(java.util.List.class)
                    .newInstance((Object) null));
    assertInstanceOf(IllegalArgumentException.class, invocationTargetException.getCause());
  }

  @Test
  void bookkeepingPublishedLanguageTranslator_translatesDirectAndReversalLineage() {
    ReversalReference reversalReference =
        new ReversalReference(new dev.erst.fingrind.core.PostingId("posting-1"));
    ReversalReason reversalReason = new ReversalReason("duplicate entry");

    assertEquals(
        PostingLineage.direct(),
        BookkeepingPublishedLanguageTranslator.toPublished(PostingLineageModel.direct()));
    assertEquals(
        PostingLineageModel.direct(),
        BookkeepingPublishedLanguageTranslator.fromPublished(PostingLineage.direct()));
    assertEquals(
        PostingLineage.reversal(reversalReference, reversalReason),
        BookkeepingPublishedLanguageTranslator.toPublished(
            PostingLineageModel.reversal(reversalReference, reversalReason)));
    assertEquals(
        PostingLineageModel.reversal(reversalReference, reversalReason),
        BookkeepingPublishedLanguageTranslator.fromPublished(
            PostingLineage.reversal(reversalReference, reversalReason)));
  }
}
