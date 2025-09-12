package com.gosu.iconpackgenerator.payment.controller;

import com.gosu.iconpackgenerator.config.StripeConfig;
import com.gosu.iconpackgenerator.payment.dto.CheckoutSessionRequest;
import com.gosu.iconpackgenerator.payment.dto.CheckoutSessionResponse;
import com.gosu.iconpackgenerator.payment.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    
    private final StripeConfig stripeConfig;
    private final StripeService stripeService;
    
    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestBody CheckoutSessionRequest request,
            Authentication authentication) {
        
        try {
            String userEmail = authentication.getName();
            StripeConfig.ProductConfig productConfig = stripeConfig.getProducts().get(request.getProductType());
            
            if (productConfig == null) {
                return ResponseEntity.badRequest().build();
            }
            
            String priceId = productConfig.getPriceId();
            Integer coins = productConfig.getCoins();
            
            CheckoutSessionResponse response = stripeService.createCheckoutSession(
                    priceId, userEmail, request.getProductType(), coins.toString()
            );
            
            log.info("Created checkout session {} for user {} for product {}", 
                    response.getSessionId(), userEmail, request.getProductType());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error creating checkout session", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getStripeConfig() {
        return ResponseEntity.ok(Map.of("publishableKey", stripeConfig.getPublishableKey()));
    }
}
