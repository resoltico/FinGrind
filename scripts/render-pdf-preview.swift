import AppKit
import Foundation
import PDFKit

enum RenderPdfPreviewFailure: Error {
  case invalidArguments
  case missingFirstPage
  case missingGraphicsContext
  case pngEncodingFailed
}

guard CommandLine.arguments.count == 3 else {
  fputs("usage: render-pdf-preview.swift <input.pdf> <output.png>\n", stderr)
  throw RenderPdfPreviewFailure.invalidArguments
}

let pdfURL = URL(fileURLWithPath: CommandLine.arguments[1])
let outputURL = URL(fileURLWithPath: CommandLine.arguments[2])

guard let document = PDFDocument(url: pdfURL), let page = document.page(at: 0) else {
  fputs("failed to load the first PDF page\n", stderr)
  throw RenderPdfPreviewFailure.missingFirstPage
}

let pageBounds = page.bounds(for: .mediaBox)
let targetLongestSide: CGFloat = 1800
let scale = targetLongestSide / max(pageBounds.width, pageBounds.height)
let canvasSize = NSSize(width: pageBounds.width * scale, height: pageBounds.height * scale)

let image = NSImage(size: canvasSize)
image.lockFocus()
NSColor.white.setFill()
NSBezierPath(rect: NSRect(origin: .zero, size: canvasSize)).fill()

guard let context = NSGraphicsContext.current?.cgContext else {
  fputs("failed to acquire a graphics context\n", stderr)
  throw RenderPdfPreviewFailure.missingGraphicsContext
}

context.saveGState()
context.scaleBy(x: scale, y: scale)
page.draw(with: .mediaBox, to: context)
context.restoreGState()
image.unlockFocus()

guard let tiff = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else {
  fputs("failed to encode PNG output\n", stderr)
  throw RenderPdfPreviewFailure.pngEncodingFailed
}

try FileManager.default.createDirectory(
  at: outputURL.deletingLastPathComponent(),
  withIntermediateDirectories: true)
try png.write(to: outputURL)
