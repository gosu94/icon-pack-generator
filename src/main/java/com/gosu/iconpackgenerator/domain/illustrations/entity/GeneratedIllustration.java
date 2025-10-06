package com.gosu.iconpackgenerator.domain.illustrations.entity;

import com.gosu.iconpackgenerator.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_illustrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedIllustration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "request_id", nullable = false)
    private String requestId;
    
    @Column(name = "illustration_id", nullable = false)
    private String illustrationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "grid_position")
    private Integer gridPosition;
    
    @Column(name = "generation_index")
    private Integer generationIndex;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;
    
    @Column(name = "illustration_count")
    private Integer illustrationCount;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_original_grid")
    private Boolean isOriginalGrid = false;
    
    @Column(name = "illustration_type")
    private String illustrationType; // "original" or "variation"
}

