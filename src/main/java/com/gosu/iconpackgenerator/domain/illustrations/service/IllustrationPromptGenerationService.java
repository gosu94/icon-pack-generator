package com.gosu.iconpackgenerator.domain.illustrations.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IllustrationPromptGenerationService {

    private static final String TEXT_TO_IMAGE_PROMPT_TEMPLATE = "Create a 2x2 arrangement of illustrations (with NO captions and NO grid lines) in a consistent style. \n" +
            "Each illustration should be contained within its own square area of equal size, with equal spacing between them. \n" +
            "Use a fully white division between the illustrations so they can be easily cropped out later.\n" +
            "\n" +
            "General theme: %s \n" +
            "%s" +
            "\n" +
            "Style guidelines:  \n" +
            "- All illustrations should share the same style, color palette, and overall visual feel.  \n" +
            "- No text, labels, numbers, or captions anywhere in the image.  \n" +
            "- Only color outside illustrations should be white \n" +
            "- Illustrations should have backgrounds distinguishable from white image background \n" +
            "- Ensure each illustration fits well within its square area with appropriate padding.  \n" +
            "- The divisions should be clear enough to separate the illustrations, but not styled (no borders, no decorative lines — just white separation).  \n" +
            "\n" +
            "Final output: A clean 2x2 layout of 4 illustrations, ready for cropping. \n";

    private static final String IMAGE_TO_IMAGE_PROMPT_TEMPLATE =
            "Using the provided reference image as a style guide, create a new 2x2 arrangement of illustrations (with NO captions and NO grid lines). \n" +
                    "The new image should match the same overall style, color palette, shading, and general theme as the reference. \n" +
                    "Maintain the same 2x2 layout: four square areas of equal size, arranged left to right, top to bottom, with equal white spacing.  \n" +
                    "\n" +
                    "General theme: %s \n" +
                    "%s" +
                    "\n" +
                    "Style guidelines:  \n" +
                    "- Replicate the visual style of the reference image as closely as possible (line weight, shading, depth, and highlights).  \n" +
                    "- Keep the same consistent color palette and professional look.  \n" +
                    "- No text, labels, numbers, or captions.  \n" +
                    "- Only color outside illustrations should be white \n" +
                    "- Illustrations should have backgrounds distinguishable from white image background \n" +
                    "- The divisions should be clear enough to separate the illustrations, but not styled (no borders, no decorative lines — just white separation).  \n" +
                    "- Ensure each illustration is clearly distinguishable and fits cleanly within its square area with appropriate padding.  \n" +
                    "\n" +
                    "Final output: A new 2x2 layout of 4 illustrations that look like part of the same series as the reference image. ";

    public static final String SECOND_GENERATION_VARIATION =
            ". Use professional color palette and artistic design";

    /**
     * Template for generating the first individual illustration (text-to-image)
     */
    private static final String INDIVIDUAL_FIRST_ILLUSTRATION_TEMPLATE =
            "Create a single high-quality illustration based on the following:\n" +
                    "General theme: %s\n" +
                    "%s" +
                    "\n" +
                    "Style guidelines:\n" +
                    "- Create a professional, cohesive illustration that fits the theme\n" +
                    "- Use a consistent style and professional color palette\n" +
                    "- No text, labels, numbers, or captions anywhere in the image\n" +
                    "- Ensure the illustration has appropriate padding and composition\n" +
                    "- Illustration should be rectangle shape unless stated otherwise in general theme\n" +
                    "- The illustration should be clear, distinct, and visually appealing\n" +
                    "\n" +
                    "Final output: A single, high-quality illustration ready to use.";

    /**
     * Template for generating subsequent illustrations matching the style of the first (image-to-image)
     */
    private static final String INDIVIDUAL_SUBSEQUENT_ILLUSTRATION_TEMPLATE =
            "Using the provided reference image as a style guide, create a new illustration that matches its style.\n" +
                    "The new illustration should have the same visual style, color palette, shading, line weight, and professional look.\n" +
                    "\n" +
                    "General theme: %s\n" +
                    "%s" +
                    "\n" +
                    "Style guidelines:\n" +
                    "- Match the exact visual style of the reference illustration (color palette, shading, depth, highlights)\n" +
                    "- Keep the same professional look and artistic approach\n" +
                    "- No text, labels, numbers, or captions\n" +
                    "- Ensure the illustration has the same composition quality as the reference\n" +
                    "- Illustration should be rectangle shape unless stated otherwise in general theme\n" +
                    "- The new illustration should look like it's from the same series as the reference\n" +
                    "\n" +
                    "Final output: A new illustration that seamlessly fits with the reference style.";

    /**
     * Generate prompt for 2x2 grid of illustrations based on text description
     */
    public String generatePromptFor2x2Grid(String generalDescription, List<String> individualDescriptions) {
        StringBuilder individualPrompts = new StringBuilder();

        if (individualDescriptions != null && !individualDescriptions.isEmpty()) {
            List<String> nonEmptyDescriptions = individualDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .collect(Collectors.toList());

            if (!nonEmptyDescriptions.isEmpty()) {
                individualPrompts.append("Specific illustrations to include (arranged left to right, top to bottom): ");
                for (int i = 0; i < nonEmptyDescriptions.size() && i < 4; i++) {
                    individualPrompts.append((i + 1))
                            .append(". ")
                            .append(nonEmptyDescriptions.get(i))
                            .append("; ");
                }

                // If less than 4 descriptions provided, instruct to fill remaining with theme-matching content
                int descriptionsProvided = nonEmptyDescriptions.size();
                if (descriptionsProvided < 4) {
                    individualPrompts.append("\nFor positions ")
                            .append(descriptionsProvided + 1)
                            .append(" through 4, create additional illustrations that match the theme '")
                            .append(generalDescription)
                            .append("' and complement the specified illustrations above. ");
                }
            }
        }

        // If no individual descriptions, emphasize creating 4 distinct illustrations
        if (individualPrompts.length() == 0) {
            individualPrompts.append("Create 4 distinct but cohesive illustrations, all matching the theme. ");
        }

        String prompt = String.format(TEXT_TO_IMAGE_PROMPT_TEMPLATE,
                generalDescription,
                individualPrompts.toString());

        log.debug("Generated 2x2 grid prompt: {}", prompt);
        return prompt;
    }

    /**
     * Generate prompt for reference image-based illustration generation
     */
    public String generatePromptForReferenceImage(List<String> individualDescriptions, String generalDescription) {
        StringBuilder individualPrompts = new StringBuilder();

        if (individualDescriptions != null && !individualDescriptions.isEmpty()) {
            List<String> nonEmptyDescriptions = individualDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .collect(Collectors.toList());

            if (!nonEmptyDescriptions.isEmpty()) {
                individualPrompts.append("Specific new illustrations to include (arranged left to right, top to bottom): ");
                for (int i = 0; i < nonEmptyDescriptions.size() && i < 4; i++) {
                    individualPrompts.append((i + 1))
                            .append(". ")
                            .append(nonEmptyDescriptions.get(i))
                            .append("; ");
                }

                // If less than 4 descriptions provided, instruct to fill remaining with theme-matching content
                int descriptionsProvided = nonEmptyDescriptions.size();
                if (descriptionsProvided < 4) {
                    String theme = (generalDescription != null && !generalDescription.trim().isEmpty())
                            ? generalDescription
                            : "the reference style";
                    individualPrompts.append("\nFor positions ")
                            .append(descriptionsProvided + 1)
                            .append(" through 4, create additional illustrations that match ")
                            .append(theme)
                            .append(" and complement the specified illustrations above. ");
                }
            }
        }

        // If no individual descriptions, emphasize creating 4 distinct illustrations
        if (individualPrompts.length() == 0) {
            individualPrompts.append("Create 4 distinct but cohesive illustrations in the same style as the reference. ");
        }

        String theme = (generalDescription != null && !generalDescription.trim().isEmpty())
                ? generalDescription
                : "matching the reference style";

        String prompt = String.format(IMAGE_TO_IMAGE_PROMPT_TEMPLATE,
                theme,
                individualPrompts.toString());

        log.debug("Generated reference image prompt: {}", prompt);
        return prompt;
    }

    /**
     * Generate prompt for the first individual illustration (text-to-image)
     */
    public String generatePromptForFirstIndividualIllustration(String generalDescription, String specificDescription) {
        String specificPart = "";
        if (specificDescription != null && !specificDescription.trim().isEmpty()) {
            specificPart = "Specific subject: " + specificDescription;
        }

        String prompt = String.format(INDIVIDUAL_FIRST_ILLUSTRATION_TEMPLATE,
                generalDescription,
                specificPart);

        log.debug("Generated first individual illustration prompt: {}", prompt);
        return prompt;
    }

    /**
     * Generate prompt for subsequent individual illustrations (image-to-image)
     */
    public String generatePromptForSubsequentIndividualIllustration(String generalDescription, String specificDescription) {
        String specificPart = "";
        if (specificDescription != null && !specificDescription.trim().isEmpty()) {
            specificPart = "Specific subject: " + specificDescription;
        } else {
            specificPart = "Create a new illustration that fits the theme and complements the reference style.";
        }

        String prompt = String.format(INDIVIDUAL_SUBSEQUENT_ILLUSTRATION_TEMPLATE,
                generalDescription,
                specificPart);

        log.debug("Generated subsequent individual illustration prompt: {}", prompt);
        return prompt;
    }
}

