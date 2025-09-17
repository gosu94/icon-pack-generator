package com.gosu.iconpackgenerator.auth.dto;

import lombok.Data;

@Data
public class LoginResponse {
    
    private boolean success;
    private String message;
    private Long userId;
    private String email;
    private Integer coins;
    private Integer trialCoins;
}
