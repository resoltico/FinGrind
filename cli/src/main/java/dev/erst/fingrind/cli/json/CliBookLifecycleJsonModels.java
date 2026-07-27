package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireSha256Hex;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.cli.json.CliBookInspectionJsonModels.BookIdentityPayload;
import org.jspecify.annotations.Nullable;

/** Book-opening, administration, and period-close JSON records emitted by the CLI. */
public interface CliBookLifecycleJsonModels {
  record OpenBookPayload(
      String bookFile,
      String initializedAt,
      BookIdentityPayload bookIdentity,
      String attestationBookId,
      AttestationCommitPayload attestationCommit,
      CliAttestationJsonModels.AttestationRegistryPayload attestationTrustRoot)
      implements CliSuccessPayload {
    public OpenBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      initializedAt = requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
      attestationBookId = requireText(attestationBookId, "attestationBookId");
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
      java.util.Objects.requireNonNull(attestationTrustRoot, "attestationTrustRoot");
    }
  }

  record GeneratedBookKeyFilePayload(String encoding, int entropyBits, String permissions)
      implements CliSuccessPayload {
    public GeneratedBookKeyFilePayload {
      encoding = requireText(encoding, "encoding");
      requirePositive(entropyBits, "entropyBits");
      permissions = requireText(permissions, "permissions");
    }
  }

  record AttestationKeyFilePayload(String credentialSpki, String keyId)
      implements CliSuccessPayload {
    public AttestationKeyFilePayload {
      credentialSpki = requireText(credentialSpki, "credentialSpki");
      keyId = requireSha256Hex(keyId, "keyId");
    }
  }

  record AttestationRegistryMutationPayload(
      String bookFile, String operationKind, AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public AttestationRegistryMutationPayload {
      bookFile = requireText(bookFile, "bookFile");
      operationKind = requireText(operationKind, "operationKind");
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  record SweptInterimResultPayload(
      int sweepOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String resultHoldingAccountCode,
      java.util.List<CliBookQueryJsonModels.BalanceBucketPayload> sweptTotals,
      String sweptAt,
      java.util.List<String> sweepPostingIds,
      AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public SweptInterimResultPayload {
      requirePositive(sweepOrder, "sweepOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      sweptTotals = CliJsonModelValidation.copyList(sweptTotals, "sweptTotals");
      sweptAt = requireText(sweptAt, "sweptAt");
      sweepPostingIds = CliJsonModelValidation.copyList(sweepPostingIds, "sweepPostingIds");
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  record ClosedFiscalYearPayload(
      int closeOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedAccumulatedAccountCode,
      String closedAt,
      boolean idempotentReplay,
      java.util.List<String> closePostingIds,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public ClosedFiscalYearPayload {
      requirePositive(closeOrder, "closeOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      capitalAccountCode = requireText(capitalAccountCode, "capitalAccountCode");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      retainedAccumulatedAccountCode =
          requireText(retainedAccumulatedAccountCode, "retainedAccumulatedAccountCode");
      closedAt = requireText(closedAt, "closedAt");
      closePostingIds = CliJsonModelValidation.copyList(closePostingIds, "closePostingIds");
      if (idempotentReplay && attestationCommit != null) {
        throw new IllegalArgumentException(
            "An idempotent fiscal year close replay must not report a newly appended attestation"
                + " operation.");
      }
      if (!idempotentReplay && attestationCommit == null) {
        throw new IllegalArgumentException(
            "A newly closed fiscal year must report its attestation operation.");
      }
    }
  }
}
