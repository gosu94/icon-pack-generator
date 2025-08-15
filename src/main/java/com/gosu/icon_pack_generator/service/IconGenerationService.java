package com.gosu.icon_pack_generator.service;

import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class IconGenerationService {
    
    private final FalAiModelService falAiModelService;
    private final OpenAiModelService openAiModelService;
    private final RecraftModelService recraftModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request) {
        log.info("Starting icon generation for {} icons with theme: {} using FalAI, OpenAI, and Recraft", 
                request.getIconCount(), request.getGeneralDescription());
        
        String requestId = UUID.randomUUID().toString();
        
        // Generate icons with all three services simultaneously
        CompletableFuture<IconGenerationResponse.ServiceResults> falAiFuture = 
                generateIconsWithService(request, requestId, falAiModelService, "fal-ai");
        
        CompletableFuture<IconGenerationResponse.ServiceResults> openAiFuture = 
                generateIconsWithService(request, requestId, openAiModelService, "openai");
        
        CompletableFuture<IconGenerationResponse.ServiceResults> recraftFuture = 
                generateIconsWithService(request, requestId, recraftModelService, "recraft");
        
        return CompletableFuture.allOf(falAiFuture, openAiFuture, recraftFuture)
                .thenApply(v -> {
                    IconGenerationResponse.ServiceResults falAiResults = falAiFuture.join();
//                    IconGenerationResponse.ServiceResults openAiResults = openAiFuture.join();
                    IconGenerationResponse.ServiceResults recraftResults = recraftFuture.join();
                    
                    return createCombinedResponse(requestId, falAiResults, null, recraftResults);
                })
                .exceptionally(error -> {
                    log.error("Error generating icons for request {}", requestId, error);
                    return createErrorResponse(requestId, "Failed to generate icons: " + error.getMessage());
                });
    }
    
    private CompletableFuture<IconGenerationResponse.ServiceResults> generateIconsWithService(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName) {
        
        long startTime = System.currentTimeMillis();
        
        return generateIconsInternalWithService(request, aiService, serviceName)
                .thenApply(icons -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
                    result.setServiceName(serviceName);
                    result.setStatus("success");
                    result.setMessage("Icons generated successfully");
                    result.setIcons(icons);
                    result.setGenerationTimeMs(generationTime);
                    return result;
                })
                .exceptionally(error -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    log.error("Error generating icons with {}", serviceName, error);
                    IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
                    result.setServiceName(serviceName);
                    result.setStatus("error");
                    result.setMessage(getDetailedErrorMessage(error, serviceName));
                    result.setIcons(new ArrayList<>());
                    result.setGenerationTimeMs(generationTime);
                    return result;
                });
    }
    
    private CompletableFuture<List<IconGenerationResponse.GeneratedIcon>> generateIconsInternalWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName) {
        if (request.getIconCount() == 9) {
            return generateSingleGridWithService(request, aiService, serviceName);
        } else {
            return generateDoubleGridWithService(request, aiService, serviceName);
        }
    }
    
    private CompletableFuture<List<IconGenerationResponse.GeneratedIcon>> generateSingleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName) {
        String prompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), 
                request.getIndividualDescriptions()
        );
        
        return aiService.generateImage(prompt)
                .thenApply(imageData -> imageProcessingService.cropIconsFromGrid(imageData, 9))
                .thenApply(base64Icons -> createIconList(base64Icons, request, serviceName));
    }
    
    private CompletableFuture<List<IconGenerationResponse.GeneratedIcon>> generateDoubleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName) {
        // For 18 icons, generate two 3x3 grids
        List<String> firstNineDescriptions = request.getIndividualDescriptions() != null ? 
                request.getIndividualDescriptions().subList(0, Math.min(9, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        List<String> secondNineDescriptions = request.getIndividualDescriptions() != null && 
                request.getIndividualDescriptions().size() > 9 ? 
                request.getIndividualDescriptions().subList(9, Math.min(18, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        String firstPrompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), firstNineDescriptions);
        String secondPrompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), secondNineDescriptions);
        
        CompletableFuture<byte[]> firstImageFuture = aiService.generateImage(firstPrompt);
        CompletableFuture<byte[]> secondImageFuture = aiService.generateImage(secondPrompt);
        
        return CompletableFuture.allOf(firstImageFuture, secondImageFuture)
                .thenApply(v -> {
                    byte[] firstImageData = firstImageFuture.join();
                    byte[] secondImageData = secondImageFuture.join();
                    
                    List<String> firstGrid = imageProcessingService.cropIconsFromGrid(firstImageData, 9);
                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9);
                    
                    List<String> allIcons = new ArrayList<>(firstGrid);
                    allIcons.addAll(secondGrid);
                    
                    return createIconList(allIcons, request, serviceName);
                });
    }
    
    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, IconGenerationRequest request, String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        
        List<String> descriptions = request.getIndividualDescriptions() != null ? 
                request.getIndividualDescriptions() : new ArrayList<>();
        
        for (int i = 0; i < base64Icons.size(); i++) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));
            icon.setDescription(i < descriptions.size() && !descriptions.get(i).isEmpty() ? 
                    descriptions.get(i) : "Generated icon " + (i + 1));
            icon.setGridPosition(i);
            icon.setServiceSource(serviceName);
            icons.add(icon);
        }
        
        return icons;
    }
    
    private IconGenerationResponse createCombinedResponse(String requestId, 
            IconGenerationResponse.ServiceResults falAiResults, 
            IconGenerationResponse.ServiceResults openAiResults,
            IconGenerationResponse.ServiceResults recraftResults) {
        
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setFalAiResults(falAiResults);
        response.setOpenAiResults(openAiResults);
        response.setRecraftResults(recraftResults);
        
        // Combine all icons for backward compatibility
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        if (falAiResults.getIcons() != null) {
            allIcons.addAll(falAiResults.getIcons());
        }
        if (openAiResults.getIcons() != null) {
            allIcons.addAll(openAiResults.getIcons());
        }
        if (recraftResults.getIcons() != null) {
            allIcons.addAll(recraftResults.getIcons());
        }
        response.setIcons(allIcons);
        
        // Set overall status
        int successCount = 0;
        List<String> successfulServices = new ArrayList<>();
        
        if ("success".equals(falAiResults.getStatus())) {
            successCount++;
            successfulServices.add("Flux-Pro");
        }
        if ("success".equals(openAiResults.getStatus())) {
            successCount++;
            successfulServices.add("GPT-Image");
        }
        if ("success".equals(recraftResults.getStatus())) {
            successCount++;
            successfulServices.add("Recraft");
        }
        
        if (successCount > 0) {
            response.setStatus("success");
            if (successCount == 3) {
                response.setMessage("Icons generated successfully with all three services");
            } else if (successCount == 2) {
                response.setMessage("Icons generated successfully with " + String.join(" and ", successfulServices));
            } else {
                response.setMessage("Icons generated successfully with " + successfulServices.get(0));
            }
        } else {
            response.setStatus("error");
            response.setMessage("All services failed to generate icons");
        }
        
        return response;
    }
    
    private IconGenerationResponse createErrorResponse(String requestId, String message) {
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setStatus("error");
        response.setMessage(message);
        response.setIcons(new ArrayList<>());
        
        // Create error results for all services
        IconGenerationResponse.ServiceResults errorResult = new IconGenerationResponse.ServiceResults();
        errorResult.setStatus("error");
        errorResult.setMessage(message);
        errorResult.setIcons(new ArrayList<>());
        
        response.setFalAiResults(errorResult);
        response.setOpenAiResults(errorResult);
        response.setRecraftResults(errorResult);
        
        return response;
    }
    
    private String getDetailedErrorMessage(Throwable error, String serviceName) {
        String message = error.getMessage();
        if (error.getCause() instanceof FalAiException) {
            return error.getCause().getMessage();
        } else if (error instanceof FalAiException) {
            return error.getMessage();
        }
        return serviceName + " service failed: " + (message != null ? message : "Unknown error");
    }
}
