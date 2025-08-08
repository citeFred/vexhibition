package com.meta.vexhibition.exhibition.repository;

import com.meta.vexhibition.exhibition.domain.Exhibition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Long> {
    List<Exhibition> findAllByOrderByCreatedAtDesc();
}
