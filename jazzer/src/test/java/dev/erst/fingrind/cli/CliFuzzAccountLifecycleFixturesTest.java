package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookFixtureMutations;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CliFuzzAccountLifecycleFixturesTest {
  @Test
  void attestation_credential_workspace_failure_is_reported() {
    assertThrows(
        IllegalStateException.class,
        () ->
            CliFuzzWorkflowFixtures.createAttestationCredential(
                () -> {
                  throw new IOException("simulated credential workspace failure");
                }));
  }

  @Test
  void lifecycle_helpers_manage_books_accounts_and_fail_fast_on_drift() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      var command =
          CliFuzzFixtures.readPostEntryCommand(
              CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));

      CliFuzzWorkflowFixtures.openBook(administrationService);
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzWorkflowFixtures.openBook(administrationService));

      List<DeclaredAccount> declaredAccounts =
          CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);
      assertEquals(2, declaredAccounts.size());
      assertEquals(
          CliFuzzSyntheticAccountFixtures.firstAccountCode(command),
          declaredAccounts.getFirst().accountCode());
      assertTrue(CliFuzzAccountFixtures.listAccounts(bookSession).containsAll(declaredAccounts));

      DeclaredAccount firstAccount = declaredAccounts.getFirst();
      InMemoryBookFixtureMutations.deactivateAccount(bookSession, firstAccount.accountCode());
      DeclaredAccount restoredAccount =
          CliFuzzAccountFixtures.reactivateAccount(administrationService, firstAccount);
      assertTrue(restoredAccount.active());
      assertEquals(firstAccount.declaredAt(), restoredAccount.declaredAt());
    }
  }

  @Test
  void lifecycle_helpers_reject_uninitialized_service_states() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      var command =
          CliFuzzFixtures.readPostEntryCommand(
              CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));
      DeclaredAccount account =
          CliFuzzFixtureStoreSupport.declaredAccount(
              new AccountCode("1000"), AccountType.ASSET, false);

      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command));
      assertThrows(
          IllegalStateException.class, () -> CliFuzzAccountFixtures.listAccounts(bookSession));
      assertThrows(
          IllegalStateException.class,
          () -> CliFuzzAccountFixtures.reactivateAccount(administrationService, account));
    }
  }

  @Test
  void lifecycle_helpers_reject_drifted_open_book_and_reactivate_account_shapes() {
    DeclaredAccount account =
        CliFuzzFixtureStoreSupport.declaredAccount(
            new AccountCode("1000"), AccountType.ASSET, false);

    BookAdministrationService driftedOpenBookService =
        new BookAdministrationService(
            () -> new BookLifecycleInspection.Missing(7),
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {
              @Override
              public BookOpeningOutcome openAttestedBook(
                  Instant initializedAt,
                  BookIdentity bookIdentity,
                  List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts,
                  dev.erst.fingrind.core.attestation.AttestationEvidence genesisEvidence) {
                return new BookOpeningOutcome.Opened(initializedAt.plusSeconds(1), bookIdentity);
              }
            },
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());
    BookAdministrationService inactiveReactivationService =
        new BookAdministrationService(
            () ->
                new BookLifecycleInspection.Initialized(
                    dev.erst.fingrind.contract.runtime.BookFormatContract.APPLICATION_ID,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    CliFuzzFixtures.fixedClock().instant(),
                    CliFuzzWorkflowFixtures.bookIdentity()),
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountDeclaration declaration,
                  Instant declaredAt,
                  dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                      attestationAuthorizer) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        declaration.accountCode(),
                        declaration.accountName(),
                        declaration.accountType(),
                        declaration.accountTaxonomy(),
                        false,
                        declaredAt));
              }
            },
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());
    BookAdministrationService changedDeclaredAtService =
        new BookAdministrationService(
            () ->
                new BookLifecycleInspection.Initialized(
                    dev.erst.fingrind.contract.runtime.BookFormatContract.APPLICATION_ID,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    CliFuzzFixtures.fixedClock().instant(),
                    CliFuzzWorkflowFixtures.bookIdentity()),
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountDeclaration declaration,
                  Instant declaredAt,
                  dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                      attestationAuthorizer) {
                return new AccountDeclarationOutcome.Declared(
                    new RegisteredAccount(
                        declaration.accountCode(),
                        declaration.accountName(),
                        declaration.accountType(),
                        declaration.accountTaxonomy(),
                        true,
                        declaredAt.plusSeconds(1)));
              }
            },
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());

    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzWorkflowFixtures.openBook(driftedOpenBookService));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzAccountFixtures.reactivateAccount(inactiveReactivationService, account));
    assertThrows(
        IllegalStateException.class,
        () -> CliFuzzAccountFixtures.reactivateAccount(changedDeclaredAtService, account));
  }

  @Test
  void declare_posting_accounts_accepts_renamed_and_unchanged_success_shapes() {
    AtomicInteger declareCalls = new AtomicInteger();
    BookAdministrationService administrationService =
        new BookAdministrationService(
            () ->
                new BookLifecycleInspection.Initialized(
                    dev.erst.fingrind.contract.runtime.BookFormatContract.APPLICATION_ID,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    dev.erst.fingrind.contract.runtime.BookFormatContract.FORMAT_VERSION,
                    CliFuzzFixtures.fixedClock().instant(),
                    CliFuzzWorkflowFixtures.bookIdentity()),
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {
              @Override
              public AccountDeclarationOutcome declareAccount(
                  AccountDeclaration declaration,
                  Instant declaredAt,
                  dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                      attestationAuthorizer) {
                RegisteredAccount account =
                    new RegisteredAccount(
                        declaration.accountCode(),
                        declaration.accountName(),
                        declaration.accountType(),
                        declaration.accountTaxonomy(),
                        true,
                        declaredAt);
                return switch (declareCalls.getAndIncrement()) {
                  case 0 -> new AccountDeclarationOutcome.Renamed(account);
                  case 1 -> new AccountDeclarationOutcome.Unchanged(account);
                  default -> throw new AssertionError("Unexpected extra account declaration call.");
                };
              }
            },
            new CliFuzzFixtureStoreSupport.AbstractBookAdministrationStoreStub() {},
            CliFuzzFixtures.fixedClock());
    var command =
        CliFuzzFixtures.readPostEntryCommand(
            CliFuzzFixtureCommandSupport.basicValidRequest().getBytes(UTF_8));

    List<DeclaredAccount> declaredAccounts =
        CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);

    assertEquals(2, declaredAccounts.size());
    assertEquals(2, declareCalls.get());
    assertTrue(declaredAccounts.stream().allMatch(DeclaredAccount::active));
  }

  @Test
  void list_accounts_follows_pagination_until_cursor_is_exhausted() {
    DeclaredAccount firstAccount =
        CliFuzzFixtureStoreSupport.declaredAccount(
            new AccountCode("1000"), AccountType.ASSET, true);
    DeclaredAccount secondAccount =
        CliFuzzFixtureStoreSupport.declaredAccount(
            new AccountCode("2000"), AccountType.REVENUE, true);
    AccountRegistryCursor nextCursor = new AccountRegistryCursor(firstAccount.accountCode());
    AtomicInteger pageCalls = new AtomicInteger();
    List<Optional<AccountRegistryCursor>> cursors = new ArrayList<>();
    var pagedStore =
        new CliFuzzFixtureStoreSupport.AbstractBookkeepingReadStoreStub() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return new BookLifecycleInspection.Initialized(
                7, 1, 1, Instant.EPOCH, CliFuzzWorkflowFixtures.bookIdentity());
          }

          @Override
          public AccountRegistryPage listAccounts(
              dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
            cursors.add(query.cursor());
            if (pageCalls.getAndIncrement() == 0) {
              return new AccountRegistryPage(
                  List.of(CliFuzzFixtureStoreSupport.toRegisteredAccount(firstAccount)),
                  query.limit(),
                  Optional.of(nextCursor));
            }
            return new AccountRegistryPage(
                List.of(CliFuzzFixtureStoreSupport.toRegisteredAccount(secondAccount)),
                query.limit(),
                Optional.empty());
          }
        };

    assertEquals(
        List.of(firstAccount, secondAccount), CliFuzzAccountFixtures.listAccounts(pagedStore));
    assertEquals(List.of(Optional.empty(), Optional.of(nextCursor)), cursors);
  }
}
