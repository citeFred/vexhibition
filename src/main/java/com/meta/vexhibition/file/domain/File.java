package com.meta.vexhibition.file.domain;
import com.meta.vexhibition.common.TimeStamped;
import com.meta.vexhibition.production.domain.Production;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file")
public class File extends TimeStamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_id")
    private Production production;

    public File(String originalFileName, String storedFileName, String path, Production production, Integer displayOrder) {
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.path = path;
        this.production = production;
        this.displayOrder = displayOrder;
    }
}