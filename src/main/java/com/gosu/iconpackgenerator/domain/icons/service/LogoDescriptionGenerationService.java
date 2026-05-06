package com.gosu.iconpackgenerator.domain.icons.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogoDescriptionGenerationService {

    private static final String SYSTEM_PROMPT =
            "You are a brand identity art director. Generate exactly 9 concise logo concept descriptions " +
                    "for a single theme. Each description must be distinct, visual, and suitable for image generation. " +
                    "Focus on logo marks, emblems, monograms, mascots, symbols, or badges as appropriate. " +
                    "Return only a JSON array of 9 strings with no markdown, no explanation, and no extra text.";

    private static final String USER_PROMPT_TEMPLATE =
            "Theme: \"%s\". Create 9 distinct logo concept descriptions for this theme. " +
                    "Keep each item under 18 words. Make the set stylistically cohesive but conceptually varied.";

    private final AnyLlmModelService anyLlmModelService;
    private final ObjectMapper objectMapper;

    public List<String> generateDescriptions(String generalDescription) {
        String trimmed = generalDescription == null ? "" : generalDescription.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String response = anyLlmModelService
                    .generateCompletion(String.format(USER_PROMPT_TEMPLATE, trimmed), SYSTEM_PROMPT)
                    .join();
            List<String> parsed = parseResponse(response);
            if (parsed.size() == 9) {
                return parsed;
            }
            log.warn("Logo description generation returned {} items, using fallback descriptions", parsed.size());
        } catch (Exception e) {
            log.warn("Failed to generate logo descriptions with LLM, using fallback descriptions", e);
        }

        return buildFallbackDescriptions(trimmed);
    }

    private List<String> parseResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return List.of();
        }

        try {
            List<String> values = objectMapper.readValue(response, new TypeReference<List<String>>() {});
            return normalize(values);
        } catch (Exception ignored) {
            return normalize(splitLoosely(response));
        }
    }

    private List<String> splitLoosely(String response) {
        return response.lines()
                .map(line -> line.replaceFirst("^\\s*[-*\\d.\\)]\\s*", "").trim())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private List<String> normalize(List<String> rawValues) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : rawValues) {
            if (value == null) {
                continue;
            }
            String cleaned = value.trim();
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
            if (unique.size() == 9) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private List<String> buildFallbackDescriptions(String theme) {
        return List.of(
                "minimal monogram logo for " + theme,
                "bold geometric emblem for " + theme,
                "clean circular badge logo for " + theme,
                "sleek abstract symbol for " + theme,
                "modern shield mark for " + theme,
                "playful mascot logo for " + theme,
                "premium line-art crest for " + theme,
                "dynamic angular icon mark for " + theme,
                "elegant stamp-style logo for " + theme
        );
    }
}
