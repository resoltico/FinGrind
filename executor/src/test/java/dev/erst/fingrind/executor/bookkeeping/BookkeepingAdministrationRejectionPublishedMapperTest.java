package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
            Optional.empty());
    AccountTaxonomy requestedTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.HEADER,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty());
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
