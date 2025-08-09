package com.meta.vexhibition.project.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRequestDto {
    private String teamname;
    private int generation;
    private String title;
    private String description;
}
