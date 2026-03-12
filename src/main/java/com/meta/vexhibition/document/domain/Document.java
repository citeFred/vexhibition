package com.meta.vexhibition.document.domain;

import com.meta.vexhibition.common.TimeStamped;
import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.production.domain.Production;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document")
public class Document extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // pgvector에 저장된 청크 수 (삭제 시 청크 ID 재구성에 사용)
    @Column(nullable = false)
    private int chunkCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_id", nullable = false)
    private Production production;

    // S3 파일 정보는 File 엔티티에 위임 (cascade 없이 명시적 관리)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    public Document(Production production, File file) {
        this.production = production;
        this.file = file;
        this.chunkCount = 0;
    }
}
