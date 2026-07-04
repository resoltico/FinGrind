package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct coverage for request-side tax admissibility rules before posting resolution. */
class TaxEntrySemanticsTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-01T00:00:00Z");
  private static final TaxRegistrationId REGISTRATION_ID = new TaxRegistrationId("vat-lv");

  @Test
  void validate_ignoresEntriesWithoutTaxSelectionAndUnsupportedKinds() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> saleViolations =
        validate(
            new ValidationStore(Optional.empty()),
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                null,
                null));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> contributionViolations =
        validate(
            new ValidationStore(Optional.of(registration())),
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "10000"),
                null));

    assertTrue(saleViolations.isEmpty());
    assertTrue(contributionViolations.isEmpty());
  }

  @Test
  void validate_reportsUnknownRegistrationAndUnknownCode() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> unknownRegistrationViolations =
        validate(
            new ValidationStore(Optional.empty()),
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                null));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> unknownCodeViolations =
        validate(
            new ValidationStore(Optional.of(registration())),
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("missing-code")),
                null));

    assertEquals("unknown-tax-registration", unknownRegistrationViolations.getFirst().code());
    assertEquals("tax.taxRegistrationId", unknownRegistrationViolations.getFirst().field());
    assertEquals("unknown-tax-code", unknownCodeViolations.getFirst().code());
    assertEquals("tax.taxCode", unknownCodeViolations.getFirst().field());
  }

  @Test
  void validate_reportsSaleAndExpenseApplicationKindMismatches() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> saleViolations =
        validate(
            new ValidationStore(Optional.of(registration())),
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-expense")),
                null));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> expenseViolations =
        validate(
            new ValidationStore(Optional.of(registration())),
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "12100"),
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                null));

    assertEquals("tax-application-kind-mismatch", saleViolations.getFirst().code());
    assertEquals("tax-application-kind-mismatch", expenseViolations.getFirst().code());
  }

  @Test
  void validate_acceptsOwnedSaleAndExpenseTaxSelections() {
    ValidationStore store = new ValidationStore(Optional.of(registration()));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> saleViolations =
        validate(
            store,
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                null));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> expenseViolations =
        validate(
            store,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "11200"),
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-nonrecoverable-expense")),
                null));

    assertTrue(saleViolations.isEmpty());
    assertTrue(expenseViolations.isEmpty());
  }

  @Test
  void validate_acceptsOwnedCreditSaleAndCreditExpenseTaxSelections() {
    ValidationStore store = new ValidationStore(Optional.of(registration()));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> creditSaleViolations =
        validate(
            store,
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                null,
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-standard-sale")),
                null));
    List<BookkeepingPostingRejection.EntrySemanticsViolation> creditExpenseViolations =
        validate(
            store,
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "11200"),
                new TaxSelection(REGISTRATION_ID, new TaxCode("vat-nonrecoverable-expense")),
                null));

    assertTrue(creditSaleViolations.isEmpty());
    assertTrue(creditExpenseViolations.isEmpty());
  }

  private static List<BookkeepingPostingRejection.EntrySemanticsViolation> validate(
      PostingValidationStore store, BookkeepingEntry entry) {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    TaxEntrySemantics.validate(
        violations, store, entry, "entryKind", entry.entryKind().wireValue());
    return List.copyOf(violations);
  }

  private static DeclaredTaxRegistration registration() {
    return new DeclaredTaxRegistration(
        REGISTRATION_ID,
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE),
            new TaxCodeDefinition(
                new TaxCode("vat-nonrecoverable-expense"),
                new TaxCodeName("VAT Nonrecoverable Expense"),
                new TaxRate(120_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE)),
        DECLARED_AT);
  }

  /** Validation-store double that exposes only the declared tax registration under test. */
  private static final class ValidationStore implements PostingValidationStore {
    private final Optional<DeclaredTaxRegistration> registration;

    private ValidationStore(Optional<DeclaredTaxRegistration> registration) {
      this.registration = registration;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return Map.of();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<DeclaredTaxRegistration> findTaxRegistration(
        TaxRegistrationId taxRegistrationId) {
      return registration.filter(value -> value.taxRegistrationId().equals(taxRegistrationId));
    }
  }
}
