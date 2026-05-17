package dev.erst.fingrind.contract.operations;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BusinessEventId;
import dev.erst.fingrind.core.BusinessEventKind;
import dev.erst.fingrind.core.Counterparty;
import dev.erst.fingrind.core.EvidenceBundle;
import dev.erst.fingrind.core.FunctionalMeasurement;
import dev.erst.fingrind.core.InventoryItemId;
import dev.erst.fingrind.core.InventoryItemName;
import dev.erst.fingrind.core.InventoryQuantity;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.MigrationBatchId;
import dev.erst.fingrind.core.PositiveMoney;
import dev.erst.fingrind.core.PriorPeriodAdjustmentKind;
import dev.erst.fingrind.core.TaxCode;
import dev.erst.fingrind.core.TaxComponent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Sealed public command family for typed business events above the raw journal-entry layer. */
public sealed interface BusinessEventRequest
    permits BusinessEventRequest.IssueInvoice,
        BusinessEventRequest.ReceivePayment,
        BusinessEventRequest.RecordBill,
        BusinessEventRequest.PayBill,
        BusinessEventRequest.RecordSale,
        BusinessEventRequest.PurchaseInventory,
        BusinessEventRequest.RecordPayrollRun,
        BusinessEventRequest.RecordOwnerDraw,
        BusinessEventRequest.RecordBankFee,
        BusinessEventRequest.RecordTaxPayment,
        BusinessEventRequest.OpeningBalanceStatement,
        BusinessEventRequest.PriorPeriodAdjustment {
  /** Stable identifier for one business event. */
  BusinessEventId businessEventId();

  /** Canonical business-event kind. */
  BusinessEventKind businessEventKind();

  /** Effective accounting date for the event. */
  LocalDate effectiveDate();

  /** Source evidence bundle for the event. */
  EvidenceBundle evidenceBundle();

  /** Tax components attached to the event. */
  List<TaxComponent> taxComponents();

  /** Optional transaction-currency measurement evidence for the event. */
  Optional<FunctionalMeasurement> functionalMeasurement();

  /** Typed invoice issuance request. */
  record IssueInvoice(
      BusinessEventId businessEventId,
      LocalDate issueDate,
      LocalDate dueDate,
      Counterparty counterparty,
      AccountCode receivableAccountCode,
      AccountCode revenueAccountCode,
      PositiveMoney functionalAmount,
      Optional<TaxCode> taxCode,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public IssueInvoice {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(issueDate, "issueDate");
      Objects.requireNonNull(dueDate, "dueDate");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
      Objects.requireNonNull(revenueAccountCode, "revenueAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(taxCode, "taxCode");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
      if (dueDate.isBefore(issueDate)) {
        throw new IllegalArgumentException("Invoice due date must not be before issue date.");
      }
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.ISSUE_INVOICE;
    }

    @Override
    public LocalDate effectiveDate() {
      return issueDate;
    }
  }

  /** Typed payment-receipt request. */
  record ReceivePayment(
      BusinessEventId businessEventId,
      LocalDate receivedDate,
      BusinessEventId settledBusinessEventId,
      Counterparty counterparty,
      AccountCode cashAccountCode,
      AccountCode receivableAccountCode,
      PositiveMoney functionalAmount,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public ReceivePayment {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(receivedDate, "receivedDate");
      Objects.requireNonNull(settledBusinessEventId, "settledBusinessEventId");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECEIVE_PAYMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return receivedDate;
    }
  }

  /** Typed supplier-bill request. */
  record RecordBill(
      BusinessEventId businessEventId,
      LocalDate billDate,
      LocalDate dueDate,
      Counterparty counterparty,
      AccountCode payableAccountCode,
      AccountCode expenseAccountCode,
      PositiveMoney functionalAmount,
      Optional<TaxCode> taxCode,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordBill {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(billDate, "billDate");
      Objects.requireNonNull(dueDate, "dueDate");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(payableAccountCode, "payableAccountCode");
      Objects.requireNonNull(expenseAccountCode, "expenseAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(taxCode, "taxCode");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
      if (dueDate.isBefore(billDate)) {
        throw new IllegalArgumentException("Bill due date must not be before bill date.");
      }
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_BILL;
    }

    @Override
    public LocalDate effectiveDate() {
      return billDate;
    }
  }

  /** Typed supplier-payment request. */
  record PayBill(
      BusinessEventId businessEventId,
      LocalDate paidDate,
      BusinessEventId settledBusinessEventId,
      Counterparty counterparty,
      AccountCode cashAccountCode,
      AccountCode payableAccountCode,
      PositiveMoney functionalAmount,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public PayBill {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(paidDate, "paidDate");
      Objects.requireNonNull(settledBusinessEventId, "settledBusinessEventId");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(payableAccountCode, "payableAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.PAY_BILL;
    }

    @Override
    public LocalDate effectiveDate() {
      return paidDate;
    }
  }

  /** Typed inventory sale request for small-shop scenarios. */
  record RecordSale(
      BusinessEventId businessEventId,
      LocalDate soldDate,
      Counterparty counterparty,
      InventoryItemId inventoryItemId,
      InventoryItemName inventoryItemName,
      InventoryQuantity quantity,
      AccountCode settlementAccountCode,
      AccountCode revenueAccountCode,
      AccountCode inventoryAssetAccountCode,
      AccountCode costOfSalesAccountCode,
      PositiveMoney grossProceeds,
      PositiveMoney inventoryCost,
      Optional<TaxCode> taxCode,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordSale {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(soldDate, "soldDate");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(inventoryItemId, "inventoryItemId");
      Objects.requireNonNull(inventoryItemName, "inventoryItemName");
      Objects.requireNonNull(quantity, "quantity");
      Objects.requireNonNull(settlementAccountCode, "settlementAccountCode");
      Objects.requireNonNull(revenueAccountCode, "revenueAccountCode");
      Objects.requireNonNull(inventoryAssetAccountCode, "inventoryAssetAccountCode");
      Objects.requireNonNull(costOfSalesAccountCode, "costOfSalesAccountCode");
      Objects.requireNonNull(grossProceeds, "grossProceeds");
      Objects.requireNonNull(inventoryCost, "inventoryCost");
      Objects.requireNonNull(taxCode, "taxCode");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_SALE;
    }

    @Override
    public LocalDate effectiveDate() {
      return soldDate;
    }
  }

  /** Typed inventory-purchase request. */
  record PurchaseInventory(
      BusinessEventId businessEventId,
      LocalDate purchasedDate,
      Counterparty counterparty,
      InventoryItemId inventoryItemId,
      InventoryItemName inventoryItemName,
      InventoryQuantity quantity,
      AccountCode settlementAccountCode,
      AccountCode inventoryAssetAccountCode,
      PositiveMoney inventoryCost,
      Optional<TaxCode> taxCode,
      List<TaxComponent> taxComponents,
      Optional<FunctionalMeasurement> functionalMeasurement,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public PurchaseInventory {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(purchasedDate, "purchasedDate");
      Objects.requireNonNull(counterparty, "counterparty");
      Objects.requireNonNull(inventoryItemId, "inventoryItemId");
      Objects.requireNonNull(inventoryItemName, "inventoryItemName");
      Objects.requireNonNull(quantity, "quantity");
      Objects.requireNonNull(settlementAccountCode, "settlementAccountCode");
      Objects.requireNonNull(inventoryAssetAccountCode, "inventoryAssetAccountCode");
      Objects.requireNonNull(inventoryCost, "inventoryCost");
      Objects.requireNonNull(taxCode, "taxCode");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(functionalMeasurement, "functionalMeasurement");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.PURCHASE_INVENTORY;
    }

    @Override
    public LocalDate effectiveDate() {
      return purchasedDate;
    }
  }

  /** Typed payroll-run request. */
  record RecordPayrollRun(
      BusinessEventId businessEventId,
      LocalDate payrollDate,
      AccountCode wageExpenseAccountCode,
      AccountCode payrollLiabilityAccountCode,
      AccountCode cashAccountCode,
      PositiveMoney grossWages,
      PositiveMoney withholdingAmount,
      PositiveMoney netCashPaid,
      List<TaxComponent> taxComponents,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordPayrollRun {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(payrollDate, "payrollDate");
      Objects.requireNonNull(wageExpenseAccountCode, "wageExpenseAccountCode");
      Objects.requireNonNull(payrollLiabilityAccountCode, "payrollLiabilityAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(grossWages, "grossWages");
      Objects.requireNonNull(withholdingAmount, "withholdingAmount");
      Objects.requireNonNull(netCashPaid, "netCashPaid");
      taxComponents = List.copyOf(Objects.requireNonNull(taxComponents, "taxComponents"));
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_PAYROLL_RUN;
    }

    @Override
    public LocalDate effectiveDate() {
      return payrollDate;
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }

  /** Typed owner-draw request. */
  record RecordOwnerDraw(
      BusinessEventId businessEventId,
      LocalDate drawDate,
      AccountCode ownerDrawingsAccountCode,
      AccountCode cashAccountCode,
      PositiveMoney functionalAmount,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordOwnerDraw {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(drawDate, "drawDate");
      Objects.requireNonNull(ownerDrawingsAccountCode, "ownerDrawingsAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_OWNER_DRAW;
    }

    @Override
    public LocalDate effectiveDate() {
      return drawDate;
    }

    @Override
    public List<TaxComponent> taxComponents() {
      return List.of();
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }

  /** Typed bank-fee request. */
  record RecordBankFee(
      BusinessEventId businessEventId,
      LocalDate feeDate,
      AccountCode bankFeeExpenseAccountCode,
      AccountCode cashAccountCode,
      PositiveMoney functionalAmount,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordBankFee {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(feeDate, "feeDate");
      Objects.requireNonNull(bankFeeExpenseAccountCode, "bankFeeExpenseAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_BANK_FEE;
    }

    @Override
    public LocalDate effectiveDate() {
      return feeDate;
    }

    @Override
    public List<TaxComponent> taxComponents() {
      return List.of();
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }

  /** Typed tax-payment request. */
  record RecordTaxPayment(
      BusinessEventId businessEventId,
      LocalDate paymentDate,
      AccountCode taxLiabilityAccountCode,
      AccountCode cashAccountCode,
      PositiveMoney functionalAmount,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public RecordTaxPayment {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(paymentDate, "paymentDate");
      Objects.requireNonNull(taxLiabilityAccountCode, "taxLiabilityAccountCode");
      Objects.requireNonNull(cashAccountCode, "cashAccountCode");
      Objects.requireNonNull(functionalAmount, "functionalAmount");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.RECORD_TAX_PAYMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return paymentDate;
    }

    @Override
    public List<TaxComponent> taxComponents() {
      return List.of();
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }

  /** Typed opening-balance statement request. */
  record OpeningBalanceStatement(
      BusinessEventId businessEventId,
      MigrationBatchId migrationBatchId,
      JournalEntry openingEntry,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public OpeningBalanceStatement {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(migrationBatchId, "migrationBatchId");
      Objects.requireNonNull(openingEntry, "openingEntry");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.OPENING_BALANCE_STATEMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return openingEntry.effectiveDate();
    }

    @Override
    public List<TaxComponent> taxComponents() {
      return List.of();
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }

  /** Typed prior-period adjustment request. */
  record PriorPeriodAdjustment(
      BusinessEventId businessEventId,
      MigrationBatchId migrationBatchId,
      PriorPeriodAdjustmentKind adjustmentKind,
      JournalEntry adjustmentEntry,
      EvidenceBundle evidenceBundle)
      implements BusinessEventRequest {
    public PriorPeriodAdjustment {
      Objects.requireNonNull(businessEventId, "businessEventId");
      Objects.requireNonNull(migrationBatchId, "migrationBatchId");
      Objects.requireNonNull(adjustmentKind, "adjustmentKind");
      Objects.requireNonNull(adjustmentEntry, "adjustmentEntry");
      Objects.requireNonNull(evidenceBundle, "evidenceBundle");
    }

    @Override
    public BusinessEventKind businessEventKind() {
      return BusinessEventKind.PRIOR_PERIOD_ADJUSTMENT;
    }

    @Override
    public LocalDate effectiveDate() {
      return adjustmentEntry.effectiveDate();
    }

    @Override
    public List<TaxComponent> taxComponents() {
      return List.of();
    }

    @Override
    public Optional<FunctionalMeasurement> functionalMeasurement() {
      return Optional.empty();
    }
  }
}
