package com.meta.vexhibition.production.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class ProductionUpdateRequestDto {
    private String title;
    private String description;
    private String teamname;
    private int generation;
    private List<Long> deleteFileIds;
}