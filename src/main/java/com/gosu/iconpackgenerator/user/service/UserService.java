package com.gosu.iconpackgenerator.user.service;

import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    
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
}
