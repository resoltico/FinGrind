package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Extracts cross-format fact sets from the model and JSON projections. */
final class ReportCrossFormatStructuredFacts {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private ReportCrossFormatStructuredFacts() {}

  static Set<String> modelFacts(ReportModel model) {
    Set<String> facts = new LinkedHashSet<>();
    appendVerdictFacts(facts, "summary", model.verdicts());
    appendVerdictFacts(facts, "context", model.context().rows());
    for (ReportSection section : model.sections()) {
      facts.add(sectionTitleFact(section.key(), section.title()));
      appendVerdictFacts(facts, section.key(), section.verdicts());
      appendTableFacts(facts, section.key(), "table", section.columns(), section.rows());
      for (ReportTotals totals : section.totals()) {
        facts.add(totalsTitleFact(section.key(), totals.title()));
        appendTableFacts(facts, section.key(), totals.title(), totals.columns(), totals.rows());
      }
    }
    return facts;
  }

  static Set<String> jsonFacts(Object payload) throws IOException {
    JsonNode root = JSON_MAPPER.readTree(CliWireJson.jsonText(payload));
    Set<String> facts = new LinkedHashSet<>();
    appendJsonVerdictFacts(facts, "summary", root.path("verdicts"));
    appendJsonContextFacts(facts, root.path("context"));
    for (JsonNode section : root.path("sections")) {
      String sectionKey = section.path("key").asText();
      String sectionTitle = section.path("title").asText();
      facts.add(sectionTitleFact(sectionKey, sectionTitle));
      appendJsonVerdictFacts(facts, sectionKey, section.path("verdicts"));
      appendJsonTableFacts(
          facts, sectionKey, "table", section.path("columns"), section.path("rows"));
      for (JsonNode totals : section.path("totals")) {
        String totalsTitle = totals.path("title").asText();
        facts.add(totalsTitleFact(sectionKey, totalsTitle));
        appendJsonTableFacts(
            facts, sectionKey, totalsTitle, totals.path("columns"), totals.path("rows"));
      }
    }
    return facts;
  }

  static String verdictFact(String sectionKey, String label, String value) {
    return "verdict|" + sectionKey + "|" + label + "|" + value;
  }

  static String sectionTitleFact(String sectionKey, String title) {
    return "section|" + sectionKey + "|" + title;
  }

  static String totalsTitleFact(String sectionKey, String title) {
    return "totals|" + sectionKey + "|" + title;
  }

  static String columnFact(
      String sectionKey, String blockTitle, int columnIndex, String columnKey, String columnTitle) {
    return "column|"
        + sectionKey
        + "|"
        + blockTitle
        + "|"
        + columnIndex
        + "|"
        + columnKey
        + "|"
        + columnTitle;
  }

  static String cellFact(
      String sectionKey,
      String blockTitle,
      int rowIndex,
      int columnIndex,
      String columnKey,
      String value) {
    return "cell|"
        + sectionKey
        + "|"
        + blockTitle
        + "|"
        + rowIndex
        + "|"
        + columnIndex
        + "|"
        + columnKey
        + "|"
        + value;
  }

  private static void appendVerdictFacts(
      Set<String> facts, String sectionKey, List<ReportVerdict> verdicts) {
    for (ReportVerdict verdict : verdicts) {
      facts.add(verdictFact(sectionKey, verdict.label(), verdict.value()));
    }
  }

  private static void appendTableFacts(
      Set<String> facts,
      String sectionKey,
      String blockTitle,
      List<ReportColumn> columns,
      List<ReportRow> rows) {
    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
      ReportColumn column = columns.get(columnIndex);
      facts.add(columnFact(sectionKey, blockTitle, columnIndex, column.key(), column.title()));
    }
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      ReportRow row = rows.get(rowIndex);
      for (int index = 0; index < columns.size(); index++) {
        facts.add(
            cellFact(
                sectionKey,
                blockTitle,
                rowIndex,
                index,
                columns.get(index).key(),
                row.cells().get(index)));
      }
    }
  }

  private static void appendJsonVerdictFacts(
      Set<String> facts, String sectionKey, JsonNode verdicts) {
    for (JsonNode verdict : verdicts) {
      facts.add(
          verdictFact(sectionKey, verdict.path("label").asText(), verdict.path("value").asText()));
    }
  }

  private static void appendJsonContextFacts(Set<String> facts, JsonNode context) {
    appendJsonOptionalContextFact(facts, context, "Entity", "entity");
    appendJsonOptionalContextFact(facts, context, "Seed template", "seedTemplate");
    appendJsonOptionalContextFact(facts, context, "Accounting basis", "accountingBasis");
    appendJsonOptionalContextFact(facts, context, "Functional currency", "functionalCurrency");
    appendJsonOptionalContextFact(facts, context, "Fiscal year start", "fiscalYearStart");
    appendJsonOptionalContextFact(facts, context, "Posting coverage", "postingCoverage");
    appendJsonOptionalContextFact(facts, context, "Period start", "periodStart");
    appendJsonOptionalContextFact(facts, context, "Period end", "periodEnd");
    appendJsonOptionalContextFact(facts, context, "As of", "asOf");
    appendJsonOptionalContextFact(
        facts, context, "Comparative period start", "comparativePeriodStart");
    appendJsonOptionalContextFact(facts, context, "Comparative period end", "comparativePeriodEnd");
    appendJsonOptionalContextFact(facts, context, "Tax registration id", "taxRegistrationId");
    appendJsonOptionalContextFact(facts, context, "Tax registration name", "taxRegistrationName");
    appendJsonOptionalContextFact(facts, context, "Jurisdiction", "taxJurisdiction");
    appendJsonOptionalContextFact(facts, context, "Due date", "dueDate");
    appendJsonVerdictFacts(facts, "context", context.path("supplementalRows"));
  }

  private static void appendJsonOptionalContextFact(
      Set<String> facts, JsonNode context, String label, String fieldName) {
    JsonNode value = context.path(fieldName);
    if (!value.isMissingNode() && !value.isNull()) {
      facts.add(verdictFact("context", label, value.asText()));
    }
  }

  private static void appendJsonTableFacts(
      Set<String> facts, String sectionKey, String blockTitle, JsonNode columns, JsonNode rows) {
    List<String> columnKeys = new ArrayList<>();
    int columnIndex = 0;
    for (JsonNode column : columns) {
      String columnKey = column.path("key").asText();
      columnKeys.add(columnKey);
      facts.add(
          columnFact(
              sectionKey, blockTitle, columnIndex, columnKey, column.path("title").asText()));
      columnIndex++;
    }
    int rowIndex = 0;
    for (JsonNode row : rows) {
      JsonNode cells = row.path("cells");
      for (int index = 0; index < columnKeys.size(); index++) {
        facts.add(
            cellFact(
                sectionKey,
                blockTitle,
                rowIndex,
                index,
                columnKeys.get(index),
                cells.path(index).asText()));
      }
      rowIndex++;
    }
  }
}
