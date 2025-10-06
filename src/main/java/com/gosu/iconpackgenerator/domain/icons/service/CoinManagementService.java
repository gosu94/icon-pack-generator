package com.gosu.iconpackgenerator.domain.icons.service;

import com.gosu.iconpackgenerator.user.model.User;
import com.gosu.iconpackgenerator.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing coin operations including deduction, refunding,
 * and payment verification for icon generation requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoinManagementService {
    
    private final UserService userService;
    
    /**
     * Result of coin deduction operation
     */
    public static class CoinDeductionResult {
        private final boolean success;
        private final boolean usedTrialCoins;
        private final int deductedAmount;
        private final String errorMessage;
        
        public CoinDeductionResult(boolean success, boolean usedTrialCoins, int deductedAmount, String errorMessage) {
            this.success = success;
            this.usedTrialCoins = usedTrialCoins;
            this.deductedAmount = deductedAmount;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public boolean isUsedTrialCoins() { return usedTrialCoins; }
        public int getDeductedAmount() { return deductedAmount; }
        public String getErrorMessage() { return errorMessage; }
        
        public static CoinDeductionResult success(boolean usedTrialCoins, int deductedAmount) {
            return new CoinDeductionResult(true, usedTrialCoins, deductedAmount, null);
        }
        
        public static CoinDeductionResult failure(String errorMessage) {
            return new CoinDeductionResult(false, false, 0, errorMessage);
        }
    }
    
    /**
     * Deducts coins for icon generation with proper priority (regular coins first, then trial coins)
     * 
     * @param user The user to deduct coins from
     * @param cost The number of coins required
     * @return CoinDeductionResult with success status and coin type used
     */
    public CoinDeductionResult deductCoinsForGeneration(User user, int cost) {
        // Debug logging for coin checking
        int regularCoins = userService.getUserCoins(user.getId());
        int trialCoins = userService.getUserTrialCoins(user.getId());
        log.info("User {} coin check: regular={}, trial={}, cost={}", user.getEmail(), regularCoins, trialCoins, cost);
        
        // Check if we should use regular coins first, then trial coins as fallback
        if (userService.hasEnoughCoins(user.getId(), cost)) {
            // Use regular coins
            if (!userService.deductCoins(user.getId(), cost)) {
                log.error("Failed to deduct regular coins from user {}", user.getEmail());
                return CoinDeductionResult.failure("Failed to process payment. Please try again.");
            }
            log.info("Deducted {} regular coin(s) from user {} for icon generation", cost, user.getEmail());
            return CoinDeductionResult.success(false, cost);
            
        } else if (trialCoins > 0) {
            // Fallback to trial coins if no regular coins (trial coins always work regardless of cost)
            log.info("Using trial coins for user {} (regular coins insufficient: {} < {}, but trial coins available: {})", 
                    user.getEmail(), regularCoins, cost, trialCoins);
            if (!userService.deductTrialCoins(user.getId(), 1)) {
                log.error("Failed to deduct trial coins from user {}", user.getEmail());
                return CoinDeductionResult.failure("Failed to process trial coin. Please try again.");
            }
            log.info("Deducted 1 trial coin from user {} for icon generation (no regular coins available)", user.getEmail());
            return CoinDeductionResult.success(true, 1);
            
        } else {
            // No coins at all
            log.warn("User {} has insufficient coins: regular={}, trial={}, cost={}", user.getEmail(), regularCoins, trialCoins, cost);
            return CoinDeductionResult.failure("Insufficient coins. You need " + cost + " coin(s) to generate icons, or you can purchase coins in the store.");
        }
    }
    
    /**
     * Deducts a single coin for "more icons" generation
     * 
     * @param user The user to deduct coins from
     * @return CoinDeductionResult with success status and coin type used
     */
    public CoinDeductionResult deductCoinForMoreIcons(User user) {
        // Debug logging for more icons generation
        int regularCoins = userService.getUserCoins(user.getId());
        int trialCoins = userService.getUserTrialCoins(user.getId());
        log.info("User {} more icons generation - coin check: regular={}, trial={}", user.getEmail(), regularCoins, trialCoins);
        
        // Check coins with same priority as main generation (regular coins first, then trial coins)
        if (userService.hasEnoughCoins(user.getId(), 1)) {
            // Use regular coins
            if (!userService.deductCoins(user.getId(), 1)) {
                log.error("Failed to deduct regular coins from user {} for more icons", user.getEmail());
                return CoinDeductionResult.failure("Failed to process payment. Please try again.");
            }
            log.info("Deducted 1 regular coin from user {} for more icons generation", user.getEmail());
            return CoinDeductionResult.success(false, 1);
            
        } else if (userService.hasTrialCoins(user.getId())) {
            // Use trial coins as fallback
            if (!userService.deductTrialCoins(user.getId(), 1)) {
                log.error("Failed to deduct trial coins from user {} for more icons", user.getEmail());
                return CoinDeductionResult.failure("Failed to process trial coin. Please try again.");
            }
            log.info("Deducted 1 trial coin from user {} for more icons generation", user.getEmail());
            return CoinDeductionResult.success(true, 1);
            
        } else {
            log.warn("User {} has insufficient coins for more icons generation: regular={}, trial={}", user.getEmail(), regularCoins, trialCoins);
            return CoinDeductionResult.failure("Insufficient coins. You need 1 coin to generate more icons, or you can purchase coins in the store.");
        }
    }
    
    /**
     * Refunds coins to a user
     * 
     * @param user The user to refund coins to
     * @param amount The number of coins to refund
     * @param isTrialCoins Whether to refund trial coins or regular coins
     */
    public void refundCoins(User user, int amount, boolean isTrialCoins) {
        try {
            if (isTrialCoins) {
                userService.addTrialCoins(user.getId(), amount);
                log.info("Refunded {} trial coin(s) to user {}", amount, user.getEmail());
            } else {
                userService.addCoins(user.getId(), amount);
                log.info("Refunded {} regular coin(s) to user {}", amount, user.getEmail());
            }
        } catch (Exception e) {
            log.error("Failed to refund {} {} coin(s) to user {}", amount, isTrialCoins ? "trial" : "regular", user.getEmail(), e);
            throw new RuntimeException("Failed to refund " + (isTrialCoins ? "trial " : "") + "coins to user " + user.getEmail(), e);
        }
    }
}
