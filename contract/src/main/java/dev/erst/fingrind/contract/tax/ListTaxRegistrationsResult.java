package dev.erst.fingrind.contract.tax;

import java.util.Objects;
import java.util.function.Function;

/** Closed result family for declared tax-registration listing. */
public sealed interface ListTaxRegistrationsResult
    permits ListTaxRegistrationsResult.Listed, ListTaxRegistrationsResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper);

  /** Success result carrying the current tax-registration registry snapshot. */
  record Listed(TaxRegistrationPage page) implements ListTaxRegistrationsResult {
    public Listed {
      Objects.requireNonNull(page, "page");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(listedMapper, "listedMapper").apply(this);
    }
  }

  /** Deterministic refusal for tax-registration listing. */
  record Rejected(TaxQueryRejection rejection) implements ListTaxRegistrationsResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T> T fold(Function<Listed, T> listedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
