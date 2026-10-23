package com.az.studyforge.generation.llm.parse;

import com.az.studyforge.generation.llm.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class QuestionParser {

    private static final List<String> LABELS = List.of("A", "B", "C", "D");
    private static final int MAX_TEXT = 2000;
    private static final Pattern OPTION_PREFIX = Pattern.compile("^[A-Da-d][.):]\\s+");
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");
    private static final int MAX_TOPIC = 80;

    private final JsonMapper json;

    private static final class Rejected extends RuntimeException {
        Rejected(String reason) { super(reason, null, false, false); }
    }

    public ParseResult parse(String raw, ChunkSpec chunk) {
        JsonNode root = readRoot(raw);
        JsonNode items = root.isArray() ? root : root.path("questions");
        if (!items.isArray()) throw new InvalidLlmOutputException("Missing \"questions\" array");

        List<GeneratedQuestion> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int mcq = 0, essay = 0;

        for (int i = 0; i < items.size(); i++) {
            try {
                GeneratedQuestion q = toQuestion(items.get(i), chunk);
                if (!seen.add(q.normalizedHash())) throw new Rejected("duplicate in the same response");

                switch (q) {
                    case GeneratedMcq m -> {
                        if (mcq >= chunk.mcqTarget()) throw new Rejected("more MCQ than requested");
                        mcq++;
                    }
                    case GeneratedEssay e -> {
                        if (essay >= chunk.essayTarget()) throw new Rejected("more essays than requested");
                        essay++;
                    }
                }
                accepted.add(q);
            } catch (Rejected r) {
                rejected.add("#" + (i + 1) + ": " + r.getMessage());
            }
        }
        return new ParseResult(List.copyOf(accepted), List.copyOf(rejected));
    }

    private GeneratedQuestion toQuestion(JsonNode node, ChunkSpec chunk) {
        if (!node.isObject()) throw new Rejected("not an object");

        String type = str(node.path("type")).strip().toUpperCase(Locale.ROOT);
        String question = text(node, "question");
        List<Integer> pages = sourcePages(node.path("sourcePages"), chunk);
        String hash = QuestionNormalizer.hash(question);
        String topic = topic(node);                                       // mới

        return switch (type) {
            case "MCQ" -> mcq(node, question, pages, hash, topic);
            case "ESSAY" -> essay(node, question, pages, hash, topic);
            default -> throw new Rejected("unknown type '" + type + "'");
        };
    }

    private static String topic(JsonNode node) {
        String t = str(node.path("topic")).strip();
        if (t.isEmpty()) return null;
        return t.length() <= MAX_TOPIC ? t : t.substring(0, MAX_TOPIC).strip();
    }

    private GeneratedMcq mcq(JsonNode node, String question, List<Integer> pages, String hash, String topic) {
        JsonNode opts = node.path("options");
        List<String> options = new ArrayList<>(4);

        if (opts.isObject()) {
            for (String label : LABELS) options.add(text(opts, label));
        } else if (opts.isArray() && opts.size() == 4) {
            for (int i = 0; i < 4; i++) options.add(clean(str(opts.get(i)), "option " + LABELS.get(i)));
        } else {
            throw new Rejected("options must have exactly A, B, C, D");
        }

        options.replaceAll(o -> OPTION_PREFIX.matcher(o).replaceFirst(""));
        if (options.stream().map(o -> o.toLowerCase(Locale.ROOT)).distinct().count() < 4) {
            throw new Rejected("options are not distinct");
        }

        String answer = str(node.path("answer")).strip().toUpperCase(Locale.ROOT);
        int correct = answer.isEmpty() ? -1 : LABELS.indexOf(answer.substring(0, 1));
        if (correct < 0) throw new Rejected("answer must be A, B, C or D");

        return new GeneratedMcq(question, List.copyOf(options), correct,
                text(node, "explanation"), pages, hash, topic);
    }

    private GeneratedEssay essay(JsonNode node, String question, List<Integer> pages, String hash, String topic) {
        JsonNode rubricNode = node.path("rubric");
        if (!rubricNode.isArray() || rubricNode.size() < 2 || rubricNode.size() > 6) {
            throw new Rejected("rubric must have 2-6 points");
        }
        List<String> rubric = new ArrayList<>();
        for (int i = 0; i < rubricNode.size(); i++) rubric.add(clean(str(rubricNode.get(i)), "rubric point"));

        return new GeneratedEssay(question, text(node, "modelAnswer"), List.copyOf(rubric), pages, hash, topic);
    }

    private List<Integer> sourcePages(JsonNode node, ChunkSpec chunk) {
        if (!node.isArray()) throw new Rejected("sourcePages missing");

        Set<Integer> pages = new TreeSet<>();
        for (int i = 0; i < node.size(); i++) {
            Integer p = toInt(node.get(i));
            if (p != null && p >= chunk.pageFrom() && p <= chunk.pageTo()) pages.add(p);
        }
        if (pages.isEmpty()) {
            throw new Rejected("no sourcePages within " + chunk.pageFrom() + "-" + chunk.pageTo());
        }
        return List.copyOf(pages);
    }

    private static Integer toInt(JsonNode n) {
        if (n.isNumber()) return n.intValue();
        try {
            return Integer.valueOf(str(n).strip());     // "3" thay vì 3
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode parent, String field) {
        return clean(str(parent.path(field)), field);
    }

    private static String clean(String value, String field) {
        String v = value.strip();
        if (v.isEmpty()) throw new Rejected(field + " is empty");
        if (v.length() > MAX_TEXT) throw new Rejected(field + " is too long");
        return v;
    }

    static String extractJsonObject(String raw) {
        String s = raw.strip();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }

    private JsonNode readRoot(String raw) {
        String s = extractJsonObject(raw);
        try {
            return json.readTree(s);
        } catch (JacksonException first) {
            try {
                JsonNode wrapped = json.readTree("[" + s + "]");
                if (wrapped.size() > 1) return wrapped;
            } catch (JacksonException ignored) {
                // không cứu được → báo lỗi gốc
            }
            throw new InvalidLlmOutputException("Response is not valid JSON: " + first.getOriginalMessage(), first);
        }
    }

    private static String str(JsonNode n) {
        return n != null && n.isValueNode() && !n.isNull() ? n.asString() : "";
    }
}
