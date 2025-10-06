package com.gosu.iconpackgenerator.domain.icons.repository;

import com.gosu.iconpackgenerator.domain.icons.entity.GeneratedIcon;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GeneratedIconRepository extends JpaRepository<GeneratedIcon, Long> {
    
    List<GeneratedIcon> findByRequestId(String requestId);
    
    List<GeneratedIcon> findByUser(User user);
    
    List<GeneratedIcon> findByUserOrderByCreatedAtDesc(User user);
    
    List<GeneratedIcon> findByUserAndRequestId(User user, String requestId);
    
    @Query("SELECT DISTINCT g.requestId FROM GeneratedIcon g WHERE g.user = :user ORDER BY g.createdAt DESC")
    List<String> findDistinctRequestIdsByUserOrderByCreatedAtDesc(@Param("user") User user);
    
    @Query("SELECT COUNT(g) FROM GeneratedIcon g WHERE g.user = :user AND g.createdAt >= :since")
    Long countByUserAndCreatedAtAfter(@Param("user") User user, @Param("since") LocalDateTime since);
    
    Long countByUser(User user);
    
    List<GeneratedIcon> findByUserAndIconType(User user, String iconType);
    
    List<GeneratedIcon> findByUserAndIconTypeOrderByCreatedAtDesc(User user, String iconType);
    
    List<GeneratedIcon> findByRequestIdAndIconType(String requestId, String iconType);
    
    void deleteByRequestId(String requestId);
    
    List<GeneratedIcon> findByFilePathIn(List<String> filePaths);
}
