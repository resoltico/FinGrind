package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shared provenance and evidence fixtures for posting-route reachability contracts. */
public final class PostingRouteReachabilityProvenanceFixtures {
  private PostingRouteReachabilityProvenanceFixtures() {}

  /** Builds the operator provenance used by shared reachability scenarios. */
  public static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new CommandId(
            UUID.nameUUIDFromBytes(
                    ("fingrind-test-commandid:" + token).getBytes(StandardCharsets.UTF_8))
                .toString()),
        new IdempotencyKey("idem-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("corr-" + token)));
  }

  /** Builds the evidence bundle that matches the command category for a scenario. */
  public static dev.erst.fingrind.core.AccountingEvidence generatedEvidence(
      String token, String sourceDocumentType) {
    return new dev.erst.fingrind.core.AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-" + token),
                new SourceDocumentType(sourceDocumentType),
                PostingRouteReachabilityTestSupport.EFFECTIVE_DATE)),
        List.of());
  }

  /** Builds committed provenance for fixtures that need a persisted posting. */
  public static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        requestProvenance(token),
        PostingRouteReachabilityTestSupport.FIXED_CLOCK.instant(),
        SourceChannel.CLI);
  }
}
