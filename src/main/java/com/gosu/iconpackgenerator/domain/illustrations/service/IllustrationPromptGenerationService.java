package com.gosu.iconpackgenerator.domain.illustrations.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IllustrationPromptGenerationService {
    
    private static final String TEXT_TO_IMAGE_PROMPT_TEMPLATE = "A high-quality, modern user interface mockup in a consistent style - general theme: %s.\n" +
            "The design should include buttons, icons, sliders, player controls, chat bubbles (with sample messages), progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
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
                "The design should include buttons, icons, sliders, player controls ,chat bubbles (with sample messages), progress bars, toggles, search bars and rating cards, arranged in a clean, balanced grid layout on a neutral background.\n" +
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
        " use professional color palette and artistic design";
    
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
}

