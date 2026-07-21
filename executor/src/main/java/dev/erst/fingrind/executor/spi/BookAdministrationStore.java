package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import java.time.Instant;
import java.util.List;

/** Writes administrative book and account-registry mutations. */
public interface BookAdministrationStore {
  /** Explicitly initializes one new book if the selected path is currently empty. */
  BookOpeningOutcome openBook(
      Instant initializedAt, BookIdentity bookIdentity, List<AccountDeclaration> seededAccounts);

  /** Explicitly initializes one new book with its self-authorizing attestation genesis. */
  BookOpeningOutcome openAttestedBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<AccountDeclaration> seededAccounts,
      AttestationEvidence genesisEvidence);

  /** Declares or reactivates one account in the selected book. */
  AccountDeclarationOutcome declareAccount(AccountDeclaration declaration, Instant declaredAt);

  /** Replaces one account definition when the Account Registry admits the lifecycle change. */
  AccountAmendmentOutcome amendAccount(AccountDeclaration amendment, Instant amendedAt);

  /** Retires one account when the Account Registry admits the lifecycle change. */
  AccountRetirementOutcome retireAccount(
      dev.erst.fingrind.core.AccountCode accountCode, Instant retiredAt);
}
