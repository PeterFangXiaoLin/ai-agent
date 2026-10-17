package com.example.aiagent.agent;

import com.example.aiagent.agent.model.AgentEvent;
import com.example.aiagent.constant.FileConstant;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** 只向客户端公开已生成、位于 PDF 产物目录内的真实文件。 */
public final class AgentArtifacts {

    private static final String PDF_SUCCESS_PREFIX = "PDF generated successfully to: ";
    private static final Path PDF_ROOT = Path.of(FileConstant.FILE_SAVE_DIR, "pdf").toAbsolutePath().normalize();

    private AgentArtifacts() {
    }

    public static Path resolvePdf(String fileName) throws IOException {
        Path candidate = PDF_ROOT.resolve(fileName).normalize();
        if (!candidate.getParent().equals(PDF_ROOT)
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !Files.isRegularFile(candidate)) {
            throw new IOException("PDF 文件不存在");
        }
        // 检查真实路径，避免通过符号链接跳出产物目录。
        Path actual = candidate.toRealPath();
        if (!actual.getParent().equals(PDF_ROOT.toRealPath())) {
            throw new IOException("无效的 PDF 文件路径");
        }
        return actual;
    }

    public static Optional<AgentEvent> fromPdfResult(String result) {
        if (!result.startsWith(PDF_SUCCESS_PREFIX)) {
            return Optional.empty();
        }
        try {
            Path candidate = Path.of(result.substring(PDF_SUCCESS_PREFIX.length()).trim()).toAbsolutePath().normalize();
            if (!PDF_ROOT.equals(candidate.getParent())) {
                return Optional.empty();
            }
            String fileName = candidate.getFileName().toString();
            resolvePdf(fileName);
            String url = "/ai/artifacts/pdf/" + UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);
            return Optional.of(AgentEvent.artifact(fileName, url));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
