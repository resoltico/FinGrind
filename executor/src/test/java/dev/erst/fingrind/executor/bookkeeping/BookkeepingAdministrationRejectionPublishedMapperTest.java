package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for bookkeeping-administration rejection publication. */
class BookkeepingAdministrationRejectionPublishedMapperTest {
  private static final MethodHandle TO_PUBLISHED_ACCOUNT_STRUCTURE_REJECTION =
      publishedAccountStructureRejectionHandle();

  @Test
  void mapperProjectsAccountStructureConflictIntoPublicContract() {
    AccountTaxonomy existingTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    AccountTaxonomy requestedTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.HEADER,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    BookAdministrationRejection.AccountTaxonomyConflict published =
        assertInstanceOf(
            BookAdministrationRejection.AccountTaxonomyConflict.class,
            BookkeepingAdministrationRejectionPublishedMapper.toPublished(
                new BookkeepingAdministrationRejection.AccountTaxonomyConflict(
                    new AccountCode("1000"), existingTaxonomy, requestedTaxonomy)));

    assertEquals(new AccountCode("1000"), published.accountCode());
    assertEquals(existingTaxonomy, published.existingAccountTaxonomy());
    assertEquals(requestedTaxonomy, published.requestedAccountTaxonomy());
  }

  @Test
  void helperRejectsUnsupportedNonAccountStructureRejection() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokeAccountStructureMapper(
                    new BookkeepingAdministrationRejection.BookAlreadyInitialized()));

    assertEquals(
        "Unsupported administration rejection for account-structure mapping: "
            + BookkeepingAdministrationRejection.BookAlreadyInitialized.class.getName(),
        exception.getMessage());
  }

  @Test
  void mapperProjectsCloseWindowAndAmbiguousTargetRejectionsIntoPublicContract() {
    assertEquals(
        new BookAdministrationRejection.FiscalYearCloseMustStartAt(LocalDate.parse("2026-01-01")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))));
    assertEquals(
        new BookAdministrationRejection.FiscalYearCloseMustEndAt(LocalDate.parse("2026-12-31")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))));
    assertEquals(
        new BookAdministrationRejection.FiscalYearCloseFutureDate(LocalDate.parse("2027-01-01")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))));
    assertEquals(
        new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            FiscalYearStart.parse("01-01")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                LocalDate.parse("2026-12-15"),
                LocalDate.parse("2027-01-15"),
                FiscalYearStart.parse("01-01"))));
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
            FinancialPositionLineClassification.RESULT_HOLDING,
            List.of(new AccountCode("3200"), new AccountCode("3210"))),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"), new AccountCode("3210")))));
  }

  private static MethodHandle publishedAccountStructureRejectionHandle() {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              BookkeepingAdministrationRejectionPublishedMapper.class, MethodHandles.lookup());
      return lookup.findStatic(
          BookkeepingAdministrationRejectionPublishedMapper.class,
          "toPublishedAccountStructureRejection",
          MethodType.methodType(
              BookAdministrationRejection.class, BookkeepingAdministrationRejection.class));
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static BookAdministrationRejection invokeAccountStructureMapper(
      BookkeepingAdministrationRejection rejection) {
    try {
      return (BookAdministrationRejection)
          TO_PUBLISHED_ACCOUNT_STRUCTURE_REJECTION.invoke(rejection);
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new AssertionError(throwable);
    }
  }
}
