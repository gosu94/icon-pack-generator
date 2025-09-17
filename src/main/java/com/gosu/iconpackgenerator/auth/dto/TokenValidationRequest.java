package com.gosu.iconpackgenerator.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenValidationRequest {
    
    @NotBlank(message = "Token is required")
    private String token;
    
    private boolean reset = false; // true for password reset token, false for email verification token
}
