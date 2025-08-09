package com.meta.vexhibition.exhibition.domain;

import com.meta.vexhibition.common.TimeStamped;
import com.meta.vexhibition.project.domain.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "exhibition")
@Entity
public class Exhibition extends TimeStamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "exhibition", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Project> projects =  new ArrayList<>();


    public Exhibition(String title) {
        this.title = title;
    }

    public void update(String title) {
        this.title = title;
    }
}
