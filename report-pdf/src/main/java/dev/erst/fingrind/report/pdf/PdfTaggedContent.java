package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;

/** Owns the tagged-PDF reading-order spine for generated FinGrind reports. */
final class PdfTaggedContent {
  private final PDStructureTreeRoot treeRoot;
  private final PDStructureElement documentElement;
  private final Map<Integer, COSArray> parentTreeEntries = new ConcurrentHashMap<>();
  private int currentPageParentKey = -1;
  private int nextMarkedContentId;

  PdfTaggedContent(PDDocument document) {
    PDDocument checkedDocument = Objects.requireNonNull(document, "document");
    PDMarkInfo markInfo = new PDMarkInfo();
    markInfo.setMarked(true);
    markInfo.setSuspect(false);
    checkedDocument.getDocumentCatalog().setMarkInfo(markInfo);
    checkedDocument.getDocumentCatalog().setLanguage("en");
    treeRoot = new PDStructureTreeRoot();
    checkedDocument.getDocumentCatalog().setStructureTreeRoot(treeRoot);
    documentElement = new PDStructureElement("Document", treeRoot);
    documentElement.setLanguage("en");
    treeRoot.appendKid(documentElement);
  }

  void beginPage(PDPage page) {
    PDPage checkedPage = Objects.requireNonNull(page, "page");
    currentPageParentKey = parentTreeEntries.size();
    checkedPage.setStructParents(currentPageParentKey);
    parentTreeEntries.put(currentPageParentKey, new COSArray());
    nextMarkedContentId = 0;
  }

  void drawParagraph(PDPageContentStream contentStream, PDPage page, PdfTextDrawAction draw)
      throws IOException {
    PDPage checkedPage = Objects.requireNonNull(page, "page");
    COSArray pageParentEntries =
        Objects.requireNonNull(parentTreeEntries.get(currentPageParentKey), "page parent entries");
    int markedContentId = nextMarkedContentId;
    nextMarkedContentId++;
    PDStructureElement paragraph = new PDStructureElement("P", documentElement);
    paragraph.setPage(checkedPage);
    paragraph.appendKid(markedContentId);
    documentElement.appendKid(paragraph);
    pageParentEntries.add(paragraph.getCOSObject());
    PDPageContentStream checkedStream = Objects.requireNonNull(contentStream, "contentStream");
    checkedStream.beginMarkedContent(COSName.P, markedContentId);
    try {
      Objects.requireNonNull(draw, "draw").draw();
    } finally {
      checkedStream.endMarkedContent();
    }
  }

  void finish() {
    COSArray numbers = new COSArray();
    parentTreeEntries.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              int key = entry.getKey();
              COSArray values = entry.getValue();
              numbers.add(COSInteger.get(key));
              numbers.add(values);
            });
    COSDictionary parentTree = new COSDictionary();
    parentTree.setItem(COSName.NUMS, numbers);
    treeRoot.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
    treeRoot.setParentTreeNextKey(parentTreeEntries.size());
  }

  /** Draws one marked text run inside the currently active PDF content stream. */
  @FunctionalInterface
  interface PdfTextDrawAction {
    /** Writes the text run's PDF operators. */
    void draw() throws IOException;
  }
}
