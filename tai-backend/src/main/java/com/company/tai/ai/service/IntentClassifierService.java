package com.company.tai.ai.service;

import com.company.tai.ai.entity.AiQueryExample;
import com.company.tai.ai.repository.AiQueryExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * A multinomial Naive Bayes text classifier, trained from scratch on every call from the
 * {@code ai_query_examples} table — no external API, no persisted model file. Because it
 * refits from the live table each time, adding more labeled examples (via
 * POST /api/ai/query-examples) immediately improves future classifications: this is the
 * "continuous learning from DB data" mechanism for the natural-language query feature.
 * Deliberately small and dependency-free (Laplace-smoothed word-frequency counting) since
 * the vocabulary/dataset here is tiny — a full ML library would be overkill.
 */
@Service
@RequiredArgsConstructor
public class IntentClassifierService {

    private final AiQueryExampleRepository aiQueryExampleRepository;

    public record Prediction(String intent, double confidence, boolean trained) {}

    public Prediction classify(String question) {
        List<AiQueryExample> examples = aiQueryExampleRepository.findAll();
        if (examples.isEmpty()) {
            return new Prediction(null, 0.0, false);
        }

        Map<String, List<String>> tokensByIntent = new HashMap<>();
        Map<String, Integer> docCountByIntent = new HashMap<>();
        Set<String> vocabulary = new HashSet<>();

        for (AiQueryExample example : examples) {
            List<String> tokens = tokenize(example.getQuestionText());
            tokensByIntent.computeIfAbsent(example.getIntent(), k -> new ArrayList<>()).addAll(tokens);
            docCountByIntent.merge(example.getIntent(), 1, Integer::sum);
            vocabulary.addAll(tokens);
        }

        int totalDocs = examples.size();
        int vocabSize = Math.max(vocabulary.size(), 1);
        List<String> queryTokens = tokenize(question);

        Map<String, Double> logScores = new HashMap<>();
        for (String intent : docCountByIntent.keySet()) {
            double logPrior = Math.log((double) docCountByIntent.get(intent) / totalDocs);
            List<String> intentTokens = tokensByIntent.get(intent);
            Map<String, Long> freq = new HashMap<>();
            for (String t : intentTokens) freq.merge(t, 1L, Long::sum);
            long totalWordsInIntent = intentTokens.size();

            double logLikelihood = 0.0;
            for (String token : queryTokens) {
                long count = freq.getOrDefault(token, 0L);
                // Laplace (add-one) smoothing so unseen words don't zero out the probability.
                double prob = (count + 1.0) / (totalWordsInIntent + vocabSize);
                logLikelihood += Math.log(prob);
            }
            logScores.put(intent, logPrior + logLikelihood);
        }

        String bestIntent = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (var entry : logScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestIntent = entry.getKey();
            }
        }

        // Convert log-scores to a normalized posterior probability (softmax over log-scores)
        // so the confidence value is interpretable as "how sure" rather than a raw log value.
        double maxLog = bestScore;
        double sumExp = 0.0;
        for (double score : logScores.values()) sumExp += Math.exp(score - maxLog);
        double confidence = 1.0 / sumExp;

        return new Prediction(bestIntent, confidence, true);
    }

    private List<String> tokenize(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> !t.isBlank())
                .toList();
    }
}
