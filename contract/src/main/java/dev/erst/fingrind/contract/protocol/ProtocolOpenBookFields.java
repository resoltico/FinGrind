package dev.erst.fingrind.contract.protocol;

/** Canonical open-book request field names shared by parser and public protocol surfaces. */
public final class ProtocolOpenBookFields {
  public static final String ENTITY_NAME = "entityName";
  public static final String BOOK_TEMPLATE_ID = "bookTemplateId";
  public static final String ACCOUNTING_BASIS = "accountingBasis";
  public static final String INVENTORY_COSTING = "inventoryCosting";
  public static final String FUNCTIONAL_CURRENCY = "functionalCurrency";
  public static final String FISCAL_YEAR_START = "fiscalYearStart";

  private ProtocolOpenBookFields() {}
}
