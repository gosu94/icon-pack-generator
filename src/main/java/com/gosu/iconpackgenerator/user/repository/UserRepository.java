package com.gosu.iconpackgenerator.user.repository;

import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    Optional<User> findByEmailVerificationToken(String token);
    
    Optional<User> findByPasswordResetToken(String token);
    
    Optional<User> findByUnsubscribeToken(String token);

    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            """)
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("""
            SELECT FUNCTION('date', u.registeredAt) AS registrationDate, COUNT(u)
            FROM User u
            WHERE u.registeredAt >= :startDate AND u.registeredAt < :endDate
            GROUP BY FUNCTION('date', u.registeredAt)
            ORDER BY FUNCTION('date', u.registeredAt)
            """)
    List<Object[]> countRegistrationsByDateRange(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
}
