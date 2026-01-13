package com.gosu.iconpackgenerator.domain.illustrations.repository;

import com.gosu.iconpackgenerator.domain.illustrations.entity.GeneratedIllustration;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GeneratedIllustrationRepository extends JpaRepository<GeneratedIllustration, Long> {
    
    List<GeneratedIllustration> findByRequestId(String requestId);
    
    List<GeneratedIllustration> findByRequestIdAndIllustrationType(String requestId, String illustrationType);
    
    Page<GeneratedIllustration> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<GeneratedIllustration> findByUserOrderByCreatedAtDesc(User user);

    List<GeneratedIllustration> findByUserAndRequestId(User user, String requestId);

    List<GeneratedIllustration> findByUserAndRequestIdAndIllustrationType(User user, String requestId, String illustrationType);

    void deleteByUserAndRequestId(User user, String requestId);

    void deleteByUserAndRequestIdAndIllustrationType(User user, String requestId, String illustrationType);
    
    List<GeneratedIllustration> findByFilePathIn(List<String> filePaths);
    
    long countByUser(User user);

    List<GeneratedIllustration> findByIsWatermarkedTrueAndCreatedAtBefore(LocalDateTime cutoff);

    List<GeneratedIllustration> findByRequestIdAndIllustrationIdInAndIsWatermarkedFalse(String requestId, List<String> illustrationIds);
}
