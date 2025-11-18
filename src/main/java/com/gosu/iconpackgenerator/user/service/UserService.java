package com.gosu.iconpackgenerator.user.service;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final SignalMessageService signalMessageService;

    /**
     * Get user's current coin balance
     */
    public Integer getUserCoins(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(User::getCoins).orElse(0);
    }
    
    /**
     * Get user's current coin balance by email
     */
    public Integer getUserCoinsByEmail(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.map(User::getCoins).orElse(0);
    }
    
    /**
     * Check if user has enough coins for a generation
     * @param userId the user ID
     * @param coinsNeeded number of coins needed
     * @return true if user has enough coins
     */
    public boolean hasEnoughCoins(Long userId, int coinsNeeded) {
        Integer currentCoins = getUserCoins(userId);
        return currentCoins >= coinsNeeded;
    }
    
    /**
     * Check if user has enough coins for a generation by email
     * @param email the user email
     * @param coinsNeeded number of coins needed
     * @return true if user has enough coins
     */
    public boolean hasEnoughCoinsByEmail(String email, int coinsNeeded) {
        Integer currentCoins = getUserCoinsByEmail(email);
        return currentCoins >= coinsNeeded;
    }
    
    /**
     * Deduct coins from user account
     * @param userId the user ID
     * @param coinsToDeduct number of coins to deduct
     * @return true if deduction was successful
     */
    @Transactional
    public boolean deductCoins(Long userId, int coinsToDeduct) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return false;
        }
        
        User user = userOptional.get();
        Integer currentCoins = user.getCoins();
        
        if (currentCoins == null || currentCoins < coinsToDeduct) {
            log.warn("User {} has insufficient coins. Current: {}, Required: {}", 
                    userId, currentCoins, coinsToDeduct);
            return false;
        }
        
        user.setCoins(currentCoins - coinsToDeduct);
        userRepository.save(user);
        
        log.info("Deducted {} coins from user {}. New balance: {}", 
                coinsToDeduct, userId, user.getCoins());
        return true;
    }
    
    /**
     * Deduct coins from user account by email
     * @param email the user email
     * @param coinsToDeduct number of coins to deduct
     * @return true if deduction was successful
     */
    @Transactional
    public boolean deductCoinsByEmail(String email, int coinsToDeduct) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return false;
        }
        
        User user = userOptional.get();
        Integer currentCoins = user.getCoins();
        
        if (currentCoins == null || currentCoins < coinsToDeduct) {
            log.warn("User {} has insufficient coins. Current: {}, Required: {}", 
                    email, currentCoins, coinsToDeduct);
            return false;
        }
        
        user.setCoins(currentCoins - coinsToDeduct);
        userRepository.save(user);
        
        log.info("Deducted {} coins from user {}. New balance: {}", 
                coinsToDeduct, email, user.getCoins());
        return true;
    }
    
    /**
     * Add coins to user account
     * @param userId the user ID
     * @param coinsToAdd number of coins to add
     */
    @Transactional
    public void addCoins(Long userId, int coinsToAdd) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return;
        }
        
        User user = userOptional.get();
        Integer currentCoins = user.getCoins() != null ? user.getCoins() : 0;
        user.setCoins(currentCoins + coinsToAdd);
        userRepository.save(user);
        
        log.info("Added {} coins to user {}. New balance: {}", 
                coinsToAdd, userId, user.getCoins());
    }
    
    /**
     * Add coins to user account by email
     * @param email the user email
     * @param coinsToAdd number of coins to add
     */
    @Transactional
    public void addCoinsByEmail(String email, int coinsToAdd) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return;
        }
        
        User user = userOptional.get();
        Integer currentCoins = user.getCoins() != null ? user.getCoins() : 0;
        user.setCoins(currentCoins + coinsToAdd);
        userRepository.save(user);
        
        log.info("Added {} coins to user {}. New balance: {}", 
                coinsToAdd, email, user.getCoins());
    }
    
    /**
     * Get user by email
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Get user by ID
     */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * Get user's current trial coin balance
     */
    public Integer getUserTrialCoins(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> u.getTrialCoins() != null ? u.getTrialCoins() : 0).orElse(0);
    }
    
    /**
     * Get user's current trial coin balance by email
     */
    public Integer getUserTrialCoinsByEmail(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.map(u -> u.getTrialCoins() != null ? u.getTrialCoins() : 0).orElse(0);
    }
    
    /**
     * Check if user has trial coins
     */
    public boolean hasTrialCoins(Long userId) {
        int trialCoins = getUserTrialCoins(userId);
        log.debug("Checking trial coins for user {}: {}", userId, trialCoins);
        return trialCoins > 0;
    }
    
    /**
     * Check if user has trial coins by email
     */
    public boolean hasTrialCoinsByEmail(String email) {
        return getUserTrialCoinsByEmail(email) > 0;
    }
    
    /**
     * Add trial coins to user account (typically 1 coin on first login)
     */
    @Transactional
    public void addTrialCoins(Long userId, int trialCoinsToAdd) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return;
        }
        
        User user = userOptional.get();
        Integer currentTrialCoins = user.getTrialCoins() != null ? user.getTrialCoins() : 0;
        user.setTrialCoins(currentTrialCoins + trialCoinsToAdd);
        userRepository.save(user);
        
        log.info("Added {} trial coins to user {}. New trial balance: {}", 
                trialCoinsToAdd, userId, user.getTrialCoins());
    }
    
    /**
     * Add trial coins to user account by email
     */
    @Transactional
    public void addTrialCoinsByEmail(String email, int trialCoinsToAdd) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return;
        }
        
        User user = userOptional.get();
        Integer currentTrialCoins = user.getTrialCoins() != null ? user.getTrialCoins() : 0;
        user.setTrialCoins(currentTrialCoins + trialCoinsToAdd);
        userRepository.save(user);
        
        log.info("Added {} trial coins to user {}. New trial balance: {}", 
                trialCoinsToAdd, email, user.getTrialCoins());
    }
    
    /**
     * Deduct trial coins from user account
     */
    @Transactional
    public boolean deductTrialCoins(Long userId, int trialCoinsToDeduct) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return false;
        }
        
        User user = userOptional.get();
        Integer currentTrialCoins = user.getTrialCoins() != null ? user.getTrialCoins() : 0;
        
        if (currentTrialCoins < trialCoinsToDeduct) {
            log.warn("User {} has insufficient trial coins. Current: {}, Required: {}", 
                    userId, currentTrialCoins, trialCoinsToDeduct);
            return false;
        }
        
        user.setTrialCoins(currentTrialCoins - trialCoinsToDeduct);
        userRepository.save(user);
        
        log.info("Deducted {} trial coins from user {}. New trial balance: {}", 
                trialCoinsToDeduct, userId, user.getTrialCoins());
        return true;
    }
    
    /**
     * Deduct trial coins from user account by email
     */
    @Transactional
    public boolean deductTrialCoinsByEmail(String email, int trialCoinsToDeduct) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return false;
        }
        
        User user = userOptional.get();
        Integer currentTrialCoins = user.getTrialCoins() != null ? user.getTrialCoins() : 0;
        
        if (currentTrialCoins < trialCoinsToDeduct) {
            log.warn("User {} has insufficient trial coins. Current: {}, Required: {}", 
                    email, currentTrialCoins, trialCoinsToDeduct);
            return false;
        }
        
        user.setTrialCoins(currentTrialCoins - trialCoinsToDeduct);
        userRepository.save(user);
        
        log.info("Deducted {} trial coins from user {}. New trial balance: {}", 
                trialCoinsToDeduct, email, user.getTrialCoins());
        return true;
    }
    
    /**
     * Update user's notification preferences
     * @param userId the user ID
     * @param enabled true to enable notifications, false to disable
     * @return true if update was successful
     */
    @Transactional
    public boolean updateNotifications(Long userId, boolean enabled) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return false;
        }
        
        User user = userOptional.get();
        user.setNotifications(enabled);
        userRepository.save(user);
        
        log.info("Updated notifications preference for user {} to {}", userId, enabled);
        return true;
    }
    
    /**
     * Update user's notification preferences by email
     * @param email the user email
     * @param enabled true to enable notifications, false to disable
     * @return true if update was successful
     */
    @Transactional
    public boolean updateNotificationsByEmail(String email, boolean enabled) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return false;
        }
        
        User user = userOptional.get();
        user.setNotifications(enabled);
        userRepository.save(user);
        
        log.info("Updated notifications preference for user {} to {}", email, enabled);
        return true;
    }
    
    /**
     * Generate or retrieve unsubscribe token for a user
     * @param userId the user ID
     * @return unsubscribe token
     */
    @Transactional
    public String getOrGenerateUnsubscribeToken(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.error("User with ID {} not found", userId);
            return null;
        }
        
        User user = userOptional.get();
        if (user.getUnsubscribeToken() == null || user.getUnsubscribeToken().isEmpty()) {
            String token = UUID.randomUUID().toString();
            user.setUnsubscribeToken(token);
            userRepository.save(user);
            log.info("Generated unsubscribe token for user {}", userId);
            return token;
        }
        
        return user.getUnsubscribeToken();
    }
    
    /**
     * Generate or retrieve unsubscribe token for a user by email
     * @param email the user email
     * @return unsubscribe token
     */
    @Transactional
    public String getOrGenerateUnsubscribeTokenByEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.error("User with email {} not found", email);
            return null;
        }
        
        User user = userOptional.get();
        if (user.getUnsubscribeToken() == null || user.getUnsubscribeToken().isEmpty()) {
            String token = UUID.randomUUID().toString();
            user.setUnsubscribeToken(token);
            userRepository.save(user);
            log.info("Generated unsubscribe token for user {}", email);
            return token;
        }
        
        return user.getUnsubscribeToken();
    }
    
    /**
     * Unsubscribe user by token (public endpoint, no authentication required)
     * @param token the unsubscribe token
     * @return true if unsubscribe was successful
     */
    @Transactional
    public boolean unsubscribeByToken(String token) {
        Optional<User> userOptional = userRepository.findByUnsubscribeToken(token);
        if (userOptional.isEmpty()) {
            log.warn("Invalid unsubscribe token provided: {}", token);
            signalMessageService.sendSignalMessage("Invalid unsubscribe token provided");
            return false;
        }
        
        User user = userOptional.get();
        user.setNotifications(false);
        userRepository.save(user);
        
        log.info("User {} unsubscribed from notifications via token", user.getEmail());
        signalMessageService.sendSignalMessage("User " + user.getEmail() + " unsubscribed from notifications via token");

        return true;
    }
}
