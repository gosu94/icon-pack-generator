package com.gosu.iconpackgenerator.domain.mockups.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.gosu.iconpackgenerator.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "generated_mockups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedMockup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "request_id", nullable = false)
    private String requestId;
    
    @Column(name = "mockup_id", nullable = false)
    private String mockupId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "generation_index")
    private Integer generationIndex;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "mockup_type")
    private String mockupType; // "original" or "variation"

    @OneToMany(mappedBy = "mockup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeneratedMockupComponent> components = new ArrayList<>();

    public String getImageUrl() {
        return this.filePath;
    }
}
