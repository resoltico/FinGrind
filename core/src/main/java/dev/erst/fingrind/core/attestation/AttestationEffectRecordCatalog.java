package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationRecordSchema.record;

import java.util.List;
import java.util.stream.Stream;

/** Catalog-owned effect record layouts and canonical per-type sort keys. */
final class AttestationEffectRecordCatalog {
  private AttestationEffectRecordCatalog() {}

  static List<AttestationRecordSchema> schemas() {
    return Stream.of(identityAndAccountSchemas(), postingAndInventorySchemas(), lifecycleSchemas())
        .flatMap(List::stream)
        .toList();
  }

  private static List<AttestationRecordSchema> identityAndAccountSchemas() {
    return List.of(
        record(
            0x0001,
            "book.identity",
            "mutation:u8!, bookId:uuid!, entityName:text!, kernelProfile:token!, accountingBasis:token!, frameworkPosition:token!, entityForm:token!, template:token!, costingDoctrine:token?, functionalCurrency:currency!, fiscalYearStartMonth:u8!, fiscalYearStartDay:u8!, bookStartDate:date!",
            1),
        record(
            0x0002,
            "principal.key-binding",
            "mutation:u8!, principalId:uuid!, keyId:hash!, bindingAction:token!, spki:spki!, credentialPurpose:token!, predecessorKeyId:hash?",
            1,
            2),
        record(
            0x0003,
            "principal.capability-grant",
            "mutation:u8!, principalId:uuid!, capability:token!, grantState:token!",
            1,
            2),
        record(0x0005, "policy.capability-rule", "mutation:u8!, capability:token!, quorum:u16!", 1),
        record(
            0x0006,
            "backup.acknowledgement",
            "mutation:u8!, backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash!",
            1),
        record(0x0007, "book.key-epoch", "mutation:u8!, keyEpoch:u64!, rekeyedAt:instant!", 1),
        record(
            0x0008,
            "system.workflow-policy",
            "mutation:u8!, workflowId:uuid!, workflowKind:token!, resultHoldingAccountCode:text!, capitalAccountCode:text?, retainedResultAccountCode:text?, active:bool!",
            1),
        record(
            0x0009,
            "credential.retirement",
            "mutation:u8!, keyId:hash!, principalId:uuid!, retirementState:token!, reason:text?",
            1),
        record(
            0x0010,
            "account.state",
            "mutation:u8!, accountCode:text!, accountName:text!, accountType:token!, nodeKind:token!, parentAccountCode:text?, unitOfMeasure:text?, active:bool!",
            1),
        record(
            0x0011,
            "account.classification",
            "mutation:u8!, accountCode:text!, classificationFamily:token!, classification:token!",
            1,
            2),
        record(
            0x0012,
            "account.relationship",
            "mutation:u8!, accountCode:text!, relationshipKind:token!, targetAccountCode:text?",
            1,
            2),
        record(
            0x0013,
            "tax.registration",
            "mutation:u8!, registrationId:text!, registrationName:text!, jurisdiction:text!, registrationCode:text?, payableAccountCode:text!, receivableAccountCode:text!, obligationFrequency:token!, dueDaysAfterPeriodEnd:u16!, active:bool!",
            1),
        record(
            0x0014,
            "tax.registration-code",
            "mutation:u8!, registrationId:text!, taxCode:text!, taxCodeName:text!, rate:scaled!, inclusionMode:token!, applicationKind:token!",
            1,
            2));
  }

  private static List<AttestationRecordSchema> postingAndInventorySchemas() {
    return List.of(
        record(
            0x0020,
            "posting.fact",
            "mutation:u8!, postingId:uuid!, operationStepOrder:u32!, operationKind:token!, postingKind:token!, originKind:token!, effectiveDate:date!, recordedAt:instant!, priorPostingId:uuid?, commandId:uuid!, idempotencyKey:text?, causationId:text?, sourceChannel:token!",
            1),
        record(
            0x0021,
            "posting.source-document",
            "mutation:u8!, postingId:uuid!, sourceDocumentId:text!, sourceDocumentType:text!, documentDate:date!",
            1,
            2),
        record(
            0x0022,
            "posting.approval",
            "mutation:u8!, postingId:uuid!, approvalId:text!, approverReference:text!, approverType:token!, decision:token!, approvedAt:instant!",
            1,
            2),
        record(
            0x0023,
            "posting.applied-tax",
            "mutation:u8!, postingId:uuid!, registrationId:text!, taxCode:text!, taxableAmount:money!, taxAmount:money!, taxDirection:token!",
            1,
            2,
            3),
        record(
            0x0024,
            "posting.foreign-exchange",
            "mutation:u8!, postingId:uuid!, foreignCurrency:currency!, foreignAmount:money!, functionalAmount:money!, exchangeRate:scaled!",
            1),
        record(
            0x0025,
            "journal.line",
            "mutation:u8!, postingId:uuid!, lineOrder:u32!, accountCode:text!, side:token!, amount:money!, quantity:scaled?",
            1,
            2),
        record(
            0x0030,
            "inventory.movement",
            "mutation:u8!, movementOrder:u64!, postingId:uuid!, accountCode:text!, movementKind:token!, quantity:scaled!, unitCost:money!, inventoryCost:money!",
            1),
        record(
            0x0031,
            "inventory.on-hand",
            "mutation:u8!, accountCode:text!, quantity:scaled!, costPool:money!, valuationThrough:date!",
            1),
        record(
            0x0040,
            hyphenated("interim", "result", "sweep"),
            "mutation:u8!, sweepOrder:u64!, effectiveFrom:date!, effectiveTo:date!, resultHoldingAccountCode:text!",
            1),
        record(
            0x0041,
            hyphenated("interim", "result", "sweep", "total"),
            "mutation:u8!, sweepOrder:u64!, currency:currency!, total:money!",
            1,
            2),
        record(
            0x0042,
            hyphenated("interim", "result", "sweep", "posting"),
            "mutation:u8!, sweepOrder:u64!, postingId:uuid!",
            1,
            2),
        record(
            0x0043,
            hyphenated("fiscal", "year", "close"),
            "mutation:u8!, closeOrder:u64!, effectiveFrom:date!, effectiveTo:date!, capitalAccountCode:text!, resultHoldingAccountCode:text!, retainedResultAccountCode:text!",
            1),
        record(
            0x0044,
            hyphenated("fiscal", "year", "close", "posting"),
            "mutation:u8!, closeOrder:u64!, postingId:uuid!",
            1,
            2));
  }

