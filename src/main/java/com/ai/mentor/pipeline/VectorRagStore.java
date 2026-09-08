package com.ai.mentor.pipeline;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class VectorRagStore {
    private static final int DIMENSIONS = 128;
    private final List<VectorChunk> chunks = List.of(
            create("mentor-principles", "Use small specific actions and review progress regularly."),
            create("math-explanations", "For math, show assumptions, intermediate steps, and a final check."),
            create("wellbeing-boundaries", "For immediate danger, contact local emergency services or a trusted person.")
    );

    public List<String> search(String question, int limit) {
        double[] queryVector = vectorize(question);
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk.content(), cosine(queryVector, chunk.vector())))
                .filter(chunk -> chunk.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(limit)
                .map(ScoredChunk::content)
                .toList();
    }

    private static VectorChunk create(String id, String content) {
        return new VectorChunk(id, content, vectorize(content));
    }

    private static double[] vectorize(String text) {
        double[] vector = new double[DIMENSIONS];
        Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .forEach(token -> vector[Math.floorMod(token.hashCode(), DIMENSIONS)]++);
        return vector;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftLength = 0;
        double rightLength = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftLength += left[index] * left[index];
            rightLength += right[index] * right[index];
        }
        return leftLength == 0 || rightLength == 0
                ? 0
                : dot / (Math.sqrt(leftLength) * Math.sqrt(rightLength));
    }

    private record VectorChunk(String id, String content, double[] vector) {}
    private record ScoredChunk(String content, double score) {}
}
