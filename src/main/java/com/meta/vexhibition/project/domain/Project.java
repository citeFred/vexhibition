package com.meta.vexhibition.project.domain;

import com.meta.vexhibition.common.TimeStamped;
import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.file.domain.File;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "project")
@Entity
public class Project extends TimeStamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String teamname;

    @Column(nullable = false)
    private int generation;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000, nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<File> files = new ArrayList<>();

    public Project(String teamname, String title, int generation, String description, Exhibition exhibition) {
        this.teamname = teamname;
        this.generation = generation;
        this.title = title;
        this.description = description;
        this.exhibition = exhibition;
    }

    public void update(String title, String description, String teamname, int generation) {
        if (title != null && !title.isEmpty()) {
            this.title = title;
        }
        if (description != null && !description.isEmpty()) {
            this.description = description;
        }
        if (teamname != null && !teamname.isEmpty()) {
            this.teamname = teamname;
        }
        this.generation = generation;
    }
}
