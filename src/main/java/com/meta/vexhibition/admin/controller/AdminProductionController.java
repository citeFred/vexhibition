package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import com.meta.vexhibition.production.dto.ProductionRequestDto;
import com.meta.vexhibition.production.dto.ProductionResponseDto;
import com.meta.vexhibition.production.dto.ProductionUpdateRequestDto;
import com.meta.vexhibition.production.service.ProductionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/admin/exhibitions/{exhibitionId}/productions")
@RequiredArgsConstructor
public class AdminProductionController {

    private final ProductionService productionService;
    private final ExhibitionService exhibitionService;

    @GetMapping("/new")
    public String showAddProductionForm(@PathVariable Long exhibitionId, Model model) {
        model.addAttribute("productionRequestDto", new ProductionRequestDto());
        model.addAttribute("exhibitionId", exhibitionId);
        return "admin/production-form";
    }

    @PostMapping
    public String addProduction(@PathVariable Long exhibitionId,
                             @ModelAttribute ProductionRequestDto productionRequestDto,
                             @RequestParam("files") List<MultipartFile> files) {
        productionService.createProduction(exhibitionId, productionRequestDto, files);
        return "redirect:/admin";
    }

    @GetMapping
    public String getProductionList(@PathVariable Long exhibitionId, Model model) {
        ExhibitionResponseDto exhibition = exhibitionService.getExhibitionById(exhibitionId);
        model.addAttribute("exhibition", exhibition);

        Pageable pageable = Pageable.unpaged();
        Page<ProductionResponseDto> productionPage = productionService.getProductionsByExhibitionId(exhibitionId, pageable);
        model.addAttribute("productions", productionPage.getContent());

        return "admin/production-list";
    }

    @GetMapping("/{productionId}/edit")
    public String showEditProductionForm(@PathVariable Long exhibitionId,
                                      @PathVariable Long productionId, Model model) {
        ProductionResponseDto productionDto = productionService.getProductionById(exhibitionId, productionId);
        model.addAttribute("productionDto", productionDto);
        model.addAttribute("exhibitionId", exhibitionId);
        return "admin/production-edit-form";
    }

    @PostMapping("/{productionId}/edit")
    public String updateProduction(@PathVariable Long exhibitionId,
                                @PathVariable Long productionId,
                                @ModelAttribute ProductionUpdateRequestDto requestDto,
                                @RequestParam("addFiles") List<MultipartFile> addFiles) {
        productionService.updateProduction(exhibitionId, productionId, requestDto, addFiles);
        return "redirect:/admin/exhibitions/" + exhibitionId + "/productions";
    }

    @GetMapping("/{productionId}/delete")
    public String deleteProduction(@PathVariable Long exhibitionId,
                                @PathVariable Long productionId) {
        productionService.deleteProduction(exhibitionId, productionId);

        return "redirect:/admin/exhibitions/" + exhibitionId + "/productions";
    }
}