package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.account;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.decode;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.richAccount;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.tags;
import static dev.erst.fingrind.core.attestation.AttestationMutationProjectionFixtures.taxRegistration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies account and tax-registration mutation projections owned by their direct snapshots. */
class AttestationAccountAndTaxMutationProjectionTest {
  @Test
  void taxRegistrationProjection_commitsTheFullCatalogAndValidatesEffectIdentity() {
    AttestationTaxRegistrationSnapshot requested = taxRegistration("registration-1", "LV-123");
    AttestationTaxRegistrationSnapshot persisted = taxRegistration("registration-1", nullOf());

    AttestationOperationPreimages created =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration", requested, persisted, AttestationEffectMutation.CREATE);
    AttestationOperationPreimages amended =
        AttestationTaxRegistrationMutationProjection.project(
            "declare-tax-registration", requested, requested, AttestationEffectMutation.AMEND);

    assertEquals(List.of(0x0100, 0x0113, 0x0114, 0x0114), tags(decode(created.request())));
    assertEquals(List.of(0x0013, 0x0014, 0x0014), tags(decode(created.effect())));
    assertNotEquals(
        java.util.Arrays.toString(created.effect()), java.util.Arrays.toString(amended.effect()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationTaxRegistrationMutationProjection.project(
                "declare-tax-registration",
                requested,
                persisted,
                AttestationEffectMutation.RETIRE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationTaxRegistrationMutationProjection.project(
                "declare-tax-registration",
                requested,
                taxRegistration("registration-2", "LV-123"),
                AttestationEffectMutation.CREATE));
    assertThrows(IllegalArgumentException.class, () -> taxRegistration("registration-1", " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationTaxCodeSnapshot("VAT", "Value added tax", -1, "EXCLUSIVE", "SALE"));
  }

  @Test
  void accountProjection_commitsRichTaxonomyAndEnforcesIntent() {
    AttestationAccountSnapshot rich = richAccount(true);
    AttestationAccountSnapshot persisted = richAccount(false);

    AttestationOperationPreimages projected =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            rich,
            persisted,
            AttestationEffectMutation.REACTIVATE);

    assertEquals(
        List.of(0x0100, 0x0110, 0x0111, 0x0111, 0x0111, 0x0112, 0x0112),
        tags(decode(projected.request())));
    assertEquals(
        List.of(0x0010, 0x0011, 0x0011, 0x0011, 0x0012, 0x0012), tags(decode(projected.effect())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationProjection.project(
                AttestationAccountMutationIntent.AMENDMENT,
                "amend-account",
                rich,
                persisted,
                AttestationEffectMutation.CREATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationProjection.project(
                AttestationAccountMutationIntent.RETIREMENT,
                "retire-account",
                rich,
                account("1001"),
                AttestationEffectMutation.RETIRE));
  }
}
