package com.meta.vexhibition.project.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectUpdateRequestDto {
    private String teamname;
    private String title;
    private int generation;
    private String description;
}