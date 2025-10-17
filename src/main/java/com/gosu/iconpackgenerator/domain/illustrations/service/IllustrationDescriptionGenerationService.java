package com.gosu.iconpackgenerator.domain.illustrations.service;

import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for generating missing illustration descriptions using AI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IllustrationDescriptionGenerationService {
    
    private final AnyLlmModelService anyLlmModelService;
    
    private static final String GENERATE_ALL_DESCRIPTIONS_SYSTEM_PROMPT = 
        "You are a creative assistant helping generate distinct illustration descriptions. " +
        "Generate exactly 4 unique, diverse, and specific illustration descriptions that fit the given theme. " +
        "Each description should be concise (1-2 sentences max) and visually distinct from the others. " +
        "Return ONLY the 4 descriptions, one per line, numbered 1-4. No additional text or explanations.";
    
    private static final String GENERATE_MISSING_DESCRIPTIONS_SYSTEM_PROMPT = 
        "You are a creative assistant helping generate distinct illustration descriptions. " +
        "Generate unique illustration descriptions that complement the existing ones provided. " +
        "Each new description should be concise (1-2 sentences max), visually distinct, and avoid repeating themes from existing descriptions. " +
        "Return ONLY the new descriptions, one per line, numbered. No additional text or explanations.";
    
    /**
     * Generate all 4 individual descriptions when none are provided
     * 
     * @param generalTheme The general theme for the illustrations
     * @return CompletableFuture containing list of 4 generated descriptions
     */
    public CompletableFuture<List<String>> generateAllDescriptions(String generalTheme) {
        log.info("Generating all 4 individual descriptions for theme: {}", generalTheme);
        
        String prompt = String.format(
            "Theme: %s\n\n" +
            "Generate 4 distinct illustration descriptions that fit this theme. " +
            "Make them diverse and visually interesting.",
            generalTheme
        );
        
        return anyLlmModelService.generateCompletion(prompt, GENERATE_ALL_DESCRIPTIONS_SYSTEM_PROMPT)
            .thenApply(response -> {
                List<String> descriptions = parseDescriptions(response, 4);
                log.info("Generated {} descriptions for theme: {}", descriptions.size(), generalTheme);
                return descriptions;
            })
            .exceptionally(error -> {
                log.error("Error generating all descriptions, using fallback", error);
                return generateFallbackDescriptions(generalTheme, 4, new ArrayList<>());
            });
    }
    
    /**
     * Generate missing individual descriptions when some are provided
     * 
     * @param generalTheme The general theme for the illustrations
     * @param existingDescriptions List of existing descriptions (non-empty ones)
     * @param totalNeeded Total number of descriptions needed (usually 4)
     * @return CompletableFuture containing complete list of descriptions (existing + generated)
     */
    public CompletableFuture<List<String>> generateMissingDescriptions(
            String generalTheme, 
            List<String> existingDescriptions, 
            int totalNeeded) {
        
        int missingCount = totalNeeded - existingDescriptions.size();
        if (missingCount <= 0) {
            return CompletableFuture.completedFuture(new ArrayList<>(existingDescriptions));
        }
        
        log.info("Generating {} missing descriptions for theme: {} (have {} existing)", 
            missingCount, generalTheme, existingDescriptions.size());
        
        String existingList = existingDescriptions.stream()
            .map(desc -> "- " + desc)
            .collect(Collectors.joining("\n"));
        
        String prompt = String.format(
            "Theme: %s\n\n" +
            "Existing illustrations:\n%s\n\n" +
            "Generate %d additional illustration descriptions that:\n" +
            "- Fit the theme\n" +
            "- Are visually distinct from the existing ones\n" +
            "- Complement the existing illustrations\n" +
            "- Don't repeat any themes or subjects already covered",
            generalTheme,
            existingList,
            missingCount
        );
        
        return anyLlmModelService.generateCompletion(prompt, GENERATE_MISSING_DESCRIPTIONS_SYSTEM_PROMPT)
            .thenApply(response -> {
                List<String> newDescriptions = parseDescriptions(response, missingCount);
                log.info("Generated {} new descriptions to complement existing ones", newDescriptions.size());
                
                // Combine existing and new descriptions
                List<String> allDescriptions = new ArrayList<>(existingDescriptions);
                allDescriptions.addAll(newDescriptions);
                
                // Ensure we have exactly totalNeeded descriptions
                while (allDescriptions.size() < totalNeeded) {
                    allDescriptions.add(generateSimpleFallback(generalTheme, allDescriptions.size()));
                }
                
                return allDescriptions.subList(0, totalNeeded);
            })
            .exceptionally(error -> {
                log.error("Error generating missing descriptions, using fallback", error);
                List<String> allDescriptions = new ArrayList<>(existingDescriptions);
                allDescriptions.addAll(generateFallbackDescriptions(generalTheme, missingCount, existingDescriptions));
                return allDescriptions;
            });
    }
    
    /**
     * Parse LLM response into individual descriptions
     */
    private List<String> parseDescriptions(String response, int expectedCount) {
        List<String> descriptions = new ArrayList<>();
        
        // Split by newlines and clean up
        String[] lines = response.split("\n");
        for (String line : lines) {
            String cleaned = line.trim();
            
            // Remove numbering (1., 2., etc.) if present
            cleaned = cleaned.replaceFirst("^\\d+\\.\\s*", "");
            cleaned = cleaned.replaceFirst("^\\d+\\)\\s*", "");
            cleaned = cleaned.replaceFirst("^-\\s*", "");
            
            if (!cleaned.isEmpty() && cleaned.length() > 5) { // Minimum length check
                descriptions.add(cleaned);
            }
        }
        
        // If we didn't get enough descriptions, try splitting by periods or other delimiters
        if (descriptions.size() < expectedCount && !response.contains("\n")) {
            descriptions.clear();
            String[] sentences = response.split("\\d+\\.");
            for (String sentence : sentences) {
                String cleaned = sentence.trim();
                if (!cleaned.isEmpty() && cleaned.length() > 5) {
                    descriptions.add(cleaned);
                }
            }
        }
        
        // Ensure we have exactly the expected count
        while (descriptions.size() < expectedCount) {
            descriptions.add("Illustration " + (descriptions.size() + 1));
        }
        
        return descriptions.subList(0, expectedCount);
    }
    
    /**
     * Generate fallback descriptions when LLM fails
     */
    private List<String> generateFallbackDescriptions(String generalTheme, int count, List<String> existing) {
        log.warn("Using fallback descriptions for theme: {}", generalTheme);
        List<String> fallbacks = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            int number = existing.size() + i + 1;
            fallbacks.add(String.format("%s illustration %d", generalTheme, number));
        }
        
        return fallbacks;
    }
    
    /**
     * Generate a single simple fallback description
     */
    private String generateSimpleFallback(String generalTheme, int index) {
        return String.format("%s illustration %d", generalTheme, index + 1);
    }
    
    /**
     * Ensure we have exactly 4 descriptions, generating missing ones if needed
     */
    public CompletableFuture<List<String>> ensureFourDescriptions(
            String generalTheme, List<String> providedDescriptions) {
        
        if (providedDescriptions == null) {
            providedDescriptions = new ArrayList<>();
        }
        
        // Filter out empty descriptions
        List<String> nonEmptyDescriptions = providedDescriptions.stream()
            .filter(desc -> desc != null && !desc.trim().isEmpty())
            .collect(Collectors.toList());
        
        // If we have all 4, return them
        if (nonEmptyDescriptions.size() >= 4) {
            return CompletableFuture.completedFuture(nonEmptyDescriptions.subList(0, 4));
        }
        
        // If we have none, generate all 4
        if (nonEmptyDescriptions.isEmpty()) {
            return generateAllDescriptions(generalTheme);
        }
        
        // If we have some, generate the missing ones
        return generateMissingDescriptions(generalTheme, nonEmptyDescriptions, 4);
    }
}

