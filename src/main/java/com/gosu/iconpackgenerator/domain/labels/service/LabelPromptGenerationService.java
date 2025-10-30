package com.gosu.iconpackgenerator.domain.labels.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LabelPromptGenerationService {

    private static final String TEXT_TO_IMAGE_TEMPLATE = """
            Create a high-quality image of the text '%s' .
            Style the text according to a %s.
            Match the theme’s typography, color palette, texture, lighting, and mood.
            Keep the text centered and fully on one line (no line breaks or stacked words).
            Use a transparent background, and ensure sharp, high-resolution edges suitable for a logo or label.
            The composition should feel cohesive, polished, and visually consistent with the described theme.
            """;

    private static final String IMAGE_TO_IMAGE_TEMPLATE = """
            Create a high-quality image of the text '%s', using the exact same visual style as the attached reference image.
            Match the font shape, texture, color palette, lighting, and material properties precisely.
            Keep the text centered, fully on one line, and do not stack or break words.
            Maintain the same artistic mood and rendering style as the reference.
            Use a transparent background and ensure clean, high-resolution edges suitable for a logo or label.
            """;

    private static final String VARIATION_SUFFIX = "Additionally, the label should have subtle shading, depth, and highlights to give a slightly dimensional look.";

    public String buildTextToImagePrompt(String labelText, String generalTheme, boolean variation) {
        String theme = generalTheme != null ? generalTheme.trim() : "";
        String prompt = String.format(TEXT_TO_IMAGE_TEMPLATE, labelText.trim(), theme);
        if (variation) {
            prompt = prompt + " " + VARIATION_SUFFIX;
        }
        log.debug("Generated text-to-image label prompt (variation={}): {}", variation, truncate(prompt));
        return prompt;
    }

    public String buildImageToImagePrompt(String labelText, boolean variation) {
        String prompt = String.format(IMAGE_TO_IMAGE_TEMPLATE, labelText.trim());
        if (variation) {
            prompt = prompt + " " + VARIATION_SUFFIX;
        }
        log.debug("Generated image-to-image label prompt (variation={}): {}", variation, truncate(prompt));
        return prompt;
    }

    private String truncate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}

