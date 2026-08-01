package dev.erst.fingrind.cli.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import org.jspecify.annotations.Nullable;

/** Declare-account JSON payload emitted inside administration success envelopes. */
public record CliDeclareAccountPayload(
    Outcome outcome,
    CliBookQueryJsonModels.DeclaredAccountPayload account,
    @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable AttestationCommitPayload attestationCommit)
    implements CliSuccessPayload {
  public CliDeclareAccountPayload {
    java.util.Objects.requireNonNull(outcome, "outcome");
    java.util.Objects.requireNonNull(account, "account");
    if (outcome.appendsAttestation()) {
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
    } else if (attestationCommit != null) {
      throw new IllegalArgumentException(
          "An unchanged account mutation must not report a newly appended attestation operation.");
    }
  }

  /** Exact account-registry lifecycle outcome published by account mutation commands. */
  public enum Outcome implements dev.erst.fingrind.core.WireValue {
    DECLARED("declared", "Account Declared", true),
    REACTIVATED("reactivated", "Account Reactivated", true),
    RENAMED("renamed", "Account Renamed", true),
    AMENDED("amended", "Account Amended", true),
    RETIRED("retired", "Account Retired", true),
    UNCHANGED("unchanged", "Account Unchanged", false);

    private final String wireValue;
    private final String textTitle;
    private final boolean appendsAttestation;

    Outcome(String wireValue, String textTitle, boolean appendsAttestation) {
      this.wireValue = wireValue;
      this.textTitle = textTitle;
      this.appendsAttestation = appendsAttestation;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    public String textTitle() {
      return textTitle;
    }

    public boolean appendsAttestation() {
      return appendsAttestation;
    }
  }
}
