package com.gosu.iconpackgenerator.domain.ai;

import ai.fal.client.FalClient;
import ai.fal.client.Output;
import ai.fal.client.SubscribeOptions;
import ai.fal.client.queue.QueueStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.gosu.iconpackgenerator.exception.FalAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for interacting with fal.ai's any-llm model for text generation.
 * This service provides access to various LLM models including GPT-4.1.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnyLlmModelService {

    private final FalClient falClient;
    private final ObjectMapper objectMapper;
    
    private static final String ANY_LLM_ENDPOINT = "fal-ai/any-llm";
    private static final String DEFAULT_MODEL = "openai/gpt-4.1";
    private static final String DEFAULT_PRIORITY = "latency";
    
    /**
     * Generate text completion using the default model (GPT-4.1).
     * 
     * @param prompt The text prompt for completion
     * @return CompletableFuture containing the generated text
     */
    public CompletableFuture<String> generateCompletion(String prompt) {
        return generateCompletion(prompt, null);
    }
    
    /**
     * Generate text completion with a custom system prompt.
     * 
     * @param prompt The text prompt for completion
     * @param systemPrompt System prompt to provide context or instructions to the model
     * @return CompletableFuture containing the generated text
     */
    public CompletableFuture<String> generateCompletion(String prompt, String systemPrompt) {
        return generateCompletion(prompt, systemPrompt, DEFAULT_MODEL);
    }
    
    /**
     * Generate text completion with custom model selection.
     * 
     * @param prompt The text prompt for completion
     * @param systemPrompt System prompt to provide context or instructions to the model
     * @param model The model to use (e.g., "openai/gpt-4.1", "anthropic/claude-3.7-sonnet")
     * @return CompletableFuture containing the generated text
     */
    public CompletableFuture<String> generateCompletion(String prompt, String systemPrompt, String model) {
        return generateCompletion(prompt, systemPrompt, model, null, null, false);
    }
    
    /**
     * Generate text completion with full control over all parameters.
     * 
     * @param prompt The text prompt for completion
     * @param systemPrompt System prompt to provide context or instructions to the model
     * @param model The model to use
     * @param temperature Controls variety in responses (0-1, lower = more deterministic)
     * @param maxTokens Maximum tokens to generate
     * @param includeReasoning Whether to include reasoning in the response
     * @return CompletableFuture containing the generated text
     */
    public CompletableFuture<String> generateCompletion(String prompt, String systemPrompt, String model, 
                                                        Float temperature, Integer maxTokens, boolean includeReasoning) {
        log.info("Generating completion with any-llm for prompt: {}, model: {}, includeReasoning: {}", 
                prompt, model, includeReasoning);
        
        return generateLlmCompletionAsync(prompt, systemPrompt, model, temperature, maxTokens, includeReasoning)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Error generating completion with any-llm", error);
                    } else {
                        log.info("Successfully generated completion with any-llm, length: {} characters", result.length());
                    }
                });
    }
    
    /**
     * Generate text completion with reasoning enabled using the default model.
     * 
     * @param prompt The text prompt for completion
     * @return CompletableFuture containing a map with "output" and "reasoning" keys
     */
    public CompletableFuture<Map<String, String>> generateCompletionWithReasoning(String prompt) {
        return generateCompletionWithReasoning(prompt, null, DEFAULT_MODEL);
    }
    
    /**
     * Generate text completion with reasoning enabled.
     * 
     * @param prompt The text prompt for completion
     * @param systemPrompt System prompt to provide context or instructions to the model
     * @param model The model to use
     * @return CompletableFuture containing a map with "output" and "reasoning" keys
     */
    public CompletableFuture<Map<String, String>> generateCompletionWithReasoning(String prompt, String systemPrompt, String model) {
        log.info("Generating completion with reasoning for prompt: {}, model: {}", prompt, model);
        
        return generateLlmCompletionWithReasoningAsync(prompt, systemPrompt, model, null, null)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Error generating completion with reasoning", error);
                    } else {
                        log.info("Successfully generated completion with reasoning");
                    }
                });
    }
    
    private CompletableFuture<String> generateLlmCompletionAsync(String prompt, String systemPrompt, String model, 
                                                                  Float temperature, Integer maxTokens, boolean includeReasoning) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating LLM completion with endpoint: {}", ANY_LLM_ENDPOINT);
                
                Map<String, Object> input = createLlmInputMap(prompt, systemPrompt, model, temperature, maxTokens, includeReasoning);
                log.info("Making any-llm API call with input keys: {}", input.keySet());
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(ANY_LLM_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("LLM generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from any-llm API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted any-llm result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractTextFromResult(jsonResult);
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("any-llm API error: {}", e.getMessage());
                String userFriendlyMessage = sanitizeLlmError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling any-llm API", e);
                throw new FalAiException("Failed to generate completion with any-llm. Please try again or use a different prompt.", e);
            }
        });
    }
    
    private CompletableFuture<Map<String, String>> generateLlmCompletionWithReasoningAsync(String prompt, String systemPrompt, 
                                                                                             String model, Float temperature, Integer maxTokens) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Generating LLM completion with reasoning, endpoint: {}", ANY_LLM_ENDPOINT);
                
                Map<String, Object> input = createLlmInputMap(prompt, systemPrompt, model, temperature, maxTokens, true);
                log.info("Making any-llm API call with reasoning enabled");
                
                // Use fal.ai client API with queue update handling
                Output<JsonObject> output = falClient.subscribe(ANY_LLM_ENDPOINT,
                    SubscribeOptions.<JsonObject>builder()
                        .input(input)
                        .logs(true)
                        .resultType(JsonObject.class)
                        .onQueueUpdate(update -> {
                            if (update instanceof QueueStatus.InProgress) {
                                log.debug("LLM generation progress: {}", 
                                    ((QueueStatus.InProgress) update).getLogs());
                            }
                        })
                        .build()
                );
                log.debug("Received output from any-llm API: {}", output);
                
                // Extract the actual result from the Output wrapper
                JsonObject result = output.getData();
                log.debug("Extracted any-llm result: {}", result);
                
                // Convert JsonObject to JsonNode for our processing
                JsonNode jsonResult = objectMapper.readTree(result.toString());
                
                return extractTextAndReasoningFromResult(jsonResult);
                
            } catch (ai.fal.client.exception.FalException e) {
                log.error("any-llm API error: {}", e.getMessage());
                String userFriendlyMessage = sanitizeLlmError(e);
                throw new FalAiException(userFriendlyMessage, e);
            } catch (Exception e) {
                log.error("Error calling any-llm API", e);
                throw new FalAiException("Failed to generate completion with reasoning. Please try again or use a different prompt.", e);
            }
        });
    }
    
    private Map<String, Object> createLlmInputMap(String prompt, String systemPrompt, String model, 
                                                   Float temperature, Integer maxTokens, boolean includeReasoning) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", prompt);
        input.put("model", model != null ? model : DEFAULT_MODEL);
        input.put("priority", DEFAULT_PRIORITY);
        
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            input.put("system_prompt", systemPrompt);
        }
        
        if (temperature != null) {
            input.put("temperature", temperature);
        }
        
        if (maxTokens != null) {
            input.put("max_tokens", maxTokens);
        }
        
        if (includeReasoning) {
            input.put("reasoning", true);
        }
        
        log.debug("any-llm input parameters: {}", input);
        return input;
    }
    
    private String extractTextFromResult(JsonNode result) {
        try {
            log.debug("Extracting text from any-llm result: {}", result);
            
            // Check for error in response
            JsonNode errorNode = result.path("error");
            // Only treat as error if the node exists, is not missing, is not null, and has non-empty text
            if (errorNode != null && !errorNode.isMissingNode() && !errorNode.isNull() && !errorNode.asText().isEmpty()) {
                String error = errorNode.asText();
                log.error("Error in any-llm response: {}", error);
                throw new FalAiException("LLM generation failed: " + error);
            }
            
            // Extract the output text
            JsonNode outputNode = result.path("output");
            if (outputNode != null && !outputNode.isMissingNode() && !outputNode.isNull()) {
                String output = outputNode.asText();
                if (!output.isEmpty()) {
                    log.debug("Successfully extracted text output, length: {} characters", output.length());
                    return output;
                }
            }
            
            log.error("Could not extract output from any-llm result: {}", result);
            throw new FalAiException("Invalid response format from any-llm - no output found");
            
        } catch (FalAiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting text from any-llm response", e);
            throw new FalAiException("Failed to extract output from any-llm API response: " + e.getMessage(), e);
        }
    }
    
    private Map<String, String> extractTextAndReasoningFromResult(JsonNode result) {
        try {
            Map<String, String> response = new HashMap<>();
            
            // Check for error in response
            JsonNode errorNode = result.path("error");
            // Only treat as error if the node exists, is not missing, is not null, and has non-empty text
            if (errorNode != null && !errorNode.isMissingNode() && !errorNode.isNull() && !errorNode.asText().isEmpty()) {
                String error = errorNode.asText();
                log.error("Error in any-llm response: {}", error);
                throw new FalAiException("LLM generation failed: " + error);
            }
            
            // Extract the output text
            JsonNode outputNode = result.path("output");
            if (outputNode != null && !outputNode.isMissingNode()) {
                response.put("output", outputNode.asText());
            }
            
            // Extract the reasoning text if present
            JsonNode reasoningNode = result.path("reasoning");
            if (reasoningNode != null && !reasoningNode.isMissingNode()) {
                response.put("reasoning", reasoningNode.asText());
            }
            
            if (response.isEmpty()) {
                log.error("Could not extract output or reasoning from any-llm result: {}", result);
                throw new FalAiException("Invalid response format from any-llm - no output or reasoning found");
            }
            
            log.debug("Successfully extracted text output and reasoning");
            return response;
            
        } catch (FalAiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error extracting text and reasoning from any-llm response", e);
            throw new FalAiException("Failed to extract data from any-llm API response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sanitize any-llm API errors into user-friendly messages
     */
    private String sanitizeLlmError(ai.fal.client.exception.FalException e) {
        String errorMessage = e.getMessage();
        
        // Extract error code if available
        if (errorMessage.contains("422")) {
            return "Unable to process the request. The prompt may be invalid or the model may not support the requested parameters. Please try:\n" +
                   "- Using a different model\n" +
                   "- Modifying your prompt\n" +
                   "- Adjusting the temperature or max_tokens parameters";
        } else if (errorMessage.contains("400")) {
            return "Invalid request. Please check your input and try again.";
        } else if (errorMessage.contains("401") || errorMessage.contains("403")) {
            return "Authentication error with the AI service. Please contact support.";
        } else if (errorMessage.contains("429")) {
            return "Too many requests. Please wait a moment and try again.";
        } else if (errorMessage.contains("500") || errorMessage.contains("503")) {
            return "The AI service is temporarily unavailable. Please try again in a few moments.";
        }
        
        // Generic fallback
        return "Failed to generate completion with any-llm. Please try again or use a different prompt.";
    }
    
    /**
     * Get the default model name
     * @return The name of the default AI model
     */
    public String getDefaultModelName() {
        return DEFAULT_MODEL + " (via fal.ai)";
    }
    
    /**
     * Check if the service is available
     * @return true if the service is available
     */
    public boolean isAvailable() {
        try {
            // any-llm uses fal.ai infrastructure, check if client is configured
            return falClient != null;
        } catch (Exception e) {
            log.warn("any-llm service is not available: {}", e.getMessage());
            return false;
        }
    }
}

