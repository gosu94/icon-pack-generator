package com.gosu.icon_pack_generator.service;

import com.gosu.icon_pack_generator.config.AIServicesConfig;
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
    
    private final FluxModelService fluxModelService;
    private final RecraftModelService recraftModelService;
    private final PhotonModelService photonModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final AIServicesConfig aiServicesConfig;
    
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request) {
        List<String> enabledServices = new ArrayList<>();
        if (aiServicesConfig.isFluxAiEnabled()) enabledServices.add("FalAI");
        if (aiServicesConfig.isRecraftEnabled()) enabledServices.add("Recraft");
        if (aiServicesConfig.isPhotonEnabled()) enabledServices.add("Photon");
        
        log.info("Starting icon generation for {} icons with theme: {} using enabled services: {}", 
                request.getIconCount(), request.getGeneralDescription(), enabledServices);
        
        String requestId = UUID.randomUUID().toString();
        
        // Generate icons only with enabled services
                CompletableFuture<IconGenerationResponse.ServiceResults> falAiFuture = 
                aiServicesConfig.isFluxAiEnabled() ? 
                generateIconsWithService(request, requestId, fluxModelService, "flux") :
                CompletableFuture.completedFuture(createDisabledServiceResult("flux"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> recraftFuture = 
                aiServicesConfig.isRecraftEnabled() ? 
                generateIconsWithService(request, requestId, recraftModelService, "recraft") :
                CompletableFuture.completedFuture(createDisabledServiceResult("recraft"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> photonFuture = 
                aiServicesConfig.isPhotonEnabled() ? 
                generateIconsWithService(request, requestId, photonModelService, "photon") :
                CompletableFuture.completedFuture(createDisabledServiceResult("photon"));
        
        return CompletableFuture.allOf(falAiFuture, recraftFuture, photonFuture)
                .thenApply(v -> {
                    IconGenerationResponse.ServiceResults falAiResults = falAiFuture.join();
                    IconGenerationResponse.ServiceResults recraftResults = recraftFuture.join();
                    IconGenerationResponse.ServiceResults photonResults = photonFuture.join();
                    
                    return createCombinedResponse(requestId, falAiResults, recraftResults, photonResults);
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
        // For 18 icons, generate first grid normally, then use image-to-image for second grid
        List<String> firstNineDescriptions = request.getIndividualDescriptions() != null ? 
                request.getIndividualDescriptions().subList(0, Math.min(9, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        List<String> secondNineDescriptions = request.getIndividualDescriptions() != null && 
                request.getIndividualDescriptions().size() > 9 ? 
                request.getIndividualDescriptions().subList(9, Math.min(18, request.getIndividualDescriptions().size())) : 
                new ArrayList<>();
        
        String firstPrompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), firstNineDescriptions);
        
        // Generate first grid
        return aiService.generateImage(firstPrompt)
                .thenCompose(firstImageData -> {
                    List<String> firstGrid = imageProcessingService.cropIconsFromGrid(firstImageData, 9);
                    
                    // Create a list of icons to avoid for the second grid
                    List<String> iconsToAvoid = createAvoidanceList(firstNineDescriptions, request.getGeneralDescription());
                    
                    // Use image-to-image for second grid if service supports it
                    String secondPrompt = promptGenerationService.generatePromptFor3x3Grid(
                            request.getGeneralDescription(), secondNineDescriptions, iconsToAvoid);
                    
                    if (supportsImageToImage(aiService)) {
                        return generateImageToImageWithService(aiService, secondPrompt, firstImageData)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9);
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    return createIconList(allIcons, request, serviceName);
                                });
                    } else {
                        // Fallback to regular generation for services that don't support image-to-image
                        return aiService.generateImage(secondPrompt)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9);
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    return createIconList(allIcons, request, serviceName);
                                });
                    }
                });
    }
    
    /**
     * Create a list of icon descriptions to avoid when generating the second grid
     * This includes specified descriptions from the first grid
     */
    private List<String> createAvoidanceList(List<String> firstGridDescriptions, String generalTheme) {
        List<String> avoidanceList = new ArrayList<>();
        
        // Add any specific descriptions that were provided for the first grid
        if (firstGridDescriptions != null) {
            firstGridDescriptions.stream()
                    .filter(desc -> desc != null && !desc.trim().isEmpty())
                    .forEach(avoidanceList::add);
        }
        
        return avoidanceList;
    }
    
    private List<IconGenerationResponse.GeneratedIcon> createIconList(List<String> base64Icons, IconGenerationRequest request, String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = new ArrayList<>();
        
        for (int i = 0; i < base64Icons.size(); i++) {
            IconGenerationResponse.GeneratedIcon icon = new IconGenerationResponse.GeneratedIcon();
            icon.setId(UUID.randomUUID().toString());
            icon.setBase64Data(base64Icons.get(i));
            icon.setDescription("");
            icon.setGridPosition(i);
            icon.setServiceSource(serviceName);
            icons.add(icon);
        }
        
        return icons;
    }
    
    private IconGenerationResponse createCombinedResponse(String requestId, 
            IconGenerationResponse.ServiceResults falAiResults, 
            IconGenerationResponse.ServiceResults recraftResults,
            IconGenerationResponse.ServiceResults photonResults) {
        
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setFalAiResults(falAiResults);
        response.setRecraftResults(recraftResults);
        response.setPhotonResults(photonResults);
        
        // Combine all icons for backward compatibility
        List<IconGenerationResponse.GeneratedIcon> allIcons = new ArrayList<>();
        if (falAiResults.getIcons() != null) {
            allIcons.addAll(falAiResults.getIcons());
        }
        if (recraftResults.getIcons() != null) {
            allIcons.addAll(recraftResults.getIcons());
        }
        if (photonResults.getIcons() != null) {
            allIcons.addAll(photonResults.getIcons());
        }
        response.setIcons(allIcons);
        
        // Set overall status
        int successCount = 0;
        int enabledCount = 0;
        List<String> successfulServices = new ArrayList<>();
        List<String> enabledServices = new ArrayList<>();
        
        if (!"disabled".equals(falAiResults.getStatus())) {
            enabledCount++;
            enabledServices.add("Flux-Pro");
            if ("success".equals(falAiResults.getStatus())) {
                successCount++;
                successfulServices.add("Flux-Pro");
            }
        }
        

        
        if (!"disabled".equals(recraftResults.getStatus())) {
            enabledCount++;
            enabledServices.add("Recraft");
            if ("success".equals(recraftResults.getStatus())) {
                successCount++;
                successfulServices.add("Recraft");
            }
        }
        
        if (!"disabled".equals(photonResults.getStatus())) {
            enabledCount++;
            enabledServices.add("Photon");
            if ("success".equals(photonResults.getStatus())) {
                successCount++;
                successfulServices.add("Photon");
            }
        }
        
        if (enabledCount == 0) {
            response.setStatus("error");
            response.setMessage("All AI services are disabled in configuration");
        } else if (successCount > 0) {
            response.setStatus("success");
            if (successCount == enabledCount) {
                response.setMessage("Icons generated successfully with all enabled services");
            } else if (successCount == 1) {
                response.setMessage("Icons generated successfully with " + successfulServices.get(0));
            } else {
                response.setMessage("Icons generated successfully with " + String.join(" and ", successfulServices));
            }
        } else {
            response.setStatus("error");
            response.setMessage("All enabled services failed to generate icons");
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
        response.setRecraftResults(errorResult);
        response.setPhotonResults(errorResult);
        
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
    
    private IconGenerationResponse.ServiceResults createDisabledServiceResult(String serviceName) {
        IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
        result.setServiceName(serviceName);
        result.setStatus("disabled");
        result.setMessage("Service is disabled in configuration");
        result.setIcons(new ArrayList<>());
        result.setGenerationTimeMs(0L);
        return result;
    }
    
    private boolean supportsImageToImage(AIModelService aiService) {
        // Check if the service supports image-to-image generation
        // PhotonModelService delegates to FluxModelService for image-to-image
        return aiService instanceof FluxModelService || aiService instanceof RecraftModelService || aiService instanceof PhotonModelService;
    }
    
    private CompletableFuture<byte[]> generateImageToImageWithService(AIModelService aiService, String prompt, byte[] sourceImageData) {
        log.info("Attempting image-to-image generation with service: {}", aiService.getClass().getSimpleName());
        
        try {
            if (aiService instanceof FluxModelService) {
                log.info("Using FalAiModelService generateImageToImage for image-to-image");
                return ((FluxModelService) aiService).generateImageToImage(prompt, sourceImageData)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("FalAiModelService image-to-image failed, falling back to regular generation", throwable);
                                return aiService.generateImage(prompt).join();
                            }
                            return result;
                        });
            } else if (aiService instanceof RecraftModelService) {
                log.info("Using RecraftModelService generateImageToImage for image-to-image");
                return ((RecraftModelService) aiService).generateImageToImage(prompt, sourceImageData)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("RecraftModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return aiService.generateImage(prompt).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Recraft", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Recraft", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof PhotonModelService) {
                log.info("Using PhotonModelService generateImageToImage (delegates to Flux) for image-to-image");
                return ((PhotonModelService) aiService).generateImageToImage(prompt, sourceImageData)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("PhotonModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return aiService.generateImage(prompt).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Photon", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Photon", fallbackError);
                                }
                            }
                            return result;
                        });
            } else {
                log.info("Service doesn't support image-to-image, using regular generation");
                // Fallback to regular generation
                return aiService.generateImage(prompt);
            }
        } catch (Exception e) {
            log.error("Error in image-to-image generation, falling back to regular generation", e);
            return aiService.generateImage(prompt);
        }
    }
}
