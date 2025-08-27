package com.gosu.iconpackgenerator.service

import com.gosu.iconpackgenerator.domain.service.PromptGenerationService
import spock.lang.Specification

class PromptGenerationServiceSpec extends Specification {

    PromptGenerationService promptGenerationService = new PromptGenerationService()

    def "should generate prompt with partial icon specifications"() {
        given: "A theme and partial icon descriptions"
        String theme = "business productivity"
        List<String> partialDescriptions = ["calendar", "email", "", null, "chart"]

        when: "Generating prompt for 3x3 grid"
        String prompt = promptGenerationService.generatePromptFor3x3Grid(theme, partialDescriptions)

        then: "Prompt should ask model to fill missing icons intelligently"
        prompt.contains("Include these specific icons: calendar, email, chart")
        prompt.contains("For the remaining 6 positions, intelligently choose icons")
        prompt.contains("business productivity")
        !prompt.contains("openai") // No OpenAI references
    }

    def "should generate prompt with duplicate avoidance for second grid"() {
        given: "A theme and icons to avoid"
        String theme = "technology"
        List<String> secondGridDescriptions = ["smartphone", "laptop"]
        List<String> iconsToAvoid = ["computer", "wifi", "cloud"]

        when: "Generating prompt for second grid with avoidance"
        String prompt = promptGenerationService.generatePromptFor3x3Grid(theme, secondGridDescriptions, iconsToAvoid)

        then: "Prompt should include avoidance instructions"
        prompt.contains("Do NOT include any icons similar to these already created ones: computer, wifi, cloud")
        prompt.contains("Create completely different and unique icons")
        prompt.contains("smartphone, laptop")
    }

    def "should handle empty descriptions gracefully"() {
        given: "A theme with no specific descriptions"
        String theme = "social media"
        List<String> emptyDescriptions = ["", null, "   "]

        when: "Generating prompt"
        String prompt = promptGenerationService.generatePromptFor3x3Grid(theme, emptyDescriptions)

        then: "Should use fallback logic"
        prompt.contains("Include 9 different icons that represent various aspects")
        prompt.contains("social media")
    }
}
