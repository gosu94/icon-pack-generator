package com.gosu.iconpackgenerator.payment.dto;

import lombok.Data;

@Data
public class CheckoutSessionRequest {
    private String productType; // "starter-pack", "creator-pack", "pro-pack"
}
