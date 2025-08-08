package com.meta.vexhibition.project.repository;

import com.meta.vexhibition.project.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByIdAndExhibitionId(Long projectId, Long boardId);

    Page<Project> findByExhibitionId(Long boardId, Pageable pageable);

    Page<Project> findAll(Pageable pageable);
}