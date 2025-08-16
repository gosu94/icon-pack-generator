package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PromptGenerationService {
    
    private static final String BASE_PROMPT_TEMPLATE = 
            "Create a 3x3 arrangement of minimalist, clean icons in a consistent style. " +
            "Each icon should be contained within its own square area of equal size. " +
            "The background should be white or transparent. " +
            "Icons should be simple, modern, and use a consistent color scheme. " +
            "IMPORTANT: Do NOT add visible grid lines, borders, or separators between icons. " +
            "Do NOT add any text, labels, numbers, or captions below or around the icons. " +
            "General theme: %s. ";
    
    private static final String SPECIFIC_ICONS_TEMPLATE = 
            "Specific icons to include in the arrangement (arranged left to right, top to bottom): %s. ";
    
    private static final String FALLBACK_ICONS = 
            "Include 9 different icons that represent various aspects of the general theme. " +
            "Each icon should be distinct and clearly separated from others. ";
    
    private static final String STYLE_GUIDELINES = 
            "Style guidelines: Icons should be scalable vector-style graphics, " +
            "with clear shapes and minimal detail. Use consistent line thickness " +
            "and a professional color palette. Ensure each icon is clearly distinguishable " +
            "and fits well within its area with appropriate padding. " +
            "NO grid lines, NO text labels, NO captions - just clean icons arranged in a 3x3 layout.";
    
    public String generatePromptFor3x3Grid(String generalDescription, List<String> iconDescriptions) {
        if (iconDescriptions != null && iconDescriptions.size() != 9) {
            throw new IllegalArgumentException("Must provide exactly 9 icon descriptions for a 3x3 grid");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format(BASE_PROMPT_TEMPLATE, generalDescription));
        
        // Add specific icon descriptions if provided
        if (iconDescriptions != null && !iconDescriptions.isEmpty()) {
            String specificIcons = String.join(", ", iconDescriptions);
            prompt.append(String.format(SPECIFIC_ICONS_TEMPLATE, specificIcons));
        } else {
            prompt.append(FALLBACK_ICONS);
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
}