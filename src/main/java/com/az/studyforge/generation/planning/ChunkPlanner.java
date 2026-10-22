package com.az.studyforge.generation.planning;

import com.az.studyforge.document.extraction.TextNormalizer;
import com.az.studyforge.generation.llm.model.PageText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkPlanner {

    static final int TARGET_WORDS = 1800;
    static final int MAX_WORDS = 6000;
    static final int MAX_QUESTIONS_PER_CHUNK = 15;
    static final int MIN_QUESTIONS_PER_CHUNK = 2;

    private record Unit(PageText page, int words) {}

    public List<PlannedChunk> plan(List<PageText> pages, QuestionMix mix) {
        if (mix.total() <= 0) throw new IllegalArgumentException("Nothing to generate");

        List<Unit> units = pages.stream()
                .map(p -> new Unit(p, TextNormalizer.countWords(p.content())))
                .filter(u -> u.words() > 0)                     // bỏ trang trắng
                .toList();
        if (units.isEmpty()) throw new IllegalArgumentException("Document has no text");

        int totalWords = units.stream().mapToInt(Unit::words).sum();

        for (int n = chunkCount(totalWords, mix.total()); ; n++) {
            List<PlannedChunk> chunks = build(units, n, totalWords, mix);
            boolean withinLimit = chunks.stream()
                    .allMatch(c -> c.mcqTarget() + c.essayTarget() <= MAX_QUESTIONS_PER_CHUNK);
            if (withinLimit || n >= mix.total()) return chunks;
        }
    }

    static int chunkCount(int totalWords, int questions) {
        int byWords = Math.ceilDiv(totalWords, TARGET_WORDS);
        int lo = Math.max(Math.ceilDiv(questions, MAX_QUESTIONS_PER_CHUNK), Math.ceilDiv(totalWords, MAX_WORDS));
        int hi = Math.max(1, questions / MIN_QUESTIONS_PER_CHUNK);
        return Math.max(lo, Math.min(byWords, hi));
    }

    private static List<PlannedChunk> build(List<Unit> all, int wanted, int totalWords, QuestionMix mix) {
        List<Unit> units = splitLargePages(all, Math.ceilDiv(totalWords, wanted));
        int n = Math.min(wanted, units.size());

        List<List<Unit>> groups = group(units, n, totalWords);
        int[] weights = groups.stream().mapToInt(g -> g.stream().mapToInt(Unit::words).sum()).toArray();
        int[] mcq = Allocation.proportional(mix.mcq(), weights);
        int[] essay = Allocation.proportional(mix.essay(), weights);

        List<PlannedChunk> chunks = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (mcq[i] + essay[i] == 0) continue;
            List<PageText> chunkPages = groups.get(i).stream().map(Unit::page).toList();
            chunks.add(new PlannedChunk(chunks.size(), chunkPages, weights[i], mcq[i], essay[i]));
        }
        return List.copyOf(chunks);
    }

    private static List<List<Unit>> group(List<Unit> units, int n, int totalWords) {
        List<List<Unit>> groups = new ArrayList<>(n);
        List<Unit> current = new ArrayList<>();
        long cumulative = 0;

        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            int groupsLeftAfterCurrent = n - groups.size() - 1;
            int unitsLeftIncludingThis = units.size() - i;

            if (!current.isEmpty() && groupsLeftAfterCurrent > 0) {
                double share = (double) totalWords * (groups.size() + 1) / n;
                boolean closerIfStopHere = share - cumulative < cumulative + unit.words() - share;
                // Còn đúng đủ trang cho mỗi nhóm sau 1 trang thì bắt buộc phải cắt
                if (closerIfStopHere || unitsLeftIncludingThis == groupsLeftAfterCurrent) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(unit);
            cumulative += unit.words();
        }
        groups.add(current);
        return groups;
    }

    private static List<Unit> splitLargePages(List<Unit> units, int targetWords) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : units) {
            if (u.words() <= targetWords) {
                out.add(u);
                continue;
            }
            int parts = Math.ceilDiv(u.words(), targetWords);
            int perPart = Math.ceilDiv(u.words(), parts);

            StringBuilder sb = new StringBuilder();
            int words = 0;
            for (String line : u.page().content().split("\n")) {
                sb.append(line).append('\n');
                words += TextNormalizer.countWords(line);
                if (words >= perPart) {
                    out.add(new Unit(new PageText(u.page().pageNumber(), sb.toString().strip()), words));
                    sb.setLength(0);
                    words = 0;
                }
            }
            if (words > 0) out.add(new Unit(new PageText(u.page().pageNumber(), sb.toString().strip()), words));
        }
        return out;
    }
}
