package com.meta.vexhibition.production.repository;

import com.meta.vexhibition.production.domain.Production;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductionRepository extends JpaRepository<Production, Long> {
    Optional<Production> findByIdAndExhibitionId(Long productionId, Long boardId);

    Page<Production> findByExhibitionId(Long boardId, Pageable pageable);

    Page<Production> findAll(Pageable pageable);
}