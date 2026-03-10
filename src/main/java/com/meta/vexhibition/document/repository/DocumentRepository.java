package com.meta.vexhibition.document.repository;

import com.meta.vexhibition.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByProductionId(Long productionId);

    List<Document> findByProductionIdAndProductionExhibitionId(Long productionId, Long exhibitionId);
}
