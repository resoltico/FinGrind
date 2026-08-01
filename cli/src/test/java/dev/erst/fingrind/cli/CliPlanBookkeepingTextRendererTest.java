package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage for bookkeeping payload sections inside full plan text output. */
class CliPlanBookkeepingTextRendererTest extends CliFixtureSupport {
  private static final BigInteger ATTESTATION_ORDER = BigInteger.valueOf(123_456_789L);
  private static final String ATTESTATION_HEAD = "a".repeat(64);

  @Test
  void renderStepData_rendersCallerAuthoredEntryFactsWhenPresent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderStepData(
            new CliPlanStepDataJsonModels.PostingStepDataPayload(
                CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), salePostingFact())
                    .posting()));

    assertTrue(rendered.contains("Entry facts"));
    assertTrue(rendered.contains("Cash account"));
    assertTrue(rendered.contains("service-revenue"));
  }

  @Test
  void renderStepData_rendersCompleteAttestationIdentityWhenAvailable() {
    AttestationCommit attestationCommit =
        new AttestationCommit(ATTESTATION_ORDER, ATTESTATION_HEAD);

    String rendered =
        CliPlanBookkeepingTextRenderer.renderStepData(
            new CliPlanStepDataJsonModels.PostingStepDataPayload(
                CliBookPostingPayloadMapper.postingPayload(
                    salePostingFact(), null, attestationCommit)));

    assertTrue(rendered.contains(CliAttestationHeadPresentation.ORDER_LABEL));
    assertTrue(rendered.contains(ATTESTATION_ORDER.toString()));
    assertTrue(rendered.contains(CliAttestationHeadPresentation.HEAD_LABEL));
    assertTrue(rendered.contains(ATTESTATION_HEAD));
  }

  @Test
  void renderStepData_rendersExplicitUnavailableAttestationWhenAbsent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderStepData(
            new CliPlanStepDataJsonModels.PostingStepDataPayload(
                CliBookPayloadMapper.postingDetailsPayload(bookIdentity(), reversalPostingFact())
                    .posting()));

    assertFalse(rendered.contains("Entry facts"));
    assertTrue(rendered.contains("Reversal"));
    assertTrue(rendered.contains("No authenticated operation reference"));
    assertFalse(rendered.contains(CliAttestationHeadPresentation.ORDER_LABEL));
    assertFalse(rendered.contains(CliAttestationHeadPresentation.HEAD_LABEL));
  }

  @Test
  void renderStepData_rendersInlineAttestationOrderAndExplicitAbsence() {
    AttestationCommit attestationCommit =
        new AttestationCommit(ATTESTATION_ORDER, ATTESTATION_HEAD);
    CliPlanStepDataJsonModels.PostingPageStepDataPayload postingPage =
        new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
            2,
            50,
            null,
            false,
            List.of(
                CliBookPostingPayloadMapper.postingSummaryPayload(
                    salePostingFact(), null, attestationCommit),
                CliBookPostingPayloadMapper.postingSummaryPayload(reversalPostingFact())));

    String rendered = CliPlanBookkeepingTextRenderer.renderStepData(postingPage);

    assertTrue(rendered.contains(CliAttestationHeadPresentation.ORDER_LABEL));
    assertTrue(rendered.contains(ATTESTATION_ORDER.toString()));
    assertTrue(rendered.contains("(none)"));
    assertFalse(rendered.contains(CliAttestationHeadPresentation.HEAD_LABEL));
    assertFalse(rendered.contains(ATTESTATION_HEAD));
  }

  @Test
  void renderDeclaredAccount_rendersInventoryUnitOfMeasureWhenPresent() {
    String rendered =
        CliPlanBookkeepingTextRenderer.renderDeclaredAccount(
            "declared",
            new CliBookQueryJsonModels.DeclaredAccountPayload(
                "1400",
                "Inventory",
                "ASSET",
                "POSTABLE",
                null,
                null,
                "INVENTORY",
                "NON_CASH",
                null,
                new CliBookQueryJsonModels.UnitOfMeasurePayload("kg", 3),
                "DEBIT",
                true,
                "2026-04-23T10:15:30Z"));

    assertTrue(rendered.contains("Unit of measure"));
    assertTrue(rendered.contains("kg (scale 3)"));
  }
}
