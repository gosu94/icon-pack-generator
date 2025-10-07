package com.gosu.iconpackgenerator.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAdminDto {
    private Long id;
    private String email;
    private LocalDateTime lastLogin;
    private Integer coins;
    private Integer trialCoins;
    private Long generatedIconsCount;
    private Long generatedIllustrationsCount;
    private LocalDateTime registeredAt;
    private String authProvider;
    private Boolean isActive;
}
