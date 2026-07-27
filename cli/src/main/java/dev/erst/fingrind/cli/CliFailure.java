package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Structured CLI failure payload used for deterministic error envelopes. */
record CliFailure(
    String code,
    String message,
    @Nullable String hint,
    @Nullable String argument,
    CliErrorJsonModels.@Nullable ErrorDetails details,
    @Nullable Path path,
    List<Path> relatedPaths,
    @Nullable Path retainedStage) {
  CliFailure(String code, String message, @Nullable String hint, @Nullable String argument) {
    this(code, message, hint, argument, null, null, List.of(), null);
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      CliErrorJsonModels.@Nullable ErrorDetails details) {
    this(code, message, hint, argument, details, null, List.of(), null);
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable Path path) {
    this(code, message, hint, argument, null, path, List.of(), null);
  }

  CliFailure(
      String code,
      String message,
      @Nullable String hint,
      @Nullable String argument,
      Path path,
      List<Path> relatedPaths) {
    this(code, message, hint, argument, null, path, relatedPaths, null);
  }

  CliFailure {
    code = requireText(code, "code");
    ContractResponseCatalog.failureCategoryFor(code);
    message = requireText(message, "message");
    hint = requireOptionalText(hint);
    argument = requireOptionalText(argument);
    details = requireSupportedDetails(details);
    relatedPaths = List.copyOf(Objects.requireNonNull(relatedPaths, "relatedPaths"));
    if (path == null && !relatedPaths.isEmpty()) {
      throw new IllegalArgumentException("relatedPaths require one primary path.");
    }
  }

  static CliFailure fromContractFailure(ContractFailure failure) {
    CliErrorJsonModels.@Nullable ErrorDetails details = detailsFor(failure.details());
    var paths = failure.paths();
    if (paths != null) {
      return new CliFailure(
          failure.code(),
          failure.message(),
          failure.hint(),
          failure.argument(),
          details,
          paths.path(),
          paths.relatedPaths(),
          failure.retainedStage() == null ? null : failure.retainedStage().retainedStagePath());
    }
    return new CliFailure(
        failure.code(),
        failure.message(),
        failure.hint(),
        failure.argument(),
        details,
        null,
        List.of(),
        failure.retainedStage() == null ? null : failure.retainedStage().retainedStagePath());
  }

  private static CliErrorJsonModels.@Nullable ErrorDetails detailsFor(
      @Nullable ContractFailureDetails contractDetails) {
    if (contractDetails == null) {
      return null;
    }
    return switch (contractDetails) {
      case ContractFailureDetails.ArtifactPublicationOutcomeUncertain outcomeUncertain ->
          new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
              CliPublicPaths.absoluteValue(outcomeUncertain.candidateArtifactPath()),
              outcomeUncertain.retainedStage() == null
                  ? null
                  : CliPublicPaths.absoluteValue(
                      outcomeUncertain.retainedStage().retainedStagePath()));
      case ContractFailureDetails.ArtifactPublicationDurabilityUncertain publicationUncertain -> {
        var publication = publicationUncertain.publication();
        yield new CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails(
            new CliMaintenanceErrorJsonModels.PublishedArtifact(
                CliPublicPaths.absoluteValue(publication.publishedArtifactPath()),
                CliPublicPaths.absoluteValue(publication.retention().retainedStagePath())));
      }
      case ContractFailureDetails.ProtectedBookPairPublicationUncertain uncertainty -> {
        ContractFailureDetails.PairPublication pair = uncertainty.pairPublication();
        yield new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
            uncertainty.operation().wireName(),
            new CliMaintenanceErrorJsonModels.PairPublication(
                new CliMaintenanceErrorJsonModels.PairPublicationMember(
                    CliPublicPaths.absoluteValue(pair.bookTarget().path()),
                    CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.from(
                        pair.bookTarget().state())),
                new CliMaintenanceErrorJsonModels.PairPublicationMember(
                    CliPublicPaths.absoluteValue(pair.generatedSecretTarget().path()),
                    CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.from(
                        pair.generatedSecretTarget().state())),
                pair.recoveryRecordState() == null
                    ? null
                    : CliMaintenanceErrorJsonModels.PairPublicationRecoveryRecordStatePayload.from(
                        pair.recoveryRecordState()),
                pairPublicationRetention(pair.pairPublicationRetention())));
      }
      case ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked blocked -> {
        ContractFailureDetails.PairPublication pair = blocked.pairPublication();
        yield new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationEvidenceBlockedDetails(
            new CliMaintenanceErrorJsonModels.PairPublication(
                new CliMaintenanceErrorJsonModels.PairPublicationMember(
                    CliPublicPaths.absoluteValue(pair.bookTarget().path()),
                    CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.from(
                        pair.bookTarget().state())),
                new CliMaintenanceErrorJsonModels.PairPublicationMember(
                    CliPublicPaths.absoluteValue(pair.generatedSecretTarget().path()),
                    CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.from(
                        pair.generatedSecretTarget().state())),
                null,
                pairPublicationRetention(pair.pairPublicationRetention())));
      }
      case ContractFailureDetails.UnsupportedBookFormatVersion formatVersion ->
          new CliErrorJsonModels.UnsupportedBookFormatVersionDetails(
              formatVersion.detectedBookFormatVersion(),
              formatVersion.supportedBookFormatVersion());
      case OpenBookFailureDetails.OpenBookPreparationArtifactsRetained retained ->
          new CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails(
              retained.retainedArtifacts().stream()
                  .map(
                      artifact ->
                          new CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact(
                              artifact.role().wireRole(),
                              CliPublicPaths.absoluteValue(artifact.path()),
                              artifact.retainedStage() == null
                                  ? null
                                  : CliPublicPaths.absoluteValue(
                                      artifact.retainedStage().retainedStagePath())))
                  .toList());
      case OpenBookFailureDetails.OpenBookCompletionUncertain completion ->
          new CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails(
              CliPublicPaths.absoluteValue(completion.bookFilePath()),
              completion.initializedAt().toString(),
              CliBookInspectionPayloadMapper.bookIdentityPayload(completion.bookIdentity()),
              completion.reportedAttestationTrustRoot().bookId().toString(),
              CliAttestationCommitPresentation.requiredPayload(
                  completion.reportedAttestationCommit()),
              new CliOpenBookErrorJsonModels.ReportedAttestationTrustRoot(
                  completion.reportedAttestationTrustRoot().bookId().toString(),
                  CliAttestationCommitPresentation.requiredPayload(
                      completion.reportedAttestationCommit()),
                  CliAttestationPayloadMapper.registryPayload(
                      completion.reportedAttestationTrustRoot())),
              completion.retainedFounderKeyArtifacts().stream()
                  .map(
                      publication ->
                          CliEnvelopeMapper.successArtifact(
                              ProtocolArtifactOutput.attestationKeyFileFormat(), publication))
                  .toList(),
              completion.retainedBookArtifacts().stream()
                  .map(
                      artifact ->
                          new CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact(
                              artifact.role().wireRole(),
                              CliPublicPaths.absoluteValue(artifact.path()),
                              artifact.retainedStage() == null
                                  ? null
                                  : CliPublicPaths.absoluteValue(
                                      artifact.retainedStage().retainedStagePath())))
                  .toList());
    };
  }

  private static CliMaintenanceErrorJsonModels.@Nullable PairPublicationRetention
      pairPublicationRetention(
          dev.erst.fingrind.contract.bookkeeping.@Nullable ProtectedBookPairPublicationRetention
              retention) {
    if (retention == null) {
      return null;
    }
    return new CliMaintenanceErrorJsonModels.PairPublicationRetention(
        publishedArtifact(retention.bookPublication()),
        publishedArtifact(retention.generatedSecretPublication()));
  }

  private static CliMaintenanceErrorJsonModels.PublishedArtifact publishedArtifact(
      dev.erst.fingrind.core.ArtifactPublicationResult publication) {
    dev.erst.fingrind.core.ArtifactPublicationResult checkedPublication =
        Objects.requireNonNull(publication, "publication");
    return new CliMaintenanceErrorJsonModels.PublishedArtifact(
        CliPublicPaths.absoluteValue(checkedPublication.publishedArtifactPath()),
        CliPublicPaths.absoluteValue(checkedPublication.retention().retainedStagePath()));
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }

  private static @Nullable String requireOptionalText(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized;
  }

  private static CliErrorJsonModels.@Nullable ErrorDetails requireSupportedDetails(
      CliErrorJsonModels.@Nullable ErrorDetails value) {
    if (value == null) {
      return null;
    }
    return value;
  }
}