  private static List<AttestationRecordSchema> lifecycleSchemas() {
    return List.of(
        record(
            0x0050,
            "accrual-cutoff",
            "mutation:u8!, cutoffId:uuid!, cutoffKind:token!, originPostingId:uuid!, balanceAccountCode:text!, recognitionAccountCode:text!, recognitionStart:date?, recognitionEnd:date?, deferredOrAccruedAmount:money!",
            1),
        record(
            0x0051,
            "accrual-cutoff-application",
            "mutation:u8!, cutoffId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, recognizedAmount:money!, reversalPostingId:uuid?",
            1,
            2),
        record(
            0x0060,
            "fixed-asset",
            "mutation:u8!, assetId:uuid!, originPostingId:uuid!, fixedAssetAccountCode:text!, accumulatedDepreciationAccountCode:text!, depreciationExpenseAccountCode:text!, gainOnDisposalAccountCode:text!, lossOnDisposalAccountCode:text!, assetClass:token!, capitalizationAmount:money!, serviceDate:date!, usefulLifeMonths:u32!",
            1),
        record(
            0x0061,
            "fixed-asset-application",
            "mutation:u8!, assetId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, amount:money!, period:date!",
            1,
            2),
        record(
            0x0062,
            "fixed-asset-reversal",
            "mutation:u8!, reversalPostingId:uuid!, assetId:uuid!, reversedApplicationOrOriginId:uuid!",
            1),
        record(
            0x0070,
            "financing-arrangement",
            "mutation:u8!, arrangementId:uuid!, originPostingId:uuid!, principalAccountCode:text!, interestPayableAccountCode:text!, principal:money!, commencementDate:date!",
            1),
        record(
            0x0071,
            "financing-application",
            "mutation:u8!, arrangementId:uuid!, applicationOrder:u64!, postingId:uuid!, principalAmount:money!, interestAmount:money!, effectiveDate:date!",
            1,
            2),
        record(
            0x0072,
            "financing-reversal",
            "mutation:u8!, reversalPostingId:uuid!, arrangementId:uuid!, reversedApplicationOrOriginId:uuid!",
            1),
        record(
            0x0080,
            "foreign-currency-obligation",
            "mutation:u8!, obligationId:uuid!, originPostingId:uuid!, receivableAccountCode:text!, revenueAccountCode:text!, foreignExchangeGainAccountCode:text!, foreignExchangeLossAccountCode:text!, currency:currency!, foreignAmount:money!, functionalAmount:money!",
            1),
        record(
            0x0081,
            "foreign-currency-settlement",
            "mutation:u8!, obligationId:uuid!, settlementId:uuid!, postingId:uuid!, settlementAmount:money!, realizedGainLoss:money!",
            1,
            2),
        record(
            0x0082,
            "foreign-currency-reversal",
            "mutation:u8!, reversalPostingId:uuid!, obligationOrSettlementId:uuid!",
            1),
        record(
            0x0090,
            "latvian-payroll-run",
            "mutation:u8!, payrollRunId:uuid!, employeeReference:text!, payrollMonth:text!, withholdingProfile:token!, wageExpenseAccountCode:text!, employerSocialContributionExpenseAccountCode:text!, netWagesPayableAccountCode:text!, employeeSocialContributionPayableAccountCode:text!, employerSocialContributionPayableAccountCode:text!, personalIncomeTaxPayableAccountCode:text!, grossAmount:money!, netAmount:money!, taxContributionAmount:money!",
            1),
        record(
            0x0091,
            "latvian-payroll-run-reversal",
            "mutation:u8!, reversalPostingId:uuid!, payrollRunId:uuid!",
            1),
        record(
            0x0092,
            "latvian-payroll-settlement",
            "mutation:u8!, payrollRunId:uuid!, settlementKind:token!, postingId:uuid!, settledAmount:money!",
            1,
            2),
        record(
            0x0093,
            "latvian-payroll-settlement-reversal",
            "mutation:u8!, reversalPostingId:uuid!, payrollRunId:uuid!, settlementKind:token!",
            1),
        record(
            0x00A0,
            "restore.provenance",
            "mutation:u8!, backupId:uuid!, backupArtifactDigest:hash!, restoredFromOrder:u64!, historicalSnapshotAuthorization:bool!",
            1));
  }

  private static String hyphenated(String... components) {
    return String.join("-", components);
  }
}
