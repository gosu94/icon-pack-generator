package com.gosu.iconpackgenerator.domain.illustrations.repository;

import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedIllustrationRepository extends JpaRepository<GeneratedIllustration, Long> {
    
    List<GeneratedIllustration> findByRequestId(String requestId);
    
    Page<GeneratedIllustration> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    List<GeneratedIllustration> findByFilePathIn(List<String> filePaths);
    
    long countByUser(User user);
}

