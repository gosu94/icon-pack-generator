package com.gosu.iconpackgenerator.user.model;

import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "directory_path", nullable = false)
    private String directoryPath;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "coins")
    private Integer coins = 0; // Default starting coins
    
    @Column(name = "trial_coins")
    private Integer trialCoins = 0; // Trial coins for first-time users
    
    @Column(name = "email_verified")
    private Boolean emailVerified = false;
    
    @Column(name = "email_verification_token")
    private String emailVerificationToken;
    
    @Column(name = "password_reset_token")
    private String passwordResetToken;
    
    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;
    
    @Column(name = "auth_provider")
    private String authProvider = "EMAIL"; // EMAIL, GOOGLE, etc.
    
    @Column(name = "notifications")
    private Boolean notifications = true; // Default to true for email notifications
    
    @Column(name = "unsubscribe_token")
    private String unsubscribeToken;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GeneratedIcon> generatedIcons;
}
