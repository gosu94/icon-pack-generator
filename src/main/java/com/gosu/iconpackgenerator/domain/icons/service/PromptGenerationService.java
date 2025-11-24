package com.gosu.iconpackgenerator.domain.icons.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PromptGenerationService {

    private static final String BASE_PROMPT_TEMPLATE =
            "Create a 3x3 arrangement of clean icons in a consistent style with no labels. " +
                    "Each icon should be contained within its own square area of equal size and there should be equal spaces between icons. " +
                    "If icons have backgrounds corners should be rounded, and backkgrounds should be the same for each icon " +
                    "otherwise background should be white or transparent. " +
                    "Icons should use a consistent color scheme. " +
                    "IMPORTANT: Do NOT add visible grid lines, borders, or separators between icons. " +
                    "Do NOT add any text, labels, numbers, or captions below or around the icons. " +
                    "General theme: %s. ";

    private static final String SPECIFIC_ICONS_TEMPLATE =
            "Specific icons to include in the arrangement (arranged left to right, top to bottom): %s. ";

    private static final String PARTIAL_ICONS_TEMPLATE =
            "Include these specific icons: %s. " +
                    "For the remaining %d positions, intelligently choose icons that would logically fit with the general theme '%s' " +
                    "and complement the specified icons. Each icon should be distinct and clearly separated from others. ";

    private static final String FALLBACK_ICONS =
            "Include 9 different icons that represent various aspects of the general theme. " +
                    "Each icon should be distinct and clearly separated from others. ";

    private static final String AVOID_DUPLICATES_TEMPLATE =
            "IMPORTANT: Do NOT include any icons similar to these already created ones: %s. " +
                    "Create completely different and unique icons that still fit the general theme. ";

    private static final String STYLE_GUIDELINES =
            "Style guidelines: " + //and a professional color palette.
                    "Icons should use high detailed modern style with professional color palette. " +
                    "Ensure each icon is clearly distinguishable " +
                    "and fits well within its area with appropriate padding. " +
                    "NO grid lines, NO text labels, NO captions - just clean icons arranged in a 3x3 layout.";

    public static final String SECOND_GENERATION_VARIATION =
            ". Icons should have subtle shading, depth, and highlights to give a slightly dimensional look. ";

    public String generatePromptFor3x3Grid(String generalDescription, List<String> iconDescriptions) {
        return generatePromptFor3x3Grid(generalDescription, iconDescriptions, null);
    }

    public String generatePromptFor3x3Grid(String generalDescription, List<String> iconDescriptions, List<String> iconsToAvoid) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format(BASE_PROMPT_TEMPLATE, generalDescription));

        // Handle icon descriptions
        if (iconDescriptions != null && !iconDescriptions.isEmpty()) {
            // Filter out empty/null descriptions
            List<String> validDescriptions = iconDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .toList();

            if (validDescriptions.size() == 9) {
                // All 9 icons specified
                String specificIcons = String.join(", ", validDescriptions);
                prompt.append(String.format(SPECIFIC_ICONS_TEMPLATE, specificIcons));
            } else if (!validDescriptions.isEmpty()) {
                // Partial specification - ask model to fill in the gaps intelligently
                String specifiedIcons = String.join(", ", validDescriptions);
                int missingCount = 9 - validDescriptions.size();
                prompt.append(String.format(PARTIAL_ICONS_TEMPLATE, specifiedIcons, missingCount, generalDescription));
            } else {
                // No valid descriptions - use fallback
                prompt.append(FALLBACK_ICONS);
            }
        } else {
            prompt.append(FALLBACK_ICONS);
        }

        // Add duplicate avoidance instruction if this is for a second grid
        if (iconsToAvoid != null && !iconsToAvoid.isEmpty()) {
            List<String> validIconsToAvoid = iconsToAvoid.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .toList();
            if (!validIconsToAvoid.isEmpty()) {
                String avoidList = String.join(", ", validIconsToAvoid);
                prompt.append(String.format(AVOID_DUPLICATES_TEMPLATE, avoidList));
            }
        }

        // Add style guidelines
        prompt.append(STYLE_GUIDELINES);

        String finalPrompt = prompt.toString();
        log.info("Generated 3x3 grid prompt: {}", finalPrompt.substring(0, Math.min(150, finalPrompt.length())));

        return finalPrompt;
    }

    public String generatePromptForSingleIcon(String description, String generalTheme) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a single high-quality icon. ");
        prompt.append("Theme: ").append(generalTheme).append(". ");
        prompt.append("Description: ").append(description).append(". ");
        prompt.append("The icon should be clean, professional, and easily recognizable. ");
        prompt.append("No text, labels, or captions should be included - just the pure icon itself.");

        return prompt.toString();
    }

    /**
     * Generate a prompt specifically for missing icons using image-to-image generation
     * This prompt emphasizes style consistency with the reference image
     */
    public String generatePromptForMissingIcons(String generalDescription, List<String> missingIconDescriptions) {
        StringBuilder prompt = new StringBuilder();

        // Start with the instruction to match the existing style
        prompt.append("Generate 3x3 icon grid in the exact same style, design, and color scheme as shown in this reference image. ");
        prompt.append("Maintain the same visual consistency, line thickness, color palette, and overall aesthetic. ");

        // Add the general theme context
//        prompt.append("General theme: ").append(generalDescription).append(". ");

        // Specify the missing icons to generate
        if (missingIconDescriptions != null && !missingIconDescriptions.isEmpty()) {
            // Filter out empty descriptions
            List<String> validDescriptions = missingIconDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .toList();

            if (!validDescriptions.isEmpty()) {
                String iconsToGenerate = String.join(", ", validDescriptions);
                prompt.append("But use these specific icons instead: ").append(iconsToGenerate).append(". ");

                // If we don't have 9 icons, fill the rest
                if (validDescriptions.size() < 9) {
                    int missingCount = 9 - validDescriptions.size();
                    prompt.append("For the remaining ").append(missingCount).append(" positions, ");
                    prompt.append("create icons that complement the specified ones and fit the general theme. ");
                }
            }
        }

        // Emphasize style matching and layout requirements
        prompt.append("CRITICAL: Match the exact style, color scheme, and design approach from the reference image. ");
        prompt.append("Arrange the icons in a clean 3x3 grid layout with consistent spacing. ");
        prompt.append("Each icon should be contained within its own square area of equal size. ");
        prompt.append("The background should match the reference image background. ");
        prompt.append("IMPORTANT: Do NOT add visible grid lines, borders, or separators between icons. ");
        prompt.append("Do NOT add any text, labels, numbers, or captions. ");
        prompt.append("Ensure the new icons blend seamlessly with the style shown in the reference image.");

        String finalPrompt = prompt.toString();
        log.info("Generated missing icons prompt: {}", finalPrompt.substring(0, Math.min(150, finalPrompt.length())));

        return finalPrompt;
    }

    /**
     * Generate a prompt for creating icons based on a reference image style
     * This will be used for image-to-image generation where the reference image provides the style
     */
    public String generatePromptForReferenceImage(List<String> iconDescriptions, String originalPrompt) {
        return generatePromptForReferenceImage(iconDescriptions, originalPrompt, null);
    }

    /**
     * Generate a prompt for creating icons based on a reference image style with avoidance list
     * This will be used for image-to-image generation where the reference image provides the style
     */
    public String generatePromptForReferenceImage(List<String> iconDescriptions, String originalPrompt, List<String> iconsToAvoid) {
        StringBuilder prompt = new StringBuilder();

        // Start with the instruction to use the reference image as style guide
        prompt.append("Create a 3x3 arrangement of clean icons using the exact same style, design approach, and color scheme as shown in this reference image. ");
        prompt.append("Maintain the same visual consistency, line thickness, color palette, and overall aesthetic. ");
        if (originalPrompt != null && !originalPrompt.trim().isEmpty()) {
            prompt.append("General theme: ").append(originalPrompt).append(".");
        }
        prompt.append("Each icon should be contained within its own square area of equal size. ");
        prompt.append("Image background should be transparent. ");

        // Handle icon descriptions
        if (iconDescriptions != null && !iconDescriptions.isEmpty()) {
            // Filter out empty/null descriptions
            List<String> validDescriptions = iconDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .toList();

            if (validDescriptions.size() == 9) {
                // All 9 icons specified
                String specificIcons = String.join(", ", validDescriptions);
                prompt.append("Create these specific icons in the reference style (arranged left to right, top to bottom): ").append(specificIcons).append(". ");
            } else if (!validDescriptions.isEmpty()) {
                // Partial specification
                String specifiedIcons = String.join(", ", validDescriptions);
                int missingCount = 9 - validDescriptions.size();
                prompt.append("Include these specific icons: ").append(specifiedIcons).append(". ");
                prompt.append("For the remaining ").append(missingCount).append(" positions, ");
                prompt.append("create complementary icons that match the style and would logically fit together. ");
            } else {
                // No valid descriptions - create icons that match the reference style
                prompt.append("Create 9 different icons that match the style and theme suggested by the reference image. ");
                prompt.append("Each icon should be distinct and clearly separated from others. ");
            }
        } else {
            prompt.append("Create 9 different icons that match the style and theme suggested by the reference image. ");
            prompt.append("Each icon should be distinct and clearly separated from others. ");
        }

        // Add duplicate avoidance instruction if this is for a second grid (consistency with text-based approach)
        if (iconsToAvoid != null && !iconsToAvoid.isEmpty()) {
            List<String> validIconsToAvoid = iconsToAvoid.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .toList();
            if (!validIconsToAvoid.isEmpty()) {
                String avoidList = String.join(", ", validIconsToAvoid);
                prompt.append(String.format(AVOID_DUPLICATES_TEMPLATE, avoidList));
            }
        }

        // Add style matching requirements
        prompt.append("CRITICAL: Match the exact style, color scheme, and design approach from the reference image. ");
        prompt.append("Arrange the icons in a clean 3x3 grid layout with consistent spacing. ");
        prompt.append("IMPORTANT: Do NOT add visible grid lines, borders, or separators between icons. ");
        prompt.append("Do NOT add any text, labels, numbers, or captions. ");
        prompt.append("Ensure all new icons blend seamlessly with the style shown in the reference image.");

        String finalPrompt = prompt.toString();
        log.info("Generated reference image-based prompt: {}", finalPrompt.substring(0, Math.min(150, finalPrompt.length())));

        return finalPrompt;
    }
}