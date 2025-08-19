package com.gosu.icon_pack_generator.service;

import com.gosu.icon_pack_generator.config.AIServicesConfig;
import com.gosu.icon_pack_generator.dto.IconGenerationRequest;
import com.gosu.icon_pack_generator.dto.IconGenerationResponse;
import com.gosu.icon_pack_generator.dto.ServiceProgressUpdate;
import com.gosu.icon_pack_generator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
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
    private final GptModelService gptModelService;
    private final ImagenModelService imagenModelService;
    private final ImageProcessingService imageProcessingService;
    private final PromptGenerationService promptGenerationService;
    private final AIServicesConfig aiServicesConfig;
    
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request) {
        return generateIcons(request, null);
    }
    
    /**
     * Generate icons with optional progress callback for real-time updates
     */
    public CompletableFuture<IconGenerationResponse> generateIcons(IconGenerationRequest request, ProgressUpdateCallback progressCallback) {
        List<String> enabledServices = new ArrayList<>();
        if (aiServicesConfig.isFluxAiEnabled()) enabledServices.add("FalAI");
        if (aiServicesConfig.isRecraftEnabled()) enabledServices.add("Recraft");
        if (aiServicesConfig.isPhotonEnabled()) enabledServices.add("Photon");
        if (aiServicesConfig.isGptEnabled()) enabledServices.add("GPT");
        if (aiServicesConfig.isImagenEnabled()) enabledServices.add("Imagen");
        
        // Generate or use provided seed for consistent results across services
        Long seed = request.getSeed() != null ? request.getSeed() : generateRandomSeed();
        
        log.info("Starting icon generation for {} icons with theme: {} using enabled services: {} (seed: {})", 
                request.getIconCount(), request.getGeneralDescription(), enabledServices, seed);
        
        String requestId = UUID.randomUUID().toString();
        
        // Send initial progress updates only for enabled services
        if (progressCallback != null) {
            if (aiServicesConfig.isFluxAiEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "flux"));
            }
            
            if (aiServicesConfig.isRecraftEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "recraft"));
            }
            
            if (aiServicesConfig.isPhotonEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "photon"));
            }
            
            if (aiServicesConfig.isGptEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "gpt"));
            }
            
            if (aiServicesConfig.isImagenEnabled()) {
                progressCallback.onUpdate(ServiceProgressUpdate.serviceStarted(requestId, "imagen"));
            }
        }
        
        // Generate icons only with enabled services
        CompletableFuture<IconGenerationResponse.ServiceResults> falAiFuture = 
                aiServicesConfig.isFluxAiEnabled() ? 
                generateIconsWithServiceAndCallback(request, requestId, fluxModelService, "flux", seed, progressCallback) :
                CompletableFuture.completedFuture(createDisabledServiceResult("flux"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> recraftFuture = 
                aiServicesConfig.isRecraftEnabled() ? 
                generateIconsWithServiceAndCallback(request, requestId, recraftModelService, "recraft", seed, progressCallback) :
                CompletableFuture.completedFuture(createDisabledServiceResult("recraft"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> photonFuture = 
                aiServicesConfig.isPhotonEnabled() ? 
                generateIconsWithServiceAndCallback(request, requestId, photonModelService, "photon", seed, progressCallback) :
                CompletableFuture.completedFuture(createDisabledServiceResult("photon"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> gptFuture = 
                aiServicesConfig.isGptEnabled() ? 
                generateIconsWithServiceAndCallback(request, requestId, gptModelService, "gpt", seed, progressCallback) :
                CompletableFuture.completedFuture(createDisabledServiceResult("gpt"));
        
        CompletableFuture<IconGenerationResponse.ServiceResults> imagenFuture = 
                aiServicesConfig.isImagenEnabled() ? 
                generateIconsWithServiceAndCallback(request, requestId, imagenModelService, "imagen", seed, progressCallback) :
                CompletableFuture.completedFuture(createDisabledServiceResult("imagen"));
        
        return CompletableFuture.allOf(falAiFuture, recraftFuture, photonFuture, gptFuture, imagenFuture)
                .thenApply(v -> {
                    IconGenerationResponse.ServiceResults falAiResults = falAiFuture.join();
                    IconGenerationResponse.ServiceResults recraftResults = recraftFuture.join();
                    IconGenerationResponse.ServiceResults photonResults = photonFuture.join();
                    IconGenerationResponse.ServiceResults gptResults = gptFuture.join();
                    IconGenerationResponse.ServiceResults imagenResults = imagenFuture.join();
                    
                    IconGenerationResponse finalResponse = createCombinedResponse(requestId, falAiResults, recraftResults, photonResults, gptResults, imagenResults, seed);
                    
                    // Send final completion update
                    if (progressCallback != null) {
                        progressCallback.onUpdate(ServiceProgressUpdate.allComplete(requestId, finalResponse.getMessage()));
                    }
                    
                    return finalResponse;
                })
                .exceptionally(error -> {
                    log.error("Error generating icons for request {}", requestId, error);
                    return createErrorResponse(requestId, "Failed to generate icons: " + error.getMessage());
                });
    }
    
    private CompletableFuture<IconGenerationResponse.ServiceResults> generateIconsWithServiceAndCallback(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName, Long seed, ProgressUpdateCallback progressCallback) {
        
        return generateIconsWithService(request, requestId, aiService, serviceName, seed)
                .whenComplete((result, error) -> {
                    if (progressCallback != null) {
                        if (error != null) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceName, getDetailedErrorMessage(error, serviceName), result != null ? result.getGenerationTimeMs() : 0L));
                        } else if ("success".equals(result.getStatus())) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceCompleted(
                                    requestId, serviceName, result.getIcons(), result.getOriginalGridImageBase64(), result.getGenerationTimeMs()));
                        } else if ("error".equals(result.getStatus())) {
                            progressCallback.onUpdate(ServiceProgressUpdate.serviceFailed(
                                    requestId, serviceName, result.getMessage(), result.getGenerationTimeMs()));
                        }
                    }
                });
    }
    
    private CompletableFuture<IconGenerationResponse.ServiceResults> generateIconsWithService(
            IconGenerationRequest request, String requestId, AIModelService aiService, String serviceName, Long seed) {
        
        long startTime = System.currentTimeMillis();
        
        return generateIconsInternalWithService(request, aiService, serviceName, seed)
                .thenApply(iconResult -> {
                    long generationTime = System.currentTimeMillis() - startTime;
                    IconGenerationResponse.ServiceResults result = new IconGenerationResponse.ServiceResults();
                    result.setServiceName(serviceName);
                    result.setStatus("success");
                    result.setMessage("Icons generated successfully");
                    result.setIcons(iconResult.getIcons());
                    result.setOriginalGridImageBase64(iconResult.getOriginalGridImageBase64());
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
    
    private CompletableFuture<IconGenerationResult> generateIconsInternalWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        if (request.getIconCount() == 9) {
            return generateSingleGridWithService(request, aiService, serviceName, seed);
        } else {
            return generateDoubleGridWithService(request, aiService, serviceName, seed);
        }
    }
    
    private CompletableFuture<IconGenerationResult> generateSingleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
        String prompt = promptGenerationService.generatePromptFor3x3Grid(
                request.getGeneralDescription(), 
                request.getIndividualDescriptions()
        );
        
        return generateImageWithSeed(aiService, prompt, seed)
                .thenApply(imageData -> {
                    List<String> base64Icons = imageProcessingService.cropIconsFromGrid(imageData, 9);
                    return createIconListWithOriginalImage(base64Icons, imageData, request, serviceName);
                });
    }
    
    private CompletableFuture<IconGenerationResult> generateDoubleGridWithService(
            IconGenerationRequest request, AIModelService aiService, String serviceName, Long seed) {
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
        return generateImageWithSeed(aiService, firstPrompt, seed)
                .thenCompose(firstImageData -> {
                    List<String> firstGrid = imageProcessingService.cropIconsFromGrid(firstImageData, 9);
                    
                    // Create a list of icons to avoid for the second grid
                    List<String> iconsToAvoid = createAvoidanceList(firstNineDescriptions, request.getGeneralDescription());
                    
                    // Use image-to-image for second grid if service supports it
                    String secondPrompt = promptGenerationService.generatePromptFor3x3Grid(
                            request.getGeneralDescription(), secondNineDescriptions, iconsToAvoid);
                    
                    if (supportsImageToImage(aiService)) {
                        return generateImageToImageWithService(aiService, secondPrompt, firstImageData, seed)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9);
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    // For 18 icons, use the first grid image as the original reference
                                    return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
                                });
                    } else {
                        // Fallback to regular generation for services that don't support image-to-image
                        return generateImageWithSeed(aiService, secondPrompt, seed)
                                .thenApply(secondImageData -> {
                                    List<String> secondGrid = imageProcessingService.cropIconsFromGrid(secondImageData, 9);
                                    
                                    List<String> allIcons = new ArrayList<>(firstGrid);
                                    allIcons.addAll(secondGrid);
                                    
                                    // For 18 icons, use the first grid image as the original reference
                                    return createIconListWithOriginalImage(allIcons, firstImageData, request, serviceName);
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
    
    /**
     * Helper class to hold both icons and original image data
     */
    private static class IconGenerationResult {
        private final List<IconGenerationResponse.GeneratedIcon> icons;
        private final String originalGridImageBase64;
        
        public IconGenerationResult(List<IconGenerationResponse.GeneratedIcon> icons, String originalGridImageBase64) {
            this.icons = icons;
            this.originalGridImageBase64 = originalGridImageBase64;
        }
        
        public List<IconGenerationResponse.GeneratedIcon> getIcons() {
            return icons;
        }
        
        public String getOriginalGridImageBase64() {
            return originalGridImageBase64;
        }
    }
    
    private IconGenerationResult createIconListWithOriginalImage(List<String> base64Icons, byte[] originalImageData, IconGenerationRequest request, String serviceName) {
        List<IconGenerationResponse.GeneratedIcon> icons = createIconList(base64Icons, request, serviceName);
        String originalGridImageBase64 = Base64.getEncoder().encodeToString(originalImageData);
        return new IconGenerationResult(icons, originalGridImageBase64);
    }
    
    private IconGenerationResponse createCombinedResponse(String requestId, 
            IconGenerationResponse.ServiceResults falAiResults, 
            IconGenerationResponse.ServiceResults recraftResults,
            IconGenerationResponse.ServiceResults photonResults,
            IconGenerationResponse.ServiceResults gptResults,
            IconGenerationResponse.ServiceResults imagenResults, Long seed) {
        
        IconGenerationResponse response = new IconGenerationResponse();
        response.setRequestId(requestId);
        response.setFalAiResults(falAiResults);
        response.setRecraftResults(recraftResults);
        response.setPhotonResults(photonResults);
        response.setGptResults(gptResults);
        response.setImagenResults(imagenResults);
        response.setSeed(seed);
        
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
        if (gptResults.getIcons() != null) {
            allIcons.addAll(gptResults.getIcons());
        }
        if (imagenResults.getIcons() != null) {
            allIcons.addAll(imagenResults.getIcons());
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
        
        if (!"disabled".equals(gptResults.getStatus())) {
            enabledCount++;
            enabledServices.add("GPT");
            if ("success".equals(gptResults.getStatus())) {
                successCount++;
                successfulServices.add("GPT");
            }
        }
        
        if (!"disabled".equals(imagenResults.getStatus())) {
            enabledCount++;
            enabledServices.add("Imagen");
            if ("success".equals(imagenResults.getStatus())) {
                successCount++;
                successfulServices.add("Imagen");
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
        response.setGptResults(errorResult);
        
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
        return aiService instanceof FluxModelService || aiService instanceof RecraftModelService || 
               aiService instanceof PhotonModelService || aiService instanceof GptModelService;
    }
    
    private CompletableFuture<byte[]> generateImageToImageWithService(AIModelService aiService, String prompt, byte[] sourceImageData, Long seed) {
        log.info("Attempting image-to-image generation with service: {}", aiService.getClass().getSimpleName());
        
        try {
            if (aiService instanceof FluxModelService) {
                log.info("Using FalAiModelService generateImageToImage for image-to-image");
                return ((FluxModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("FalAiModelService image-to-image failed, falling back to regular generation", throwable);
                                return generateImageWithSeed(aiService, prompt, seed).join();
                            }
                            return result;
                        });
            } else if (aiService instanceof RecraftModelService) {
                log.info("Using RecraftModelService generateImageToImage for image-to-image");
                return ((RecraftModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("RecraftModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Recraft", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Recraft", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof PhotonModelService) {
                log.info("Using PhotonModelService generateImageToImage (delegates to Flux) for image-to-image");
                return ((PhotonModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("PhotonModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for Photon", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for Photon", fallbackError);
                                }
                            }
                            return result;
                        });
            } else if (aiService instanceof GptModelService) {
                log.info("Using GptModelService generateImageToImage for image-to-image");
                return ((GptModelService) aiService).generateImageToImage(prompt, sourceImageData, seed)
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                log.error("GptModelService image-to-image failed, falling back to regular generation", throwable);
                                try {
                                    return generateImageWithSeed(aiService, prompt, seed).join();
                                } catch (Exception fallbackError) {
                                    log.error("Fallback generation also failed for GPT", fallbackError);
                                    throw new RuntimeException("Both image-to-image and fallback generation failed for GPT", fallbackError);
                                }
                            }
                            return result;
                        });
            } else {
                log.info("Service doesn't support image-to-image, using regular generation");
                // Fallback to regular generation
                return generateImageWithSeed(aiService, prompt, seed);
            }
        } catch (Exception e) {
            log.error("Error in image-to-image generation, falling back to regular generation", e);
            return generateImageWithSeed(aiService, prompt, seed);
        }
    }
    
    /**
     * Helper method to generate image with optional seed support
     */
    private CompletableFuture<byte[]> generateImageWithSeed(AIModelService aiService, String prompt, Long seed) {
        // Check if the service supports seed parameter by trying specific service types
        if (aiService instanceof FluxModelService) {
            return ((FluxModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof RecraftModelService) {
            return ((RecraftModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof PhotonModelService) {
            return ((PhotonModelService) aiService).generateImage(prompt, seed);
        } else if (aiService instanceof GptModelService) {
            return ((GptModelService) aiService).generateImage(prompt, seed);
        } else {
            // Fallback to basic generateImage method for unknown service types
            return aiService.generateImage(prompt);
        }
    }
    
    /**
     * Generate a random seed for reproducible results
     */
    private long generateRandomSeed() {
        return System.currentTimeMillis() + (long) (Math.random() * 1000);
    }
}
