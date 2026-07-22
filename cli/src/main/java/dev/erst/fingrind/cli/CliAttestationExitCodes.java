package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;

/** Exit-code mapping for attestation outcome families. */
final class CliAttestationExitCodes {
  private CliAttestationExitCodes() {}

  static int exitCodeFor(ExportAttestationReceiptResult result) {
    return switch (result) {
      case ExportAttestationReceiptResult.Exported _ -> 0;
      case ExportAttestationReceiptResult.AuthorizationRejected _ -> 2;
    };
  }

  static int exitCodeFor(AttestationRegistryMutationResult result) {
    return switch (result) {
      case AttestationRegistryMutationResult.Mutated _ -> 0;
      case AttestationRegistryMutationResult.Rejected _ -> 2;
      case AttestationRegistryMutationResult.AuthorizationRejected _ -> 2;
    };
  }
}
