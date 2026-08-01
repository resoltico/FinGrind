package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationHeadPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Owns canonical public projections of immutable attestation identities while preserving their
 * distinct commit, verified-head, and receipt-anchor meanings.
 */
final class CliAttestationHeadPresentation {
  static final String ORDER_LABEL = "Attestation order";
  static final String HEAD_LABEL = "Attestation head";
  static final String PREVIOUS_HEAD_LABEL = "Previous attestation head";

  private CliAttestationHeadPresentation() {}

  /** Projects the verified current chain head without representing it as a newly created commit. */
  static AttestationHeadPayload verifiedHeadPayload(
      BigInteger operationOrder, String operationHead) {
    return identityPayload(operationOrder, operationHead);
  }

  /** Projects the immutable operation identity to which a receipt is anchored. */
  static AttestationHeadPayload receiptAnchorPayload(
      BigInteger operationOrder, String operationHead) {
    return identityPayload(operationOrder, operationHead);
  }

  /** Appends the canonical identity rows for a newly committed operation. */
  static void appendCommitRows(List<List<String>> rows, AttestationCommit attestationCommit) {
    Objects.requireNonNull(attestationCommit, "attestationCommit");
    appendIdentityRows(
        rows, attestationCommit.operationOrder().toString(), attestationCommit.operationHeadHex());
  }

  /** Appends the verified chain head and its signed predecessor without conflating their roles. */
  static void appendVerifiedHeadRows(
      List<List<String>> rows,
      BigInteger operationOrder,
      String operationHead,
      String previousHead) {
    appendIdentityRows(rows, operationOrder.toString(), operationHead);
    rows.add(List.of(PREVIOUS_HEAD_LABEL, Objects.requireNonNull(previousHead, "previousHead")));
  }

  /** Appends the canonical identity rows for a receipt's historical anchor. */
  static void appendReceiptAnchorRows(
      List<List<String>> rows, BigInteger operationOrder, String operationHead) {
    appendIdentityRows(rows, operationOrder.toString(), operationHead);
  }

  /** Appends the canonical identity rows for a verified head without a predecessor projection. */
  static void appendVerifiedHeadRows(
      List<List<String>> rows, BigInteger operationOrder, String operationHead) {
    appendIdentityRows(rows, operationOrder.toString(), operationHead);
  }

  private static AttestationHeadPayload identityPayload(
      BigInteger operationOrder, String operationHead) {
    return new AttestationHeadPayload(
        Objects.requireNonNull(operationOrder, "operationOrder").toString(), operationHead);
  }

  static void appendIdentityRows(
      List<List<String>> rows, String operationOrder, String operationHead) {
    Objects.requireNonNull(rows, "rows");
    rows.add(List.of(ORDER_LABEL, Objects.requireNonNull(operationOrder, "operationOrder")));
    rows.add(List.of(HEAD_LABEL, Objects.requireNonNull(operationHead, "operationHead")));
  }
}
