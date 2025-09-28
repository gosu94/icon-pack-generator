package com.gosu.iconpackgenerator.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for Spring Security's persistent login tokens (Remember Me functionality).
 * This table structure is required by Spring Security's JdbcTokenRepositoryImpl.
 */
@Entity
@Table(name = "persistent_logins", indexes = {
    @Index(name = "idx_persistent_logins_username", columnList = "username"),
    @Index(name = "idx_persistent_logins_series", columnList = "series")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistentLogin {
    
    @Id
    @Column(name = "series", length = 64, nullable = false)
    private String series;
    
    @Column(name = "username", length = 64, nullable = false)
    private String username;
    
    @Column(name = "token", length = 64, nullable = false)
    private String token;
    
    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;
}
