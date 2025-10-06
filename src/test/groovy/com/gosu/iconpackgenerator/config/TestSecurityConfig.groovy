package com.gosu.iconpackgenerator.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.TestingAuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

/**
 * Test security configuration that provides minimal security beans needed for tests.
 * This ensures the application context can load successfully during testing.
 */
@TestConfiguration
@Profile("test")
class TestSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder()
    }

    @Bean
    AuthenticationManager authenticationManager() {
        // Create a simple authentication manager for tests
        // Using TestingAuthenticationProvider which accepts any authentication
        return new ProviderManager(new TestingAuthenticationProvider())
    }

    @Bean
    PersistentTokenRepository persistentTokenRepository() {
        // Use in-memory token repository for tests
        return new InMemoryTokenRepositoryImpl()
    }
}
