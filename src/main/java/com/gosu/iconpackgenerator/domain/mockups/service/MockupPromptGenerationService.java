package com.gosu.iconpackgenerator.domain.mockups.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockupPromptGenerationService {

    private static final String TEXT_TO_IMAGE_PROMPT_TEMPLATE = "A high-quality, modern user interface mockup in a consistent style - general theme: %s.\n" +
            "The design should include buttons, icons, sliders, player controls, chat bubbles, progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
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
                    "The design should include buttons, icons, sliders, player controls, chat bubbles, progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
                    "Each component should share the same visual language — soft shadows, rounded corners, consistent color palette, and even spacing.\n" +
                    "The layout should show distinct UI elements without visible grid lines or borders.\n" +
                    "\n" +
                    "The mockup should look like a professional design system overview or UI kit preview, not a wireframe.\n" +
                    "All text (if any) should be minimal and used only as placeholder content.\n" +
                    "\n" +
                    "Emphasize clarity, readability, and design consistency.\n" +
                    "Use subtle lighting and depth to separate components naturally.\n" +
                    "No branding, no logos, no real-world content — only abstract UI shapes and elements.";

    private static final String PRIMARY_UI_ELEMENTS_PROMPT_TEMPLATE =
            "Create a 2x2 arrangement of clean, modern UI elements in a consistent style with no labels or text. \n" +
                    "Each element should appear noticeably wider than tall — around a 6:1 to 8:1 width-to-height ratio — to look sleek and realistic. \n" +
                    "Each element should be contained within its own equal-sized rectangular area, with equal spacing between them. \n" +
                    "Background should be white or transparent. \n" +
                    "\n" +
                    "Visual theme: %s, no borders or separators between elements, no grid lines, no labels, no captions. \n" +
                    "Each element should have appropriate padding within its area and be clearly distinguishable from the others. \n" +
                    "\n" +
                    "Elements to include (arranged left to right, top to bottom): \n" +
                    "1. rectangle_button \n" +
                    "2. rectangle_button_pressed \n" +
                    "3. text_field_empty \n" +
                    "4. text_field_focused \n" +
                    "\n" +
                    "Style guidelines: high-detail, professional, modern UI design using a cohesive color palette. \n" +
                    "Ensure elements are horizontally elongated, slim, and visually balanced — not tall or bulky.";

    private static final String PRIMARY_UI_ELEMENTS_REFERENCE_PROMPT =
            "Create a 2x2 arrangement of clean, modern UI elements in a consistent style with no labels or text. \n" +
                    "Each element should appear noticeably wider than tall — around a 6:1 to 8:1 width-to-height ratio — to look sleek and realistic. \n" +
                    "Each element should be contained within its own equal-sized rectangular area, with equal spacing between them. \n" +
                    "Background should be white or transparent. \n" +
                    "\n" +
                    "Visual theme: match the reference image style precisely (palette, materials, bevels, highlight logic), no borders or separators between elements, no grid lines, no labels, no captions. \n" +
                    "Each element should have appropriate padding within its area and be clearly distinguishable from the others. \n" +
                    "\n" +
                    "Elements to include (arranged left to right, top to bottom): \n" +
                    "1. rectangle_button \n" +
                    "2. rectangle_button_pressed \n" +
                    "3. text_field_empty \n" +
                    "4. text_field_focused \n" +
                    "\n" +
                    "Style guidelines: high-detail, professional, modern UI design using a cohesive color palette. \n" +
                    "Ensure elements are horizontally elongated, slim, and visually balanced — not tall or bulky.";

    private static final String SECONDARY_UI_ELEMENTS_PROMPT_HEADER =
            "Generate a single image containing exactly 6 UI element icons.\n" +
            "\n" +
            "Reference: match style to the reference image I supply (this is mandatory — copy palette / bevel / highlight logic).\n";

    private static final String SECONDARY_UI_ELEMENTS_PROMPT_LAYOUT =
            "\nLayout:\n" +
            "Arrange the 6 items into a horizontal row (or 3x3 neat grid if spatially helpful). Consistent baseline alignment, consistent visual padding, natural widths allowed, no labels. Ensure every element fits fully inside the canvas with even outer margins—no cropping or cut-off edges.\n" +
            "\n" +
            "Render these 6 items in this exact left-to-right order:\n" +
            "\n" +
            "9 progress_bar_empty\n" +
            "10 progress_bar_fill\n" +
            "11 slider_track_empty\n" +
            "12 slider_track_fill\n" +
            "13 slider_knob\n" +
            "14 vertical_scrollbar_thumb\n" +
            "\n" +
            "Flat orthographic front view. High clarity vector-like edges.";

    public static final String SECOND_GENERATION_VARIATION =
            " with professional color palette refined design aesthetics and enhanced visual appeal";

    private static final String DEFAULT_THEME = "modern UI kit";

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

    public String generatePrimaryUiElementsPrompt(String themeText) {
        String resolvedTheme = (themeText == null || themeText.trim().isEmpty())
                ? DEFAULT_THEME
                : themeText.trim();
        String prompt = String.format(PRIMARY_UI_ELEMENTS_PROMPT_TEMPLATE, resolvedTheme);
        log.debug("Generated primary UI elements prompt: {}", prompt);
        return prompt;
    }

    public String generateSecondaryUiElementsPrompt(String themeText) {
        StringBuilder prompt = new StringBuilder(SECONDARY_UI_ELEMENTS_PROMPT_HEADER);
        if (themeText != null && !themeText.trim().isEmpty()) {
            prompt.append("\nGeneral visual theme: ").append(themeText.trim()).append("\n");
        }
        prompt.append(SECONDARY_UI_ELEMENTS_PROMPT_LAYOUT);
        String finalPrompt = prompt.toString();
        log.debug("Generated secondary UI elements prompt: {}", finalPrompt);
        return finalPrompt;
    }

    public String generatePrimaryUiElementsPromptForReference() {
        log.debug("Generated primary UI elements reference prompt");
        return PRIMARY_UI_ELEMENTS_REFERENCE_PROMPT;
    }
}
