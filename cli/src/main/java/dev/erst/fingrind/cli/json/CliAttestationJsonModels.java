package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Success payloads for non-mutating book-attestation verification and receipt operations. */
public interface CliAttestationJsonModels {
  record VerifyBookPayload(
      String bookId,
      String headOrder,
      String operationHead,
      boolean reviewRequired,
      List<AttestationReviewFindingPayload> reviewFindings)
      implements CliSuccessPayload {
    public VerifyBookPayload {
      bookId = requireText(bookId, "bookId");
      headOrder = requireText(headOrder, "headOrder");
      operationHead = requireText(operationHead, "operationHead");
      reviewFindings = copyList(reviewFindings, "reviewFindings");
      if (reviewRequired != !reviewFindings.isEmpty()) {
        throw new IllegalArgumentException("reviewRequired must exactly reflect reviewFindings.");
      }
    }
  }

  record AttestationReviewPayload(
      String bookId, String headOrder, List<AttestationReviewFindingPayload> findings)
      implements CliSuccessPayload {
    public AttestationReviewPayload {
      bookId = requireText(bookId, "bookId");
      headOrder = requireText(headOrder, "headOrder");
      findings = copyList(findings, "findings");
    }
  }

  record AttestationReviewFindingPayload(
      String credentialKeyId,
      String firstAffectedOrder,
      @Nullable String lastAffectedOrder,
      String operationOrder) {
    public AttestationReviewFindingPayload {
      credentialKeyId = requireText(credentialKeyId, "credentialKeyId");
      firstAffectedOrder = requireText(firstAffectedOrder, "firstAffectedOrder");
      lastAffectedOrder = requireOptionalText(lastAffectedOrder, "lastAffectedOrder");
      operationOrder = requireText(operationOrder, "operationOrder");
    }
  }

  record ExportReceiptPayload(
      String receiptFile,
      String bookId,
      String operationOrder,
      String operationHead,
      List<String> warnings)
      implements CliSuccessPayload {
    public ExportReceiptPayload {
      receiptFile = requireText(receiptFile, "receiptFile");
      bookId = requireText(bookId, "bookId");
      operationOrder = requireText(operationOrder, "operationOrder");
      operationHead = requireText(operationHead, "operationHead");
      warnings = copyList(warnings, "warnings");
    }
  }

  record VerifyReceiptPayload(String bookId, String operationOrder, List<String> findings)
      implements CliSuccessPayload {
    public VerifyReceiptPayload {
      bookId = requireText(bookId, "bookId");
      operationOrder = requireText(operationOrder, "operationOrder");
      findings = copyList(findings, "findings");
    }
  }
}
