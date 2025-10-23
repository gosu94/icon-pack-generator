package com.gosu.iconpackgenerator.domain.labels.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gosu.iconpackgenerator.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_labels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "label_id", nullable = false)
    private String labelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "service_source", nullable = false)
    private String serviceSource;

    @Column(name = "generation_index")
    private Integer generationIndex;

    @Column(name = "label_text", columnDefinition = "TEXT")
    private String labelText;

    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;

    @Column(name = "label_type")
    private String labelType;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getImageUrl() {
        return this.filePath;
    }
}
