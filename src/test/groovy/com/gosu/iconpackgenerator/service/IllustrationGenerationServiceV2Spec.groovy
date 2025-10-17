package com.gosu.iconpackgenerator.service

import com.gosu.iconpackgenerator.domain.ai.AnyLlmModelService
import com.gosu.iconpackgenerator.domain.ai.BananaModelService
import com.gosu.iconpackgenerator.domain.icons.service.CoinManagementService
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationRequest
import com.gosu.iconpackgenerator.domain.illustrations.dto.IllustrationGenerationResponse
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationDescriptionGenerationService
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationGenerationServiceV2
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationPersistenceService
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationPromptGenerationService
import com.gosu.iconpackgenerator.domain.illustrations.service.IllustrationTrialModeService
import com.gosu.iconpackgenerator.user.model.User
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class IllustrationGenerationServiceV2Spec extends Specification {

    BananaModelService bananaModelService
    IllustrationPromptGenerationService illustrationPromptGenerationService
    IllustrationDescriptionGenerationService illustrationDescriptionGenerationService
    CoinManagementService coinManagementService
    IllustrationPersistenceService illustrationPersistenceService
    IllustrationTrialModeService illustrationTrialModeService
    IllustrationGenerationServiceV2 service
    
    def setup() {
        bananaModelService = Mock(BananaModelService)
        illustrationPromptGenerationService = Mock(IllustrationPromptGenerationService)
        illustrationDescriptionGenerationService = Mock(IllustrationDescriptionGenerationService)
        coinManagementService = Mock(CoinManagementService)
        illustrationPersistenceService = Mock(IllustrationPersistenceService)
        illustrationTrialModeService = Mock(IllustrationTrialModeService)
        
        service = new IllustrationGenerationServiceV2(
            bananaModelService,
            illustrationPromptGenerationService,
            illustrationDescriptionGenerationService,
            coinManagementService,
            illustrationPersistenceService,
            illustrationTrialModeService
        )
    }
    
    def "should generate 4 illustrations using V2 approach - first text-to-image, then 3 parallel image-to-image"() {
        given: "a request with general theme and no individual descriptions"
        def request = new IllustrationGenerationRequest()
        request.generalDescription = "Travel destinations"
        request.illustrationCount = 4
        request.generationsPerService = 1
        request.individualDescriptions = []
        
        def user = new User()
        user.email = "test@example.com"
        user.coins = 10
        user.trialCoins = 5
        
        and: "LLM generates 4 descriptions"
        def generatedDescriptions = [
            "Eiffel Tower in Paris",
            "Tokyo skyline at sunset",
            "Beach in Bali",
            "Mountains in Switzerland"
        ]
        
        and: "mock responses for image generation"
        def firstImageData = "first-image-data".bytes
        def secondImageData = "second-image-data".bytes
        def thirdImageData = "third-image-data".bytes
        def fourthImageData = "fourth-image-data".bytes
        
        when: "generating illustrations"
        def future = service.generateIllustrations(request, user)
        def response = future.join()
        
        then: "coin deduction is verified"
        1 * coinManagementService.deductCoinsForGeneration(user, 1) >> 
            new CoinManagementService.CoinDeductionResult(true, false, 1, null)
        
        and: "descriptions are generated for missing individual descriptions"
        1 * illustrationDescriptionGenerationService.ensureFourDescriptions("Travel destinations", []) >> 
            CompletableFuture.completedFuture(generatedDescriptions)
        
        and: "prompts are generated"
        1 * illustrationPromptGenerationService.generatePromptForFirstIndividualIllustration(
            "Travel destinations", "Eiffel Tower in Paris") >> "First illustration prompt"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(
            "Travel destinations", "Tokyo skyline at sunset") >> "Second illustration prompt"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(
            "Travel destinations", "Beach in Bali") >> "Third illustration prompt"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(
            "Travel destinations", "Mountains in Switzerland") >> "Fourth illustration prompt"
        
        and: "first illustration is generated with text-to-image"
        1 * bananaModelService.generateImage("First illustration prompt", _, "4:3", false) >> 
            CompletableFuture.completedFuture(firstImageData)
        
        and: "remaining 3 illustrations are generated with image-to-image"
        1 * bananaModelService.generateImageToImage("Second illustration prompt", firstImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(secondImageData)
        1 * bananaModelService.generateImageToImage("Third illustration prompt", firstImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(thirdImageData)
        1 * bananaModelService.generateImageToImage("Fourth illustration prompt", firstImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(fourthImageData)
        
        and: "illustrations are persisted"
        1 * illustrationPersistenceService.persistGeneratedIllustrations(_, request, _, user)
        
        and: "response is successful with 4 illustrations"
        response.status == "success"
        response.illustrations.size() == 4
        response.illustrations[0].description == "Eiffel Tower in Paris"
        response.illustrations[1].description == "Tokyo skyline at sunset"
        response.illustrations[2].description == "Beach in Bali"
        response.illustrations[3].description == "Mountains in Switzerland"
    }
    
    def "should use existing descriptions when some are provided and generate missing ones"() {
        given: "a request with 2 provided descriptions"
        def request = new IllustrationGenerationRequest()
        request.generalDescription = "Nature"
        request.illustrationCount = 4
        request.generationsPerService = 1
        request.individualDescriptions = ["Forest landscape", "Ocean waves"]
        
        def user = new User()
        user.email = "test@example.com"
        user.coins = 10
        
        and: "LLM completes the descriptions"
        def completedDescriptions = [
            "Forest landscape",
            "Ocean waves",
            "Mountain peaks",
            "Desert dunes"
        ]
        
        and: "mock image data"
        def mockImageData = "mock-image".bytes
        
        when: "generating illustrations"
        def future = service.generateIllustrations(request, user)
        def response = future.join()
        
        then: "coin deduction succeeds"
        1 * coinManagementService.deductCoinsForGeneration(user, 1) >> 
            new CoinManagementService.CoinDeductionResult(true, false, 1, null)
        
        and: "existing descriptions are used and missing ones generated"
        1 * illustrationDescriptionGenerationService.ensureFourDescriptions("Nature", 
            ["Forest landscape", "Ocean waves"]) >> 
            CompletableFuture.completedFuture(completedDescriptions)
        
        and: "all images are generated"
        1 * illustrationPromptGenerationService.generatePromptForFirstIndividualIllustration(_, _) >> "Prompt 1"
        3 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(_, _) >> 
            ["Prompt 2", "Prompt 3", "Prompt 4"]
        
        1 * bananaModelService.generateImage(_, _, "4:3", false) >> CompletableFuture.completedFuture(mockImageData)
        3 * bananaModelService.generateImageToImage(_, mockImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(mockImageData)
        
        and: "persistence succeeds"
        1 * illustrationPersistenceService.persistGeneratedIllustrations(_, _, _, _)
        
        and: "all 4 illustrations are generated"
        response.illustrations.size() == 4
        response.illustrations[0].description == "Forest landscape"
        response.illustrations[1].description == "Ocean waves"
    }
    
    def "should handle coin deduction failure gracefully"() {
        given: "a request"
        def request = new IllustrationGenerationRequest()
        request.generalDescription = "Test theme"
        request.illustrationCount = 4
        request.generationsPerService = 1
        
        def user = new User()
        user.email = "test@example.com"
        user.coins = 0
        
        when: "generating illustrations with insufficient coins"
        def future = service.generateIllustrations(request, user)
        def response = future.join()
        
        then: "coin deduction fails"
        1 * coinManagementService.deductCoinsForGeneration(user, 1) >> 
            new CoinManagementService.CoinDeductionResult(false, false, 0, "Insufficient coins")
        
        and: "error response is returned"
        response.status == "error"
        response.message == "Insufficient coins"
        
        and: "no image generation occurs"
        0 * bananaModelService.generateImage(_, _, _, _)
        0 * bananaModelService.generateImageToImage(_, _, _, _, _)
    }
    
    def "should handle generation error gracefully"() {
        given: "a request"
        def request = new IllustrationGenerationRequest()
        request.generalDescription = "Test theme"
        request.illustrationCount = 4
        request.generationsPerService = 1
        request.individualDescriptions = []
        
        def user = new User()
        user.email = "test@example.com"
        user.coins = 10
        
        when: "generation fails at description generation stage"
        def future = service.generateIllustrations(request, user)
        def response = future.join()
        
        then: "coin deduction succeeds"
        1 * coinManagementService.deductCoinsForGeneration(user, 1) >> 
            new CoinManagementService.CoinDeductionResult(true, false, 1, null)
        
        and: "description generation fails"
        1 * illustrationDescriptionGenerationService.ensureFourDescriptions(_, _) >> 
            CompletableFuture.failedFuture(new RuntimeException("LLM error"))
        
        and: "error response is returned without refund (error is handled at service level)"
        response.status == "error"
        response.bananaResults[0].status == "error"
        
        and: "no refund occurs because error was handled at service level"
        0 * coinManagementService.refundCoins(_, _, _)
    }
    
    def "should handle reference image and generate first illustration with image-to-image"() {
        given: "a request with reference image"
        def request = new IllustrationGenerationRequest()
        request.generalDescription = "Tech gadgets"
        request.illustrationCount = 4
        request.generationsPerService = 1
        request.referenceImageBase64 = Base64.encoder.encodeToString("reference-image-data".bytes)
        request.individualDescriptions = []
        
        def user = new User()
        user.email = "test@example.com"
        user.coins = 10
        
        and: "LLM generates 4 descriptions"
        def generatedDescriptions = ["Laptop", "Smartphone", "Tablet", "Smartwatch"]
        
        and: "mock image data"
        def referenceImageData = "reference-image-data".bytes
        def firstImageData = "first-image-from-ref".bytes
        def subsequentImageData = "subsequent-image".bytes
        
        when: "generating illustrations with reference image"
        def future = service.generateIllustrations(request, user)
        def response = future.join()
        
        then: "coin deduction succeeds"
        1 * coinManagementService.deductCoinsForGeneration(user, 1) >> 
            new CoinManagementService.CoinDeductionResult(true, false, 1, null)
        
        and: "descriptions are generated"
        1 * illustrationDescriptionGenerationService.ensureFourDescriptions("Tech gadgets", []) >> 
            CompletableFuture.completedFuture(generatedDescriptions)
        
        and: "first illustration uses image-to-image with reference"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(
            "Tech gadgets", "Laptop") >> "First prompt from reference"
        1 * bananaModelService.generateImageToImage("First prompt from reference", referenceImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(firstImageData)
        
        and: "subsequent illustrations use the first as reference"
        3 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(_, _) >> 
            ["Prompt 2", "Prompt 3", "Prompt 4"]
        3 * bananaModelService.generateImageToImage(_, firstImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(subsequentImageData)
        
        and: "persistence succeeds"
        1 * illustrationPersistenceService.persistGeneratedIllustrations(_, _, _, _)
        
        and: "all 4 illustrations are generated"
        response.status == "success"
        response.illustrations.size() == 4
    }
    
    def "should generate more illustrations from original image"() {
        given: "original image data and descriptions"
        def originalImageData = "original-grid-image".bytes
        def generalTheme = "Tech gadgets"
        def seed = 12345L
        def descriptions = ["New item 1", "New item 2", "New item 3", "New item 4"]
        
        and: "mock generated image data"
        def firstImageData = "first-more-image".bytes
        def subsequentImageData = "subsequent-more-image".bytes
        
        when: "generating more illustrations"
        def future = service.generateMoreIllustrationsFromImage(originalImageData, generalTheme, seed, descriptions)
        def illustrations = future.join()
        
        then: "first prompt is generated from general theme and first description"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(generalTheme, "New item 1") >> 
            "First illustration prompt"
        
        and: "first illustration uses image-to-image with original"
        1 * bananaModelService.generateImageToImage("First illustration prompt", originalImageData, seed, "4:3", false) >> 
            CompletableFuture.completedFuture(firstImageData)
        
        and: "subsequent illustrations use first as reference and general theme"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(generalTheme, "New item 2") >> "Prompt 2"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(generalTheme, "New item 3") >> "Prompt 3"
        1 * illustrationPromptGenerationService.generatePromptForSubsequentIndividualIllustration(generalTheme, "New item 4") >> "Prompt 4"
        3 * bananaModelService.generateImageToImage(_, firstImageData, _, "4:3", false) >> 
            CompletableFuture.completedFuture(subsequentImageData)
        
        and: "all 4 illustrations are generated"
        illustrations.size() == 4
        illustrations[0].description == "New item 1"
        illustrations[1].description == "New item 2"
        illustrations[2].description == "New item 3"
        illustrations[3].description == "New item 4"
        illustrations.every { it.serviceSource == "banana" }
    }
}

