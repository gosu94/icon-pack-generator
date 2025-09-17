package com.gosu.iconpackgenerator.auth.dto;

import lombok.Data;

@Data
public class EmailCheckResponse {
    
    private String email;
    private boolean exists;
    private boolean hasPassword;
}
