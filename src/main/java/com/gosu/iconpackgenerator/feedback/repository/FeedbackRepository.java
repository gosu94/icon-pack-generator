package com.gosu.iconpackgenerator.feedback.repository;

import com.gosu.iconpackgenerator.feedback.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
