package com.gosu.iconpackgenerator.domain.mockups.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockupPromptGenerationService {

    private static final String COMPONENT_SET_BASE = "A high-quality, modern UI component mockup on a square 1:1 canvas. " +
            "Use a consistent visual language with rounded corners, subtle depth, and even spacing on a neutral background. " +
            "Include only the following components: %s. " +
            "Arrange the components in a clean grid with generous spacing and no overlap. " +
            "No text labels, no logos, no branding, no real-world content. " +
            "Keep the background clearly different from the UI elements to avoid transparency issues.";

    private static final String COMPONENT_SET_TEXT_PROMPT =
            "General theme: %s. " + COMPONENT_SET_BASE;

    private static final String COMPONENT_SET_IMAGE_PROMPT =
            "Using the provided reference image as a style guide, " + COMPONENT_SET_BASE;

    private static final String TEXT_TO_IMAGE_PROMPT_TEMPLATE = "A high-quality, modern user interface mockup in a consistent style - general theme: %s.\n" +
            "The design should include buttons, icons, sliders, player controls, chat bubbles , progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
            "Each component should share the same visual language — soft shadows, rounded corners, consistent color palette, and even spacing.\n" +
            "The layout should show distinct UI elements without visible grid lines or borders.\n" +
            "\n" +
            "The mockup should look like a professional design system overview or UI kit preview, not a wireframe.\n" +
            "All text (if any) should be minimal and used only as placeholder content.\n" +
            "\n" +
            "Emphasize clarity, readability, and design consistency.\n" +
            "Use subtle lighting and depth to separate components naturally.\n" +
            "No branding, no logos, no real-world content — only abstract UI shapes and elements.";

    private static final String IMAGE_TO_IMAGE_PROMPT_TEMPLATE =
            "Using the provided reference image as a style guide\n" +
                    "A high-quality, modern user interface mockup in a consistent style - with general theme as in reference image\n" +
                    "The design should include buttons, icons, sliders, player controls ,chat bubbles, progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
                    "Each component should share the same visual language — soft shadows, rounded corners, consistent color palette, and even spacing.\n" +
                    "The layout should show distinct UI elements without visible grid lines or borders.\n" +
                    "\n" +
                    "The mockup should look like a professional design system overview or UI kit preview, not a wireframe.\n" +
                    "All text (if any) should be minimal and used only as placeholder content.\n" +
                    "\n" +
                    "Emphasize clarity, readability, and design consistency.\n" +
                    "Use subtle lighting and depth to separate components naturally.\n" +
                    "No branding, no logos, no real-world content — only abstract UI shapes and elements.";

    public static final String SECOND_GENERATION_VARIATION =
            " with professional color palette refined design aesthetics and enhanced visual appeal";

    /**
     * Generate prompt for UI mockup based on text description
     */
    public String generatePromptForMockup(String description) {
        String prompt = String.format(TEXT_TO_IMAGE_PROMPT_TEMPLATE, description);
        log.debug("Generated UI mockup prompt: {}", prompt);
        return prompt;
    }

    /**
     * Generate prompt for reference image-based mockup generation
     */
    public String generatePromptForReferenceImage(String description) {
        String prompt = String.format(IMAGE_TO_IMAGE_PROMPT_TEMPLATE, description);
        log.debug("Generated reference image mockup prompt: {}", prompt);
        return prompt;
    }

    public String generateComponentSetPromptForText(String description, String componentsCsv) {
        String prompt = String.format(COMPONENT_SET_TEXT_PROMPT, description, componentsCsv);
        log.debug("Generated component set prompt (text): {}", prompt);
        return prompt;
    }

    public String generateComponentSetPromptForReference(String componentsCsv) {
        String prompt = String.format(COMPONENT_SET_IMAGE_PROMPT, componentsCsv);
        log.debug("Generated component set prompt (reference): {}", prompt);
        return prompt;
    }
}
