package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Prevents the executable preimage catalog from drifting from its public protocol contract. */
class AttestationPreimageCatalogContractTest {
  private static final String DOCUMENT = "docs/DOC_02_VerifiableOperationAttestation.md";

  @Test
  void catalog_matchesThePublishedRequestAndEffectRecordTables() throws IOException {
    Map<Integer, CatalogRow> documented = documentedRows();
    Map<Integer, AttestationRecordSchema> implemented = implementedRows();

    assertEquals(documented.keySet(), implemented.keySet());
    for (Map.Entry<Integer, CatalogRow> documentedEntry : documented.entrySet()) {
      AttestationRecordSchema implementedRow =
          Objects.requireNonNull(implemented.get(documentedEntry.getKey()));
      CatalogRow documentedRow = documentedEntry.getValue();
      assertEquals(documentedRow.name(), implementedRow.name());
      assertEquals(documentedRow.fieldSpecification(), implementedRow.fieldSpecification());
      assertEquals(
          documentedRow.sortKeyFieldIndexes(),
          java.util.Arrays.stream(implementedRow.sortKeyFieldIndexes()).boxed().toList());
    }
  }

  private static Map<Integer, CatalogRow> documentedRows() throws IOException {
    List<String> lines = Files.readAllLines(protocolDocument());
    List<CatalogRow> rows = new ArrayList<>();
    boolean inCatalog = false;
    for (String line : lines) {
      if ("### Request Record Catalog".equals(line) || "### Effect Record Catalog".equals(line)) {
        inCatalog = true;
      } else if (inCatalog && line.startsWith("### ")) {
        inCatalog = false;
      } else if (inCatalog && line.startsWith("| ") && !line.startsWith("| Tag")) {
        String[] cells = line.substring(1, line.length() - 1).split("\\|");
        if (cells.length == 4 && !cells[0].strip().startsWith(":")) {
          CatalogRow row = CatalogRow.parse(cells);
          rows.add(row);
        }
      }
    }
    return rows.stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(CatalogRow::recordTypeTag, row -> row));
  }

  private static Map<Integer, AttestationRecordSchema> implementedRows() {
    return Stream.concat(
            AttestationRequestRecordCatalog.schemas().stream(),
            AttestationEffectRecordCatalog.schemas().stream())
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                AttestationRecordSchema::recordTypeTag, schema -> schema));
  }

  private static Path protocolDocument() {
    for (Path directory = Path.of("").toAbsolutePath();
        directory != null;
        directory = directory.getParent()) {
      Path document = directory.resolve(DOCUMENT);
      if (Files.isRegularFile(document)) {
        return document;
      }
    }
    throw new IllegalStateException(
        "Cannot locate " + DOCUMENT + " from the test working directory.");
  }

  private record CatalogRow(
      int recordTypeTag,
      String name,
      String fieldSpecification,
      List<Integer> sortKeyFieldIndexes) {
    private static CatalogRow parse(String[] cells) {
      String fieldSpecification = cells[2].strip();
      String[] fields = Pattern.compile(", ").split(fieldSpecification);
      List<String> sortKeyNames = Pattern.compile(", ").splitAsStream(cells[3].strip()).toList();
      List<Integer> sortKeyFieldIndexes =
          sortKeyNames.stream().map(sortKeyName -> fieldIndex(fields, sortKeyName)).toList();
      return new CatalogRow(
          Integer.parseInt(cells[0].strip(), 16),
          cells[1].strip(),
          fieldSpecification,
          sortKeyFieldIndexes);
    }

    private static int fieldIndex(String[] fields, String sortKeyName) {
      for (int index = 0; index < fields.length; index++) {
        if (fields[index].substring(0, fields[index].indexOf(':')).equals(sortKeyName)) {
          return index;
        }
      }
      throw new IllegalStateException("Documented sort-key field is not part of its record.");
    }
  }
}
