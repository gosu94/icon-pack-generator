package com.gosu.iconpackgenerator.payment.service;

import com.gosu.iconpackgenerator.payment.dto.CheckoutSessionResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StripeService {

    @Value("${app.base-url}")
    private String baseUrl;
    
    public CheckoutSessionResponse createCheckoutSession(String priceId, String userEmail, 
                                                       String productType, String coins) throws StripeException {
        
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(baseUrl + "/payment/success/index.html")
                .setCancelUrl(baseUrl + "/payment/failure/index.html")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("user_email", userEmail)
                .putMetadata("product_type", productType)
                .putMetadata("coins", coins)
                .setCustomerEmail(userEmail)
                .build();
        
        Session session = Session.create(params);
        
        return new CheckoutSessionResponse(session.getId(), session.getUrl());
    }
    
    public Event parseWebhookEvent(String payload, String sigHeader, String webhookSecret) throws Exception {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
    
    public String getEventType(Event event) {
        return event.getType();
    }
    
    public Map<String, String> getSessionMetadata(Event event) {
        try {
            // Only process checkout.session.completed events
            if (!"checkout.session.completed".equals(event.getType())) {
                log.warn("Attempting to extract session metadata from non-session event: {}", event.getType());
                return new HashMap<>();
            }
            
            log.debug("Processing checkout.session.completed event: {}", event.getId());
            
            // Extract session ID directly from event data (this is the reliable approach)
            String sessionId = extractSessionIdFromEventData(event);
            if (sessionId == null) {
                log.error("Could not extract session ID from checkout.session.completed event: {}", event.getId());
                return new HashMap<>();
            }
            
            log.debug("Extracted session ID: {} from event: {}", sessionId, event.getId());
            
            // Fetch the session directly from Stripe API (this is the recommended approach)
            Session session = Session.retrieve(sessionId);
            if (session == null) {
                log.error("Could not retrieve session from Stripe API for ID: {}", sessionId);
                return new HashMap<>();
            }
            
            log.debug("Successfully retrieved session: {} from Stripe API", session.getId());
            
            Map<String, String> metadata = session.getMetadata();
            if (metadata == null || metadata.isEmpty()) {
                log.error("Session metadata is null or empty for session: {}", session.getId());
                return new HashMap<>();
            }
            
            log.debug("Successfully extracted {} metadata fields from session: {}", metadata.size(), session.getId());
            return metadata;
            
        } catch (StripeException e) {
            log.error("Stripe API error when retrieving session from event: {} - {}", event.getId(), e.getMessage(), e);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Unexpected error extracting session metadata from event: {} - {}", event.getId(), e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    private String extractSessionIdFromEventData(Event event) {
        try {
            // Method 1: Direct approach using the event data object
            // According to Stripe docs: Session session = (Session) event.getData().getObject();
            @SuppressWarnings("deprecation")
            Object eventDataObject = event.getData().getObject();
            log.debug("Event data object type: {}", eventDataObject != null ? eventDataObject.getClass().getSimpleName() : "null");
            
            if (eventDataObject != null) {
                // If it's already a Session object, get the ID directly
                if (eventDataObject instanceof Session) {
                    String sessionId = ((Session) eventDataObject).getId();
                    log.debug("Extracted session ID from Session object: {}", sessionId);
                    return sessionId;
                }
                
                // If it's a Map (raw JSON), try to get the ID field
                if (eventDataObject instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) eventDataObject;
                    if (dataMap.containsKey("id")) {
                        String sessionId = (String) dataMap.get("id");
                        log.debug("Extracted session ID from data map: {}", sessionId);
                        return sessionId;
                    }
                    log.debug("Data map keys: {}", dataMap.keySet());
                }
                
                // Try to get ID from the object using reflection/toString parsing as last resort
                String objectString = eventDataObject.toString();
                log.debug("Event data object string representation: {}", objectString);
            }
            
            // Method 2: Try deserialization as fallback
            try {
                StripeObject deserializedObject = event.getDataObjectDeserializer().getObject().orElse(null);
                log.debug("Deserialized object type: {}", deserializedObject != null ? deserializedObject.getClass().getSimpleName() : "null");
                
                if (deserializedObject instanceof Session) {
                    String sessionId = ((Session) deserializedObject).getId();
                    log.debug("Extracted session ID from deserialized Session: {}", sessionId);
                    return sessionId;
                }
            } catch (Exception deserializationException) {
                log.debug("Deserialization failed: {}", deserializationException.getMessage());
            }
            
            log.error("Could not extract session ID from event data. Event ID: {}, Event Type: {}", 
                     event.getId(), event.getType());
            return null;
            
        } catch (Exception e) {
            log.error("Unexpected error extracting session ID from event data", e);
            return null;
        }
    }
}