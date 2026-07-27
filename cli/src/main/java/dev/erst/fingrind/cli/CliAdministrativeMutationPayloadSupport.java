package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;

/** Shared administrative mutation payload and artifact mapping helpers. */
final class CliAdministrativeMutationPayloadSupport {
  private CliAdministrativeMutationPayloadSupport() {}

  static CliDeclareAccountPayload declareAccountPayload(
      CliDeclareAccountPayload.Outcome outcome,
      dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return new CliDeclareAccountPayload(
        outcome,
        CliBookQueryPayloadMapper.accountPayload(account),
        CliAttestationCommitPresentation.payload(attestationCommit));
  }
}
