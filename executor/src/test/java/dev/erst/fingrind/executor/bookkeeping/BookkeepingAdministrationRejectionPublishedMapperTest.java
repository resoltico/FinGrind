package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for bookkeeping-administration rejection publication. */
class BookkeepingAdministrationRejectionPublishedMapperTest {
  @Test
  void mapperProjectsAccountStructureConflictIntoPublicContract() {
    AccountTaxonomy existingTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    AccountTaxonomy requestedTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.HEADER,
            Optional.empty(),
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
  void accountRegistryMapperRejectsUnsupportedRejection() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountRegistryRejectionPublishedMapper.toPublished(
                    new BookkeepingAdministrationRejection.BookAlreadyInitialized()));

    assertEquals(
        "Expected an Account Registry rejection but received "
            + BookkeepingAdministrationRejection.BookAlreadyInitialized.class.getName()
            + ".",
        exception.getMessage());
  }

  @Test
  void accountRegistryMapperProjectsContraAccountRejectionsIntoThePublishedContract() {
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid(
            new AccountCode("4090"),
            new AccountCode("4000"),
            ContraAccountRelationshipViolation.TARGET_IS_CONTRA),
        AccountRegistryRejectionPublishedMapper.toPublished(
            new ContraAccountInvalid(
                new AccountCode("4090"),
                new AccountCode("4000"),
                ContraAccountRelationshipViolation.TARGET_IS_CONTRA)));
  }

  @Test
  void closeTargetMapperProjectsMissingCandidatesAndRejectsUnrelatedRejections() {
    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of(new AccountCode("3200"))),
        CloseTargetRejectionPublishedMapper.toPublished(
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200")))));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CloseTargetRejectionPublishedMapper.toPublished(
                    new BookkeepingAdministrationRejection.BookAlreadyInitialized()));

    assertEquals(
        "Expected a close-target rejection but received "
            + BookkeepingAdministrationRejection.BookAlreadyInitialized.class.getName()
            + ".",
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
        new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
            LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
                LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31"))));
    assertEquals(
        new BookAdministrationRejection.FiscalYearCloseFutureDate(LocalDate.parse("2027-01-01")),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2027-01-01"))));
    assertEquals(
        new FiscalYearCloseRequiresGeneratedPostings(),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new BookkeepingAdministrationRejection.FiscalYearCloseRequiresGeneratedPostings()));
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

  @Test
  void mapperProjectsAccountRegistryLifecycleRejectionsIntoPublicContract() {
    AccountCode accountCode = new AccountCode("1000");

    assertEquals(
        new dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection
            .AccountHasDependents(
            accountCode,
            List.of(
                AccountRegistryDependency.POSTINGS, AccountRegistryDependency.TAX_REGISTRATIONS)),
        BookkeepingAdministrationRejectionPublishedMapper.toPublished(
            new AccountRegistryLifecycleRejection.AccountHasDependents(
                accountCode,
                List.of(
                    AccountRegistryDependency.POSTINGS,
                    AccountRegistryDependency.TAX_REGISTRATIONS))));
  }
}
