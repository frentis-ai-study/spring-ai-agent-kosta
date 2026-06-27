package com.kosta.demo.pdfrag.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 업로드된 PDF 바이트를 페이지별 {@link Document}로 변환한다.
 * PagePdfDocumentReader(PDFBox)를 감싼 얇은 어댑터 — 테스트에서는 mock으로 대체된다.
 */
@Component
public class PdfTextExtractor {

    public List<Document> extract(byte[] bytes, String filename) {
        Resource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new PagePdfDocumentReader(resource).read();
    }
}
