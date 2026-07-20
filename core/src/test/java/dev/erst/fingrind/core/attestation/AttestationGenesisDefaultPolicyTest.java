package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.withField;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Proves the public founder-count boundary and exact initial policy table. */
class AttestationGenesisDefaultPolicyTest {
  private static final AttestationHash ZERO_HEAD =
      AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]);

  @Test
  void acceptsEveryAllowedFounderCountWithTheExactPublishedGenesisDefaults() {
    for (int founderCount = 1; founderCount <= 5; founderCount++) {
      TestCredential[] founders =
          java.util.stream.IntStream.range(0, founderCount)
              .mapToObj(ignored -> credential())
              .toArray(TestCredential[]::new);
      AttestationPreimage request =
          withPublishedGenesisQuorums(genesisRequestPreimage(founders), founderCount);
      AttestationPreimage effect =
          withPublishedGenesisQuorums(genesisEffectPreimage(founders), founderCount);
      AttestationGenesisAuthorizationContext context =
          AttestationGenesisAuthorizationContext.verify(
              genesisPayload(BigInteger.ZERO, ZERO_HEAD, request, effect), request, effect);

      assertDoesNotThrow(
          () ->
              AttestationAuthorization.requireGenesis(
                  context, signedGenesisEnvelope(context, founders)));

      AttestationRegistry registry =
          AttestationRegistry.genesis(
              java.util.Arrays.stream(founders)
                  .map(AttestationGenesisTestSupport::founder)
                  .toList());
      for (AttestationCapability capability : AttestationCapability.values()) {
        assertEquals(
            publishedGenesisQuorum(capability, founderCount),
            registry.quorumAt(capability, BigInteger.ZERO));
      }
    }
  }

  private static AttestationPreimage withPublishedGenesisQuorums(
      AttestationPreimage preimage, int founderCount) {
    return AttestationPreimage.of(
        preimage.records().stream()
            .map(record -> withPublishedGenesisQuorum(record, founderCount))
            .toList());
  }

  private static AttestationPreimage.Fact withPublishedGenesisQuorum(
      AttestationPreimage.Fact record, int founderCount) {
    int capabilityField;
    int quorumField;
    switch (record.recordTypeTag()) {
      case 0x0005 -> {
        capabilityField = 1;
        quorumField = 2;
      }
      case 0x0103 -> {
        capabilityField = 0;
        quorumField = 1;
      }
      default -> {
        return record;
      }
    }
    AttestationCapability capability =
        capability(
            AttestationPreimageValueReader.token(
                record, capabilityField, AttestationAuthorizationFailure.GENESIS_INVALID));
    return withField(
        record,
        quorumField,
        AttestationField.present(
            AttestationNumericFieldValue.unsigned16(
                publishedGenesisQuorum(capability, founderCount))));
  }

  private static AttestationCapability capability(String token) {
    for (AttestationCapability capability : AttestationCapability.values()) {
      if (capability.token().equals(token)) {
        return capability;
      }
    }
    throw new IllegalArgumentException("Unknown attestation capability token.");
  }

  private static int publishedGenesisQuorum(AttestationCapability capability, int founderCount) {
    return switch (capability) {
      case POST, APPROVE, CLOSE_PERIOD, BACKUP, ANCHOR -> 1;
      case RESTORE, REKEY, ENROLL_KEY, REVOKE_KEY, ALTER_POLICY -> founderCount == 1 ? 1 : 2;
    };
  }
}
