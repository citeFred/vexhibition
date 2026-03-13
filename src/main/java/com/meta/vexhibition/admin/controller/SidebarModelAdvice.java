package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.repository.ExhibitionRepository;
import com.meta.vexhibition.production.repository.ProductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.meta.vexhibition.admin")
@RequiredArgsConstructor
public class SidebarModelAdvice {

    private final ExhibitionRepository exhibitionRepository;
    private final ProductionRepository productionRepository;

    @ModelAttribute
    public void addSidebarCounts(Model model) {
        model.addAttribute("sidebarExhibitionCount", exhibitionRepository.count());
        model.addAttribute("sidebarProductionCount", productionRepository.count());
    }
}
