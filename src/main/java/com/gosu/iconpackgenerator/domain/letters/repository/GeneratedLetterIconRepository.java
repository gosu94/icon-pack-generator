package com.gosu.iconpackgenerator.domain.letters.repository;

import com.gosu.iconpackgenerator.domain.letters.entity.GeneratedLetterIcon;
import com.gosu.iconpackgenerator.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedLetterIconRepository extends JpaRepository<GeneratedLetterIcon, Long> {

    List<GeneratedLetterIcon> findByRequestIdOrderBySequenceIndexAsc(String requestId);

    List<GeneratedLetterIcon> findByUserOrderByCreatedAtDesc(User user);

    List<GeneratedLetterIcon> findByFilePathIn(List<String> filePaths);
}
