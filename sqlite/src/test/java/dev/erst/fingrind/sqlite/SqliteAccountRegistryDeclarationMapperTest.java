package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationDecision;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Verifies durable and attestation fact mapping for account-registry declarations. */
class SqliteAccountRegistryDeclarationMapperTest {
  @Test
  void declaredAccount_returnsDurableSnapshotsAndRejectsRejectedOutcomes() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        account,
        SqliteAccountRegistryDeclarationMapper.declaredAccount(
            new AccountDeclarationDecision.Declared(account)));
    assertEquals(
        account,
        SqliteAccountRegistryDeclarationMapper.declaredAccount(
            new AccountDeclarationDecision.Reactivated(account)));
    assertEquals(
        account,
        SqliteAccountRegistryDeclarationMapper.declaredAccount(
            new AccountDeclarationDecision.Renamed(account)));
    assertEquals(
        account,
        SqliteAccountRegistryDeclarationMapper.declaredAccount(
            new AccountDeclarationDecision.Unchanged(account)));

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteAccountRegistryDeclarationMapper.declaredAccount(
                    new AccountDeclarationDecision.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized())));
    assertTrue(
        Objects.requireNonNullElse(rejected.getMessage(), "")
            .contains("Rejected account declarations do not carry a durable account snapshot"));
  }

  @Test
  void accountAuditEvent_mapsDurableOutcomesAndRejectsNonAuditedOutcomes() {
    Instant recordedAt = Instant.parse("2026-04-08T11:15:30Z");
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        BookAuditEvent.accountDeclared(recordedAt, account.accountCode()),
        SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
            recordedAt, new AccountDeclarationDecision.Declared(account)));
    assertEquals(
        BookAuditEvent.accountReactivated(recordedAt, account.accountCode()),
        SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
            recordedAt, new AccountDeclarationDecision.Reactivated(account)));
    assertEquals(
        BookAuditEvent.accountRenamed(recordedAt, account.accountCode()),
        SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
            recordedAt, new AccountDeclarationDecision.Renamed(account)));

    IllegalArgumentException unchanged =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
                    recordedAt, new AccountDeclarationDecision.Unchanged(account)));
    assertEquals("Unchanged account declarations do not append audit.", unchanged.getMessage());

    IllegalArgumentException rejected =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteAccountRegistryDeclarationMapper.accountAuditEvent(
                    recordedAt,
                    new AccountDeclarationDecision.Rejected(
                        new BookkeepingAdministrationRejection.BookNotInitialized())));
    assertTrue(
        Objects.requireNonNullElse(rejected.getMessage(), "")
            .contains("Rejected account declarations do not append audit"));
  }

  @Test
  void withAttestationAppend_projectsOnlyDurableAccountDeclarationMutations() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("2100"),
            new AccountName("Trade creditors"),
            AccountType.LIABILITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    AttestationAppendOutcome.Appended attestationAppend = attestationAppend();

    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
            account, attestationAppend),
        SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
            new AccountDeclarationDecision.Declared(account), attestationAppend));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Reactivated(
            account, attestationAppend),
        SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
            new AccountDeclarationDecision.Reactivated(account), attestationAppend));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Renamed(
            account, attestationAppend),
        SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
            new AccountDeclarationDecision.Renamed(account), attestationAppend));
    assertEquals(
        "An unchanged account declaration must not receive an attestation append outcome.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
                        new AccountDeclarationDecision.Unchanged(account), attestationAppend))
            .getMessage());
    assertEquals(
        "A rejected account declaration must not receive an attestation append outcome.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    SqliteAccountRegistryDeclarationMapper.withAttestationAppend(
                        new AccountDeclarationDecision.Rejected(
                            new BookkeepingAdministrationRejection.BookNotInitialized()),
                        attestationAppend))
            .getMessage());
  }

  @Test
  void declarationMutation_mapsPersistedAccountChangesAndRejectsNonMutatingOutcomes() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("3000"),
            new AccountName("Retained earnings"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    assertEquals(
        AttestationEffectMutation.CREATE,
        SqliteAccountRegistryDeclarationMapper.declarationMutation(
            new AccountDeclarationDecision.Declared(account)));
    assertEquals(
        AttestationEffectMutation.REACTIVATE,
        SqliteAccountRegistryDeclarationMapper.declarationMutation(
            new AccountDeclarationDecision.Reactivated(account)));
    assertEquals(
        AttestationEffectMutation.AMEND,
        SqliteAccountRegistryDeclarationMapper.declarationMutation(
            new AccountDeclarationDecision.Renamed(account)));

    assertEquals(
        "Unchanged account declarations do not append attestation.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    SqliteAccountRegistryDeclarationMapper.declarationMutation(
                        new AccountDeclarationDecision.Unchanged(account)))
            .getMessage());
    assertTrue(
        Objects.requireNonNullElse(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                            SqliteAccountRegistryDeclarationMapper.declarationMutation(
                                new AccountDeclarationDecision.Rejected(
                                    new BookkeepingAdministrationRejection.BookNotInitialized())))
                    .getMessage(),
                "")
            .contains("Rejected account declarations do not append attestation"));
  }

  @Test
  void planOutcome_preservesEveryAccountDeclarationDecisionWithoutInventingAnAppend() {
    RegisteredAccount account =
        SqlitePostingFactFixtureSupport.registeredAccount(
            new AccountCode("3100"),
            new AccountName("Plan retained earnings"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    BookkeepingAdministrationRejection rejection =
        new BookkeepingAdministrationRejection.BookNotInitialized();

    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Declared(account),
        SqliteAccountRegistryDeclarationMapper.planOutcome(
            new AccountDeclarationDecision.Declared(account)));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Reactivated(
            account),
        SqliteAccountRegistryDeclarationMapper.planOutcome(
            new AccountDeclarationDecision.Reactivated(account)));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Renamed(account),
        SqliteAccountRegistryDeclarationMapper.planOutcome(
            new AccountDeclarationDecision.Renamed(account)));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Unchanged(account),
        SqliteAccountRegistryDeclarationMapper.planOutcome(
            new AccountDeclarationDecision.Unchanged(account)));
    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome.Rejected(
            rejection),
        SqliteAccountRegistryDeclarationMapper.planOutcome(
            new AccountDeclarationDecision.Rejected(rejection)));
  }

  private static AttestationAppendOutcome.Appended attestationAppend() {
    return new AttestationAppendOutcome.Appended(
        dev.erst.fingrind.testsupport.AttestationVerificationTestFixtures.verifiedAppend());
  }
}
