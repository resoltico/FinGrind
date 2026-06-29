package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stable page of declared tax registrations. */
public record TaxRegistrationPage(
    BookIdentity bookIdentity,
    List<DeclaredTaxRegistration> registrations,
    int limit,
    Optional<TaxRegistrationPageCursor> nextCursor) {
  /** Validates one paginated tax-registration page. */
  public TaxRegistrationPage {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    registrations = ContractDescriptorValidation.copyList(registrations, "registrations");
    Objects.requireNonNull(nextCursor, "nextCursor");
    if (limit < 1) {
      throw new IllegalArgumentException("Tax registration page limit must be positive.");
    }
  }

  /** Returns whether another page exists after this one. */
  public boolean hasMore() {
    return nextCursor.isPresent();
  }
}
