package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountPage;
import java.util.List;

/** Renders declared-account page payloads for human and CSV output modes. */
final class CliAccountPageOutputRenderer {
  private CliAccountPageOutputRenderer() {}

  static String renderHuman(AccountPage page) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Returned accounts", Integer.toString(page.accounts().size())),
                List.of("Limit", Integer.toString(page.limit())),
                List.of(
                    "Next cursor",
                    page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)"))));
    String table =
        CliTextFormat.renderTable(
            List.of("Account", "Name", "Normal balance", "Active", "Declared at"),
            page.accounts().stream()
                .map(
                    account ->
                        List.of(
                            account.accountCode().value(),
                            account.accountName().value(),
                            account.normalBalance().wireValue(),
                            Boolean.toString(account.active()),
                            account.declaredAt().toString()))
                .toList());
    return CliTextFormat.renderTitledBlock(
        "Accounts", summary + System.lineSeparator() + System.lineSeparator() + table);
  }

  static String renderCsv(AccountPage page) {
    return CliTextFormat.renderCsv(
        List.of("accountCode", "accountName", "normalBalance", "active", "declaredAt"),
        page.accounts().stream()
            .map(
                account ->
                    List.of(
                        account.accountCode().value(),
                        account.accountName().value(),
                        account.normalBalance().wireValue(),
                        Boolean.toString(account.active()),
                        account.declaredAt().toString()))
            .toList());
  }
}
