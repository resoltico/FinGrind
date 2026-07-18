package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Verifies request-field ownership for each Latvian payroll posting command. */
class CliLatvianPayrollBookkeepingEntryReadersTest extends CliRequestReaderTestSupport {
  @Test
  void readers_bindMonthlyPayrollAndBothSettlementRequestsToTypedEntries() throws IOException {
    LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll.class,
            CliLatvianPayrollBookkeepingEntryReaders.read(
                rootNode(
                    """
                {
                  "effectiveDate": "2026-07-31",
                  "payrollRunId": "payroll-run-2026-07-employee-001",
                  "employeeReference": "employee-001",
                  "payrollMonth": "2026-07",
                  "taxBookHeldAtEmployer": true,
                  "dependantCount": 0,
                  "wageExpenseAccountCode": "5000",
                  "employerSocialContributionExpenseAccountCode": "5010",
                  "netWagesPayableAccountCode": "2200",
                  "employeeSocialContributionPayableAccountCode": "2210",
                  "employerSocialContributionPayableAccountCode": "2220",
                  "personalIncomeTaxPayableAccountCode": "2230",
                  "grossWages": %s
                }
                """
                        .formatted(eurMoneyJson("200000"))),
                BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL));
    LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWages =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.NetWageSettlement.class,
            CliLatvianPayrollBookkeepingEntryReaders.read(
                rootNode(
                    """
                {
                  "effectiveDate": "2026-08-01",
                  "payrollRunId": "payroll-run-2026-07-employee-001",
                  "cashAccountCode": "1000"
                }
                """),
                BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT));
    LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.StateRemittance.class,
            CliLatvianPayrollBookkeepingEntryReaders.read(
                rootNode(
                    """
                {
                  "effectiveDate": "2026-08-05",
                  "payrollRunId": "payroll-run-2026-07-employee-001",
                  "cashAccountCode": "1000"
                }
                """),
                BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE));

    assertEquals("payroll-run-2026-07-employee-001", monthlyPayroll.payrollRunId().value());
    assertEquals("employee-001", monthlyPayroll.employeeReference().value());
    assertEquals("200000", monthlyPayroll.grossWages().minorUnits());
    assertEquals("1000", netWages.cashAccountCode().value());
    assertEquals("1000", stateRemittance.cashAccountCode().value());
    assertInstanceOf(LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll.class, monthlyPayroll);
  }

  private static ObjectNode rootNode(String json) throws IOException {
    return (ObjectNode) CliJsonObjectMappers.configuredObjectMapper().readTree(json);
  }
}
