package com.gosu.iconpackgenerator.domain.letters.entity;

import com.gosu.iconpackgenerator.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_letter_icons")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedLetterIcon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "letter", nullable = false, length = 4)
    private String letter;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "letter_group", nullable = false)
    private String letterGroup;

    @Column(name = "sequence_index")
    private Integer sequenceIndex;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getImageUrl() {
        return this.filePath;
    }
}
