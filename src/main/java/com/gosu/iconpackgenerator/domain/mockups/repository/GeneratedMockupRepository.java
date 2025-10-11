package com.gosu.iconpackgenerator.domain.mockups.repository;

import com.gosu.iconpackgenerator.domain.mockups.entity.GeneratedMockup;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedMockupRepository extends JpaRepository<GeneratedMockup, Long> {
    
    List<GeneratedMockup> findByRequestId(String requestId);
    
    List<GeneratedMockup> findByRequestIdAndMockupType(String requestId, String mockupType);
    
    Page<GeneratedMockup> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<GeneratedMockup> findByUserOrderByCreatedAtDesc(User user);
    
    List<GeneratedMockup> findByFilePathIn(List<String> filePaths);
    
    long countByUser(User user);
}

