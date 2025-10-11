package com.gosu.iconpackgenerator.payment.controller;

import com.gosu.iconpackgenerator.config.StripeConfig;
import com.gosu.iconpackgenerator.payment.service.PaymentService;
import com.gosu.iconpackgenerator.payment.service.StripeService;
import com.gosu.iconpackgenerator.singal.SignalMessageService;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final StripeConfig stripeConfig;
    private final PaymentService paymentService;
    private final StripeService stripeService;
    private final SignalMessageService signalMessageService;

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
                    log.info("Successfully processed checkout.session.completed event");
                    break;
                case "payment_intent.created":
                case "payment_intent.succeeded":
                case "charge.succeeded":
                case "charge.updated":
                    // These are expected events that occur during payment processing
                    // We don't need to process them for our use case
                    log.debug("Received expected event type: {}", eventType);
                    break;
                default:
                    log.info("Unhandled event type: {}", eventType);
            }

            return ResponseEntity.ok("Success");

        } catch (Exception e) {
            String errorMessage = e.getMessage();
            signalMessageService.sendSignalMessage("[IconPackGen] Something went wrong during purchase: " + errorMessage);
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
                log.error("Could not retrieve session metadata from checkout.session.completed event");
                return;
            }

            String userEmail = metadata.get("user_email");
            String productType = metadata.get("product_type");
            String coinsStr = metadata.get("coins");

            if (userEmail == null || productType == null || coinsStr == null) {
                log.error("Missing required metadata in session. userEmail: {}, productType: {}, coins: {}",
                        userEmail != null ? "present" : "missing",
                        productType != null ? "present" : "missing",
                        coinsStr != null ? "present" : "missing");
                return;
            }

            int coins;
            try {
                coins = Integer.parseInt(coinsStr);
            } catch (NumberFormatException e) {
                log.error("Invalid coins value in metadata: {}", coinsStr, e);
                return;
            }

            paymentService.addCoinsToUser(userEmail, coins);
            signalMessageService.sendSignalMessage("[IconPackGen] You just made a sale for " + coinsStr + " coins!");
            log.info("Successfully added {} coins to user {} for product {}",
                    coins, userEmail, productType);

        } catch (Exception e) {
            log.error("Error processing checkout.session.completed event", e);
        }
    }
}
