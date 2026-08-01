package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders compromise-review findings as declaration-centred text. */
final class CliAttestationReviewTextRenderer {
  private static final Comparator<AttestationCompromiseReview> REVIEW_DECLARATION_ORDER =
      Comparator.comparing(AttestationCompromiseReview::credentialKeyId)
          .thenComparing(AttestationCompromiseReview::firstAffectedOrder)
          .thenComparing(
              AttestationCompromiseReview::lastAffectedOrder,
              Comparator.nullsLast(Comparator.naturalOrder()));

  private CliAttestationReviewTextRenderer() {}

  static String renderFindings(List<AttestationReviewFinding> findings) {
    return renderDeclarations(findingsByDeclaration(findings));
  }

  /**
   * Renders wire findings with the same declaration grouping as their core counterparts.
   *
   * <p>Strict attestation verification reaches the generic rejection renderer with transport
   * payloads, while the read surface reaches it with domain findings. Keeping the grouping here
   * prevents the two text surfaces from drifting apart.
   */
  static String renderPayloadFindings(
      List<CliAttestationJsonModels.AttestationReviewFindingPayload> findings) {
    return renderDeclarations(payloadFindingsByDeclaration(findings));
  }

  private static String renderDeclarations(
      Map<AttestationCompromiseReview, List<BigInteger>> findingsByDeclaration) {
    return CliTextFormat.renderBulletedBlock(
        findingsByDeclaration.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(REVIEW_DECLARATION_ORDER))
            .map(entry -> renderDeclaration(entry.getKey(), entry.getValue()))
            .toList(),
        CliReportRenderSupport.TEXT_TABLE_WIDTH);
  }

  private static Map<AttestationCompromiseReview, List<BigInteger>> findingsByDeclaration(
      List<AttestationReviewFinding> findings) {
    return findings.stream()
        .collect(
            Collectors.groupingBy(
                AttestationReviewFinding::compromiseReview,
                Collectors.mapping(AttestationReviewFinding::operationOrder, Collectors.toList())));
  }

  private static Map<AttestationCompromiseReview, List<BigInteger>> payloadFindingsByDeclaration(
      List<CliAttestationJsonModels.AttestationReviewFindingPayload> findings) {
    return findings.stream()
        .collect(
            Collectors.groupingBy(
                CliAttestationReviewTextRenderer::payloadReview,
                Collectors.mapping(
                    finding -> new BigInteger(finding.operationOrder()), Collectors.toList())));
  }

  private static AttestationCompromiseReview payloadReview(
      CliAttestationJsonModels.AttestationReviewFindingPayload finding) {
    return new AttestationCompromiseReview(
        finding.credentialKeyId(),
        new BigInteger(finding.firstAffectedOrder()),
        finding.lastAffectedOrder() == null ? null : new BigInteger(finding.lastAffectedOrder()));
  }

  private static String renderDeclaration(
      AttestationCompromiseReview review, List<BigInteger> operationOrders) {
    return "Review declaration"
        + "\n  Credential key ID: "
        + review.credentialKeyId()
        + "\n  Review window: "
        + review.firstAffectedOrder()
        + " through "
        + (review.lastAffectedOrder() == null ? "head" : review.lastAffectedOrder())
        + "\n  Affected operation orders: "
        + renderOperationOrderRanges(operationOrders);
  }

  private static String renderOperationOrderRanges(List<BigInteger> operationOrders) {
    List<BigInteger> orderedOperationOrders = new ArrayList<>(operationOrders);
    orderedOperationOrders.sort(Comparator.naturalOrder());
    List<String> renderedRanges = new ArrayList<>();
    BigInteger rangeStart = orderedOperationOrders.getFirst();
    BigInteger rangeEnd = rangeStart;
    for (int index = 1; index < orderedOperationOrders.size(); index++) {
      BigInteger operationOrder = orderedOperationOrders.get(index);
      if (operationOrder.equals(rangeEnd.add(BigInteger.ONE))) {
        rangeEnd = operationOrder;
        continue;
      }
      renderedRanges.add(renderOperationOrderRange(rangeStart, rangeEnd));
      rangeStart = operationOrder;
      rangeEnd = operationOrder;
    }
    renderedRanges.add(renderOperationOrderRange(rangeStart, rangeEnd));
    return String.join(", ", renderedRanges);
  }

  private static String renderOperationOrderRange(BigInteger start, BigInteger end) {
    return start.equals(end) ? start.toString() : start + "-" + end;
  }
}
