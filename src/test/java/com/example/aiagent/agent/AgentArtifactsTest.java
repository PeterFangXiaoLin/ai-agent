package com.example.aiagent.agent;

import com.example.aiagent.constant.FileConstant;
import com.example.aiagent.controller.AgentArtifactController;
import com.example.aiagent.tools.PDFGenerationTool;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentArtifactsTest {

    @Test
    void exposesAnExistingPdfAndDownloadsIt() throws Exception {
        Path directory = Path.of(FileConstant.FILE_SAVE_DIR, "pdf");
        Files.createDirectories(directory);
        Path pdf = Files.createTempFile(directory, "agent-output-", ".pdf");
        try {
            String result = new PDFGenerationTool().generatePDF(pdf.getFileName().toString(), "约会计划：午后散步与晚餐");
            var event = AgentArtifacts.fromPdfResult(result).orElseThrow();
            assertEquals(pdf.getFileName().toString(), event.title());
            try (PdfDocument document = new PdfDocument(new PdfReader(pdf.toString()))) {
                assertTrue(PdfTextExtractor.getTextFromPage(document.getPage(1)).contains("约会计划"));
            }
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentArtifactController()).build();
            mockMvc.perform(get(event.url()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                    .andExpect(content().bytes(Files.readAllBytes(pdf)));
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void rejectsMissingFilesAndPathsOutsideThePdfDirectory() throws Exception {
        assertTrue(AgentArtifacts.fromPdfResult("PDF generated successfully to: /outside/file.pdf").isEmpty());
        assertTrue(AgentArtifacts.fromPdfResult("Error generating PDF: failed").isEmpty());
        assertThrows(IOException.class, () -> AgentArtifacts.resolvePdf("../application.yml"));
        MockMvcBuilders.standaloneSetup(new AgentArtifactController()).build()
                .perform(get("/ai/artifacts/pdf/nonexistent-agent-output.pdf"))
                .andExpect(status().isNotFound());
    }
}
