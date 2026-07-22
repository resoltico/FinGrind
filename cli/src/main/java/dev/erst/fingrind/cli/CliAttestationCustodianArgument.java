package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.core.attestation.AttestationCustodianNotSupportedException;
import java.util.ListIterator;

/** Resolves the exact explicit custody selection shared by attestation-key commands. */
final class CliAttestationCustodianArgument {
  private CliAttestationCustodianArgument() {}

  static AttestationCustodian require(ListIterator<String> argumentIterator) {
    String custodian =
        CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Attestation.CUSTODIAN);
    try {
      return AttestationCustodian.require(custodian);
    } catch (AttestationCustodianNotSupportedException exception) {
      throw CliArgumentValueParser.unsupportedAttestationCustodian(exception);
    }
  }
}
