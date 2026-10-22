package com.az.studyforge.generation.llm;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.document.entity.FileType;
import com.az.studyforge.document.extraction.TextExtractor;
import com.az.studyforge.document.extraction.TextNormalizer;
import com.az.studyforge.generation.domain.OutputLanguage;
import com.az.studyforge.generation.llm.dto.LlmRequest;
import com.az.studyforge.generation.llm.dto.LlmResult;
import com.az.studyforge.generation.llm.model.ChunkSpec;
import com.az.studyforge.generation.llm.model.PageText;
import com.az.studyforge.generation.llm.model.ParseResult;
import com.az.studyforge.generation.llm.parse.QuestionParser;
import com.az.studyforge.generation.llm.promt.PromptBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Tag("llm")
@EnabledIfEnvironmentVariable(named = "PROMPT_SAMPLE_FILE", matches = ".+")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PromptPlaygroundTest {

    @Autowired
    List<TextExtractor> extractors;
    @Autowired
    PromptBuilder promptBuilder;
    @Autowired
    QuestionParser parser;
    @Autowired
    LlmClient llmClient;
    @Autowired
    JsonMapper json;

    @Test
    void generateFromRealDocument() throws Exception {
        Path file = Path.of(System.getenv("PROMPT_SAMPLE_FILE"));
        FileType type = file.toString().toLowerCase().endsWith(".pptx") ? FileType.PPTX : FileType.PDF;
        TextExtractor extractor = extractors.stream().filter(e -> e.supports() == type).findFirst().orElseThrow();

        List<String> raw;
        try (InputStream in = Files.newInputStream(file)) {
            raw = extractor.extractPages(in);
        }

        List<PageText> pages = new ArrayList<>();
        int words = 0;
        for (int i = 0; i < raw.size() && words < 1800; i++) {
            String text = TextNormalizer.normalize(raw.get(i));
            words += TextNormalizer.countWords(text);
            if (!text.isBlank()) pages.add(new PageText(i + 1, text));
        }

        var chunk = new ChunkSpec(pages.getFirst().pageNumber(), pages.getLast().pageNumber(),
                pages, 5, 2, OutputLanguage.VI, List.of());

        LlmResult res = llmClient.complete(new LlmRequest(promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(chunk), promptBuilder.maxTokens(chunk), null, null));
        ParseResult parsed = parser.parse(res.content(), chunk);

        System.out.printf("%n=== %s | pages %d-%d | %d words | %s tokens in, %s out ===%n",
                file.getFileName(), chunk.pageFrom(), chunk.pageTo(), words, res.promptTokens(), res.completionTokens());
        Path out = Path.of("target", "prompt-playground",
                file.getFileName() + "." + PromptBuilder.PROMPT_VERSION + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(parsed),
                StandardCharsets.UTF_8);
        System.out.println("Saved to " + out.toAbsolutePath());
    }
}