package com.gosu.iconpackgenerator.payment.controller;

import com.gosu.iconpackgenerator.config.StripeConfig;
import com.gosu.iconpackgenerator.payment.service.PaymentService;
import com.gosu.iconpackgenerator.payment.service.StripeService;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    
    private final StripeConfig stripeConfig;
    private final PaymentService paymentService;
    private final StripeService stripeService;
    
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        try {
            Event event = stripeService.parseWebhookEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
            String eventType = stripeService.getEventType(event);
            
            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                default:
                    log.info("Unhandled event type: {}", eventType);
            }
            
            return ResponseEntity.ok("Success");
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("signature")) {
                log.error("Invalid signature in webhook", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing error");
        }
    }
    
    private void handleCheckoutSessionCompleted(Event event) {
        try {
            Map<String, String> metadata = stripeService.getSessionMetadata(event);
            
            if (metadata.isEmpty()) {
                log.error("Could not retrieve session metadata from event");
                return;
            }
            
            String userEmail = metadata.get("user_email");
            String productType = metadata.get("product_type");
            String coinsStr = metadata.get("coins");
            
            if (userEmail == null || productType == null || coinsStr == null) {
                log.error("Missing metadata in session");
                return;
            }
            
            int coins = Integer.parseInt(coinsStr);
            
            paymentService.addCoinsToUser(userEmail, coins);
            log.info("Successfully added {} coins to user {} for product {}", 
                    coins, userEmail, productType);
                    
        } catch (Exception e) {
            log.error("Error processing checkout.session.completed event", e);
        }
    }
}
