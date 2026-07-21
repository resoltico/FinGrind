package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The complete semantic account state relevant to an account-registry operation. */
public record AttestationAccountSnapshot(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure,
    boolean active) {
  /** Requires all account-state components that carry domain meaning. */
  public AttestationAccountSnapshot {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(accountName, "accountName");
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
  }
}
