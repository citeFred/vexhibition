package com.meta.vexhibition.project.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProjectUpdateRequestDto {
    private String title;
    private String description;
    private String teamname;
    private int generation;
    private List<Long> deleteFileIds;
}