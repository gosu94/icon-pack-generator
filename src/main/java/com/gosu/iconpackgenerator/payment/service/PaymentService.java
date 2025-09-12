package com.gosu.iconpackgenerator.payment.service;

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
public class PaymentService {
    
    private final UserRepository userRepository;
    
    @Transactional
    public void addCoinsToUser(String userEmail, int coins) {
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        
        if (userOpt.isEmpty()) {
            log.error("User with email {} not found for coin payment", userEmail);
            return;
        }
        
        User user = userOpt.get();
        int currentCoins = user.getCoins() != null ? user.getCoins() : 0;
        int newCoinsTotal = currentCoins + coins;
        
        user.setCoins(newCoinsTotal);
        userRepository.save(user);
        
        log.info("Added {} coins to user {}. New balance: {}", 
                coins, userEmail, newCoinsTotal);
    }
}
