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
            "Icons should be simple, and use a consistent color scheme. " +
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
            "Style guidelines: Icons should be scalable vector-style graphics, " +
            "with clear shapes and minimal detail. Use consistent line thickness. " + //and a professional color palette.
            "Ensure each icon is clearly distinguishable " +
            "and fits well within its area with appropriate padding. " +
            "NO grid lines, NO text labels, NO captions - just clean icons arranged in a 3x3 layout.";
    
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
}