package com.gosu.icon_pack_generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PromptGenerationService {
    
    private static final String BASE_PROMPT_TEMPLATE = 
            "Create a 3x3 grid of minimalist, clean icons in a consistent style. " +
            "Each icon should be contained within its own square cell of equal size. " +
            "The background should be white or transparent. " +
            "Icons should be simple, modern, and use a consistent color scheme. " +
            "General theme: %s. ";
    
    private static final String SPECIFIC_ICONS_TEMPLATE = 
            "Specific icons to include in the grid (arranged left to right, top to bottom): %s. ";
    
    private static final String FALLBACK_ICONS = 
            "Include 9 different icons that represent various aspects of the general theme. ";
    
    private static final String STYLE_GUIDELINES = 
            "Style guidelines: Icons should be scalable vector-style graphics, " +
            "with clear shapes and minimal detail. Use consistent line thickness " +
            "and a professional color palette. Ensure each icon is clearly distinguishable " +
            "and fits well within its grid cell with appropriate padding.";
    
    public String generatePromptFor3x3Grid(String generalDescription, List<String> individualDescriptions) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add base prompt with general description
        promptBuilder.append(String.format(BASE_PROMPT_TEMPLATE, generalDescription));
        
        // Add specific icon descriptions if provided
        if (individualDescriptions != null && !individualDescriptions.isEmpty()) {
            String specificIcons = String.join(", ", individualDescriptions);
            promptBuilder.append(String.format(SPECIFIC_ICONS_TEMPLATE, specificIcons));
        } else {
            promptBuilder.append(FALLBACK_ICONS);
        }
        
        // Add style guidelines
        promptBuilder.append(STYLE_GUIDELINES);
        
        String finalPrompt = promptBuilder.toString();
        log.info("Generated prompt for 3x3 grid: {}", finalPrompt.substring(0, Math.min(200, finalPrompt.length())));
        
        return finalPrompt;
    }
}
