package com.meta.vexhibition.production.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductionRequestDto {
    private String teamname;
    private int generation;
    private String title;
    private String description;
}
