package com.gosu.iconpackgenerator.domain.mockups.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_mockup_components")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedMockupComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "component_id", nullable = false, unique = true)
    private String componentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mockup_id_fk", nullable = false)
    @JsonBackReference
    private GeneratedMockup mockup;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "component_order")
    private Integer componentOrder;

    @Column(name = "label", length = 128)
    private String label;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

