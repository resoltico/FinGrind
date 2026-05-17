module dev.erst.fingrind.report.pdf {
  exports dev.erst.fingrind.report.pdf;

  requires dev.erst.fingrind.contract;
  requires java.desktop;
  requires java.logging;
  requires org.apache.pdfbox;
  requires static org.jspecify;
}
