---
afad: "5.0.1"
version: "0.61.0"
domain: INDEX_ATTESTATION
updated: "2026-07-22"
route:
  keywords: [fingrind, protected-book, attestation, verification, receipt, manifest, operation-envelope, signing]
  questions: ["where are protected-book attestation symbols documented", "which document owns attestation verification failures", "where are attestation receipt and manifest result types routed"]
---

# Attestation API Index

**Purpose**: Route protected-book attestation, verification, artifact, and result symbols to their
canonical references without widening the general API index.

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AttestationAccountMutationIntent` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationAccountMutationIntent` |
| `AttestationAccountMutationProjection` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationAccountSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationArtifactSnapshotReader` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupAcknowledgement` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupAcknowledgementAdmission` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupArtifact` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupArtifactVerification` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationClosePostingSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationCredentialException` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationCredentialUseException` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationCredentialSource` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationDirectoryDurability` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `AttestationDirectoryDurability` |
| `AttestationEvidence` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationEvidence` |
| `AttestationEffectMutation` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationFounderInput` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationGenesis` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationGenesisFactory` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationInspectionService` | `DOC_02_VerifiableOperationAttestationVerification.md` | `Attestation Inspection And Verification Results` |
| `AttestationKeyFiles` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationKeyFiles` |
| `AttestationLifecycleMutationProjection` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationLifecycleState` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationMutationAuthorization` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationOperationAuthorizer` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationOperationKind` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationOperationPreimages` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationOperationRequest` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationOperationSigner` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationOperationSigner` |
| `AttestationPeriodCloseMutationProjection` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPlanOperationAuthorizer` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPostingEffectSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPostingEvidenceDocument` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPostingLine` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPostingMutationProjection` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPostingRequestSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationPublicCredential` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationPublicCredential` |
| `AttestationReceipt` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReceiptFinding` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReceiptRetention` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReceiptVerificationResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReviewResult` | `DOC_02_VerifiableOperationAttestationVerification.md` | `Attestation Inspection And Verification Results` |
| `AttestationSigningSession` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationSigningSessionFactory` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationSigningCredential` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationSigningCredential` |
| `AttestationTaxCodeSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationTaxRegistrationMutationProjection` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationTaxRegistrationSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationVerification` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerification` |
| `AttestationVerificationException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerificationException` |
| `AttestationVerificationFailure` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerificationFailure` |
| `AttestationVerifier` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerifier` |
| `AttestedProtectedBookLifecycleWorkflow` | `DOC_03_BookSessionsAndAdapters.md` | `Attested Protected-Book Maintenance` |
| `AttestedProtectedBookMaintenanceStore` | `DOC_03_BookSessionsAndAdapters.md` | `Attested Protected-Book Maintenance` |
| `BackupAcknowledgementConflictException` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `ExportAttestationReceiptResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `ProtocolOptions.Attestation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `AttestationStaleHeadException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationStaleHeadException` |
| `VerifyAttestationReceiptResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `VerifyBookAttestationResult` | `DOC_02_VerifiableOperationAttestationVerification.md` | `Attestation Inspection And Verification Results` |
