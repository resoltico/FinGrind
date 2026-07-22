---
afad: "5.0.1"
version: "0.61.0"
domain: INDEX_ATTESTATION
updated: "2026-07-22"
route:
  keywords: [fingrind, protected-book, attestation, verification, receipt, manifest, operation-envelope, signing, credential lifecycle, policy lifecycle]
  questions: ["where are protected-book attestation symbols documented", "which document owns attestation credential lifecycle", "which document owns attestation verification failures", "where are attestation receipt and manifest result types routed"]
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
| `AttestationAdmissionRejectedException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationAdmissionRejectedException` |
| `AttestationArtifactSnapshotReader` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationAuthorizationException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationAuthorizationException` And `AttestationAuthorizationFailure` |
| `AttestationAuthorizationFailure` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationAuthorizationException` And `AttestationAuthorizationFailure` |
| `AttestationBookInspection` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationCommit` | `DOC_02_PostingAndLedgerPlans.md` | `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult` |
| `AttestationAuthorizationLimits` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationAuthorizationLimits` |
| `AttestationBackupAcknowledgement` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupAcknowledgementAdmission` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupArtifact` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationBackupArtifactVerification` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `AttestationCapability` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind` |
| `AttestationClosePostingSnapshot` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationCompromiseReview` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerifier` |
| `AttestationCredentialException` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationCredentialPurpose` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind` |
| `AttestationCredentialUseException` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationCredentialSource` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Signing Sessions And Authorization` |
| `AttestationCustodian` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationCustodian` And `AttestationCustodianNotSupportedException` |
| `AttestationCustodianNotSupportedException` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationCustodian` And `AttestationCustodianNotSupportedException` |
| `AttestationDirectoryDurability` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `AttestationDirectoryDurability` |
| `AttestationEvidence` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationEvidence` |
| `AttestationEffectMutation` | `DOC_02_VerifiableOperationAttestation.md` | `Attestation Mutation Projections` |
| `AttestationFounderInput` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationGenesis` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationGenesisFactory` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationGenesis` |
| `AttestationGrantState` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind` |
| `AttestationInspectionService` | `DOC_02_VerifiableOperationAttestationVerification.md` | `Attestation Inspection And Verification Results` |
| `AttestationKeyFiles` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationKeyFiles` |
| `AttestationKeyFileMetadata` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationKeyFileMetadata` |
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
| `AttestationReceiptArtifactException` | `DOC_02_VerifiableOperationAttestationVerification.md` | Verification, Compromise Review, And Failure Taxonomy |
| `AttestationReceiptFinding` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReceiptRetention` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationReceiptVerificationResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `AttestationRegistryMutation` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind` |
| `AttestationRegistryInspection` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.CapabilityPolicy` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.Credential` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.PrincipalCapability` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryInspection.SystemWorkflowPolicy` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationBookInspection` And `AttestationRegistryInspection` |
| `AttestationRegistryMutationResult` | `DOC_02_BookMaintenanceContracts.md` | `AttestationRegistryMutationResult` |
| `AttestationReviewFinding` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationVerifier` |
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
| `ProtectedBookRegistryMutationOutcome` | `DOC_03_BookSessionsAndAdapters.md` | `Attested Protected-Book Maintenance` |
| `BackupAcknowledgementConflictException` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Backup Artifact Types` |
| `ExportAttestationReceiptResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `ProtocolOptions.Attestation` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolOptions` |
| `ProtocolAttestationRegistryRequestFields` | `DOC_02_ProtocolAndDiscovery.md` | `ProtocolAttestationRegistryRequestFields` |
| `AttestationStaleHeadException` | `DOC_02_VerifiableOperationAttestationVerification.md` | `AttestationStaleHeadException` |
| `AttestationSystemWorkflowKind` | `DOC_02_VerifiableOperationAttestation.md` | `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind` |
| `VerifyAttestationReceiptResult` | `DOC_02_VerifiableOperationAttestationArtifacts.md` | `Receipt Result Types` |
| `VerifyBookAttestationResult` | `DOC_02_VerifiableOperationAttestationVerification.md` | `Attestation Inspection And Verification Results` |
