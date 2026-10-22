package com.az.studyforge.generation.planning;

import com.az.studyforge.document.extraction.TextNormalizer;
import com.az.studyforge.generation.domain.QuestionType;
import com.az.studyforge.generation.llm.model.PageText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkPlannerTest {

    private final ChunkPlanner planner = new ChunkPlanner();

    // ===== helpers =====

    private static String words(int n, int wordsPerLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append('w').append(i).append((i + 1) % wordsPerLine == 0 ? '\n' : ' ');
        }
        return sb.toString().strip();
    }

    private static List<PageText> doc(int pages, int wordsPerPage) {
        List<PageText> result = new ArrayList<>();
        for (int i = 1; i <= pages; i++) result.add(new PageText(i, words(wordsPerPage, 10)));
        return result;
    }

    private static List<Integer> mcqTargets(List<PlannedChunk> chunks) {
        return chunks.stream().map(PlannedChunk::mcqTarget).toList();
    }

    private static List<Integer> essayTargets(List<PlannedChunk> chunks) {
        return chunks.stream().map(PlannedChunk::essayTarget).toList();
    }

    private static List<String> ranges(List<PlannedChunk> chunks) {
        return chunks.stream().map(c -> c.pageFrom() + "-" + c.pageTo()).toList();
    }


    @ParameterizedTest(name = "{0} words, {1} questions -> {2} chunks")
    @CsvSource({
            "18000, 50, 10",
            "18000, 3,  3",
            "18000, 10, 5",
            "3000,  50, 4",
            "1200,  10, 1",
            "9000,  30, 5",
    })
    void chunkCount(int words, int questions, int expected) {
        assertThat(ChunkPlanner.chunkCount(words, questions)).isEqualTo(expected);
    }


    @Test
    void longDocument_mcq50_tenEqualChunks() {
        List<PlannedChunk> chunks = planner.plan(doc(60, 300), QuestionMix.of(QuestionType.MCQ, 50));

        assertThat(chunks).hasSize(10);
        assertThat(mcqTargets(chunks)).containsOnly(5);
        assertThat(essayTargets(chunks)).containsOnly(0);
        assertThat(ranges(chunks)).startsWith("1-6", "7-12").endsWith("55-60");
        assertThat(chunks).allSatisfy(c -> assertThat(c.wordCount()).isEqualTo(1800));
    }

    @Test
    void longDocument_essay3_coversWholeDocument() {
        List<PlannedChunk> chunks = planner.plan(doc(60, 300), QuestionMix.of(QuestionType.ESSAY, 3));

        assertThat(ranges(chunks)).containsExactly("1-20", "21-40", "41-60");
        assertThat(essayTargets(chunks)).containsExactly(1, 1, 1);
        assertThat(mcqTargets(chunks)).containsOnly(0);
    }

    @Test
    void mixed30_essaysSpreadAcrossChunks() {
        List<PlannedChunk> chunks = planner.plan(doc(30, 300), QuestionMix.of(QuestionType.MIXED, 30));

        assertThat(mcqTargets(chunks)).containsExactly(5, 6, 5, 6, 5);
        assertThat(essayTargets(chunks)).containsExactly(1, 0, 1, 0, 1);
    }

    @Test
    void shortDocument_singleChunkWithAllPages() {
        List<PlannedChunk> chunks = planner.plan(doc(12, 100), QuestionMix.of(QuestionType.MCQ, 10));

        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.pageFrom()).isEqualTo(1);
            assertThat(c.pageTo()).isEqualTo(12);
            assertThat(c.pages()).hasSize(12);
            assertThat(c.mcqTarget()).isEqualTo(10);
            assertThat(c.wordCount()).isEqualTo(1200);
        });
    }

    @Test
    void singleHugePage_splitButKeepsPageNumber() {
        List<PlannedChunk> chunks = planner.plan(doc(1, 5000), QuestionMix.of(QuestionType.MCQ, 50));

        assertThat(chunks).hasSize(4);
        assertThat(mcqTargets(chunks)).containsExactly(13, 12, 13, 12);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.pageFrom()).isEqualTo(1);
            assertThat(c.pageTo()).isEqualTo(1);
        });
        assertThat(chunks.stream().mapToInt(PlannedChunk::wordCount).sum()).isEqualTo(5000);
    }

    @Test
    void fewDensePages_splitToStayWithin15PerChunk() {
        List<PlannedChunk> chunks = planner.plan(doc(2, 1500), QuestionMix.of(QuestionType.MCQ, 50));

        assertThat(ranges(chunks)).containsExactly("1-1", "1-1", "2-2", "2-2");
        assertThat(mcqTargets(chunks)).containsExactly(13, 12, 13, 12);
    }

    @Test
    void blankPages_skipped() {
        List<PageText> pages = List.of(
                new PageText(1, words(200, 10)),
                new PageText(2, "   "),
                new PageText(3, words(200, 10)),
                new PageText(4, ""));

        List<PlannedChunk> chunks = planner.plan(pages, QuestionMix.of(QuestionType.MCQ, 10));

        List<Integer> usedPages = chunks.stream()
                .flatMap(c -> c.pages().stream())
                .map(PageText::pageNumber)
                .toList();
        assertThat(usedPages).containsExactly(1, 3);
    }

    @Test
    void allBlankPages_rejected() {
        List<PageText> pages = List.of(new PageText(1, ""), new PageText(2, "  \n "));

        assertThatThrownBy(() -> planner.plan(pages, QuestionMix.of(QuestionType.MCQ, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroQuestions_rejected() {
        assertThatThrownBy(() -> planner.plan(doc(3, 100), new QuestionMix(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultIsImmutable() {
        List<PlannedChunk> chunks = planner.plan(doc(10, 300), QuestionMix.of(QuestionType.MCQ, 10));

        assertThatThrownBy(() -> chunks.removeFirst()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> chunks.getFirst().pages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invariants_holdForRandomDocuments() {
        Random random = new Random(42);
        int[] counts = {3, 5, 7, 10, 30, 50};

        for (int run = 0; run < 2_000; run++) {
            List<PageText> pages = new ArrayList<>();
            int pageCount = 1 + random.nextInt(100);
            for (int i = 1; i <= pageCount; i++) {
                boolean blank = random.nextInt(5) == 0;
                pages.add(new PageText(i, blank ? "" : words(1 + random.nextInt(900), 1 + random.nextInt(15))));
            }
            int totalWords = pages.stream().mapToInt(p -> TextNormalizer.countWords(p.content())).sum();
            if (totalWords == 0) continue;

            QuestionType type = QuestionType.values()[random.nextInt(3)];
            int count = counts[random.nextInt(counts.length)];
            if (totalWords < 1500 && count > 10) count = type == QuestionType.ESSAY ? 3 : 10;

            QuestionMix mix = QuestionMix.of(type, count);
            List<PlannedChunk> chunks = planner.plan(pages, mix);
            String context = "run=%d pages=%d words=%d %s x%d".formatted(run, pageCount, totalWords, type, count);

            assertThat(chunks.stream().mapToInt(PlannedChunk::mcqTarget).sum()).as(context).isEqualTo(mix.mcq());
            assertThat(chunks.stream().mapToInt(PlannedChunk::essayTarget).sum()).as(context).isEqualTo(mix.essay());

            for (int i = 0; i < chunks.size(); i++) {
                PlannedChunk c = chunks.get(i);
                assertThat(c.index()).as(context).isEqualTo(i);
                assertThat(c.mcqTarget() + c.essayTarget()).as(context).isBetween(1, 15);
                assertThat(c.pageFrom()).as(context).isLessThanOrEqualTo(c.pageTo());
                assertThat(c.pages()).as(context).allSatisfy(p -> assertThat(p.content()).isNotBlank());
                if (i > 0) {
                    assertThat(c.pageFrom()).as(context).isGreaterThanOrEqualTo(chunks.get(i - 1).pageTo());
                }
            }
        }
    }
}
