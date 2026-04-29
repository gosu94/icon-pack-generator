package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconPromptEnhancementService {

    private static final String PROMPT_ENHANCER_SYSTEM_PROMPT = "You are an art director specializing in crafting vivid, cohesive icon pack prompts. Rewrite the user input into a clear, descriptive but concise creative brief. Mention color palette, tone, shapes, and stylistic cues. Keep it under 80 words and in natural sentences without bullet points.";
    private static final String PROMPT_ENHANCER_USER_TEMPLATE = "Original description: \"%s\". Rewrite this so it guides an AI model to design a cohesive icon pack with a unified style, colors, materials, and lighting.";

    private final AnyLlmModelService anyLlmModelService;

    public String enhanceIfPossible(String generalDescription) {
        if (generalDescription == null) {
            return null;
        }

        String trimmed = generalDescription.trim();
        if (trimmed.isEmpty()) {
            return generalDescription;
        }

        try {
            log.info("Enhancing icon prompt with AnyLlmModelService");
            String detailedPrompt = anyLlmModelService
                    .generateCompletion(String.format(PROMPT_ENHANCER_USER_TEMPLATE, trimmed), PROMPT_ENHANCER_SYSTEM_PROMPT)
                    .join();
            if (detailedPrompt != null) {
                String cleaned = detailedPrompt.trim();
                if (!cleaned.isEmpty()) {
                    log.debug("Prompt enhanced from '{}' to '{}'", trimmed, cleaned);
                    return cleaned;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enhance icon prompt, falling back to original description", e);
        }

        return generalDescription;
    }
}
