package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationRecordSchema.record;

import java.util.List;
import java.util.stream.Stream;

/** Catalog-owned request record layouts and canonical per-type sort keys. */
final class AttestationRequestRecordCatalog {
  private AttestationRequestRecordCatalog() {}

  static List<AttestationRecordSchema> schemas() {
    return Stream.of(foundationalSchemas(), operationSchemas()).flatMap(List::stream).toList();
  }

  private static List<AttestationRecordSchema> foundationalSchemas() {
    return List.of(
        record(
            0x0100,
            "request.command",
            "operationKind:token!, idempotencyKey:text?, causationId:text?, sourceChannel:token!",
            0),
        record(
            0x0101,
            "request.book-identity",
            "bookId:uuid!, entityName:text!, kernelProfile:token!, accountingBasis:token!, frameworkPosition:token!, entityForm:token!, template:token!, costingDoctrine:token?, functionalCurrency:currency!, fiscalYearStartMonth:u8!, fiscalYearStartDay:u8!, bookStartDate:date!",
            0),
        record(
            0x0102,
            "request.founder",
            "principalId:uuid!, keyId:hash!, spki:spki!, credentialPurpose:token!",
            0,
            1),
        record(0x0103, "request.policy-rule", "capability:token!, quorum:u16!", 0),
        record(
            0x0110,
            "request.account",
            "accountCode:text!, accountName:text!, accountType:token!, nodeKind:token!, parentAccountCode:text?, unitOfMeasure:text?",
            0),
        record(
            0x0111,
            "request.account-classification",
            "accountCode:text!, classificationFamily:token!, classification:token!",
            0,
            1),
        record(
            0x0112,
            "request.account-relationship",
            "accountCode:text!, relationshipKind:token!, targetAccountCode:text?",
            0,
            1),
        record(
            0x0113,
            "request.tax-registration",
            "registrationId:text!, registrationName:text!, jurisdiction:text!, registrationCode:text?, payableAccountCode:text!, receivableAccountCode:text!, obligationFrequency:token!, dueDaysAfterPeriodEnd:u16!, active:bool!",
            0),
        record(
            0x0114,
            "request.tax-registration-code",
            "registrationId:text!, taxCode:text!, taxCodeName:text!, rate:scaled!, inclusionMode:token!, applicationKind:token!",
            0,
            1));
  }

  private static List<AttestationRecordSchema> operationSchemas() {
    return List.of(
        record(
            0x0120,
            "request.posting",
            "stepOrder:u32!, operationKind:token!, effectiveDate:date!, postingKind:token!, priorPostingId:uuid?, reversalReason:text?",
            0),
        record(
            0x0121, "request.account-role", "stepOrder:u32!, role:token!, accountCode:text!", 0, 1),
        record(0x0122, "request.money", "stepOrder:u32!, role:token!, amount:money!", 0, 1),
        record(0x0123, "request.quantity", "stepOrder:u32!, role:token!, quantity:scaled!", 0, 1),
        record(
            0x0124,
            "request.evidence-document",
            "stepOrder:u32!, sourceDocumentId:text!, sourceDocumentType:text!, documentDate:date!",
            0,
            1),
        record(
            0x0125,
            "request.posting-approval",
            "postingId:uuid!, approvalId:text!, approverReference:text!, approverType:token!, decision:token!, approvedAt:instant!",
            0,
            1),
        record(
            0x0126,
            "request.tax-selection",
            "stepOrder:u32!, registrationId:text!, taxCode:text!",
            0,
            1,
            2),
        record(
            0x0127,
            "request.foreign-exchange",
            "stepOrder:u32!, foreignCurrency:currency!, foreignAmount:money!, functionalAmount:money!, exchangeRate:scaled!",
            0),
        record(
            0x0128,
            "request.inventory-movement",
            "stepOrder:u32!, inventoryAccountCode:text!, counterAccountCode:text!, movementKind:token!, quantity:scaled!",
            0),
        record(
            0x0129,
            "request.settlement-adjunct",
            "stepOrder:u32!, accountCode:text!, amount:money!",
            0,
            1),
        record(
            0x012A,
            "request.journal-line",
            "stepOrder:u32!, lineOrder:u32!, accountCode:text!, side:token!, amount:money!, quantity:scaled?",
            0,
            1),
        record(
            0x0130,
            "request.accrual-cutoff",
            "stepOrder:u32!, cutoffId:uuid!, cutoffKind:token!, recognitionStart:date?, recognitionEnd:date?",
            0,
            1),
        record(
            0x0131,
            "request.fixed-asset",
            "stepOrder:u32!, assetId:uuid!, assetClass:token?, usefulLifeMonths:u32?",
            0,
            1),
        record(0x0132, "request.financing", "stepOrder:u32!, arrangementId:uuid!", 0, 1),
        record(
            0x0133,
            "request.foreign-currency-obligation",
            "stepOrder:u32!, obligationId:uuid!, settlementId:uuid?",
            0,
            1,
            2),
        record(
            0x0134,
            "request.payroll",
            "stepOrder:u32!, payrollRunId:uuid!, employeeReference:text?, payrollMonth:text?, withholdingProfile:token?, taxBookHeldAtEmployer:bool?, dependantCount:u8?",
            0,
            1),
        record(
            0x0140,
            "request.period-close",
            "closeKind:token!, effectiveFrom:date?, effectiveTo:date?, fiscalYear:u32?, resultHoldingAccountCode:text?, capitalAccountCode:text?, retainedResultAccountCode:text?",
            0,
            2),
        record(0x0141, "request.system-workflow-run", "workflowId:uuid!", 0),
        record(
            0x0150,
            "request.backup-acknowledgement",
            "backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash!",
            0),
        record(
            0x0160,
            "request.restore",
            "backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash!",
            0),
        record(0x0170, "request.rekey", "keyEpoch:u64!, reason:text?", 0),
        record(
            0x0180,
            "request.credential-binding",
            "principalId:uuid!, keyId:hash!, bindingAction:token!, spki:spki!, credentialPurpose:token!, predecessorKeyId:hash?",
            0,
            1),
        record(0x0182, "request.policy-change", "capability:token!, quorum:u16!", 0),
        record(
            0x0183,
            "request.principal-capability-grant",
            "principalId:uuid!, capability:token!, grantState:token!",
            0,
            1),
        record(
            0x0184,
            "request.system-workflow-policy",
            "workflowId:uuid!, workflowKind:token!, resultHoldingAccountCode:text!, capitalAccountCode:text?, retainedResultAccountCode:text?, active:bool!",
            0),
        record(
            0x0185,
            "request.credential-retirement",
            "keyId:hash!, principalId:uuid!, retirementState:token!, reason:text?",
            0));
  }
}
