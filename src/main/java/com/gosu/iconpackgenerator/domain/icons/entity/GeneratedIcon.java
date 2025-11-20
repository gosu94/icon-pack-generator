package com.gosu.iconpackgenerator.domain.icons.entity;

import com.gosu.iconpackgenerator.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_icons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedIcon {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "request_id", nullable = false)
    private String requestId;
    
    @Column(name = "icon_id", nullable = false)
    private String iconId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "service_source", nullable = false)
    private String serviceSource;
    
    @Column(name = "grid_position")
    private Integer gridPosition;
    
    @Column(name = "generation_index")
    private Integer generationIndex;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;
    
    @Column(name = "icon_count")
    private Integer iconCount;
    
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "used_prompt_enhancer")
    private Boolean usedPromptEnhancer = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_original_grid")
    private Boolean isOriginalGrid = false;
    
    @Column(name = "icon_type")
    private String iconType; // "original" or "variation"

    public String getImageUrl() {
        return this.filePath;
    }
}
