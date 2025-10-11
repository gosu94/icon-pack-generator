package com.gosu.iconpackgenerator.domain.mockups.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockupPromptGenerationService {
    
    private static final String TEXT_TO_IMAGE_PROMPT_TEMPLATE = 
        "Create a professional UI/UX mockup design with 16:9 aspect ratio. \n" +
        "\n" +
        "Description: %s \n" +
        "\n" +
        "Design guidelines:  \n" +
        "- Modern, clean, and professional user interface design  \n" +
        "- Appropriate use of whitespace and visual hierarchy  \n" +
        "- Consistent color scheme and typography  \n" +
        "- High-quality, production-ready UI mockup  \n" +
        "- 16:9 aspect ratio (widescreen format)  \n" +
        "- No placeholder text like 'Lorem Ipsum' - use realistic content  \n" +
        "- Professional quality suitable for presentation or development handoff  \n" +
        "\n" +
        "Final output: A polished, high-fidelity UI mockup ready for professional use.";

    private static final String IMAGE_TO_IMAGE_PROMPT_TEMPLATE = 
        "Using the provided reference image as a style guide, create a new UI/UX mockup design with 16:9 aspect ratio. \n" +
        "The new design should maintain the same visual style, color palette, layout principles, and design language as the reference. \n" +
        "\n" +
        "Description: %s \n" +
        "\n" +
        "Design guidelines:  \n" +
        "- Match the design style and visual language of the reference image  \n" +
        "- Maintain consistent color scheme, typography, and component styling  \n" +
        "- Follow the same layout principles and spacing guidelines  \n" +
        "- Keep the same level of detail and polish as the reference  \n" +
        "- 16:9 aspect ratio (widescreen format)  \n" +
        "- No placeholder text like 'Lorem Ipsum' - use realistic content  \n" +
        "- Professional quality suitable for presentation or development handoff  \n" +
        "\n" +
        "Final output: A polished UI mockup that looks like it's part of the same design system as the reference.";
    
    public static final String SECOND_GENERATION_VARIATION = 
        " with refined design aesthetics and enhanced visual appeal";
    
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
}

