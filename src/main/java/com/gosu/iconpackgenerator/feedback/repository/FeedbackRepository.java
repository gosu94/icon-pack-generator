package com.gosu.iconpackgenerator.feedback.repository;

import com.gosu.iconpackgenerator.feedback.model.Feedback;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    boolean existsByUserAndCreatedAtAfter(User user, LocalDateTime createdAt);

    List<Feedback> findByUser(User user);
}
