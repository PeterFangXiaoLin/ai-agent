package com.example.aiagent.controller;

import com.example.aiagent.agent.AgentArtifacts;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/ai/artifacts/pdf")
public class AgentArtifactController {

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String fileName) {
        try {
            Path path = AgentArtifacts.resolvePdf(fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                    .body(new FileSystemResource(path));
        } catch (IOException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
