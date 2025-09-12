package com.gosu.iconpackgenerator.payment.service;

import com.gosu.iconpackgenerator.payment.dto.CheckoutSessionResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StripeService {
    
    public CheckoutSessionResponse createCheckoutSession(String priceId, String userEmail, 
                                                       String productType, String coins) throws StripeException {
        
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:8080/payment/success")
                .setCancelUrl("http://localhost:8080/payment/failure")
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
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (session == null) {
                log.error("Could not retrieve session from event");
                return new HashMap<>();
            }
            
            return session.getMetadata();
            
        } catch (Exception e) {
            log.error("Error extracting session metadata", e);
            return new HashMap<>();
        }
    }
}