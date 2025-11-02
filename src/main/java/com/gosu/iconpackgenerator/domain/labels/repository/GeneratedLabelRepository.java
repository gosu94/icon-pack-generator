package com.gosu.iconpackgenerator.domain.labels.repository;

import com.gosu.iconpackgenerator.domain.labels.entity.GeneratedLabel;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedLabelRepository extends JpaRepository<GeneratedLabel, Long> {

    List<GeneratedLabel> findByUserOrderByCreatedAtDesc(User user);

    List<GeneratedLabel> findByRequestId(String requestId);

    List<GeneratedLabel> findByUserAndLabelTypeOrderByCreatedAtDesc(User user, String labelType);

    void deleteByRequestId(String requestId);

    List<GeneratedLabel> findByFilePathIn(List<String> filePaths);

    Long countByUser(User user);
}
