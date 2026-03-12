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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        model.addAttribute("currentPage", "dashboard");
        return "admin/production-form";
    }

    @PostMapping
    public String addProduction(@PathVariable Long exhibitionId,
                                @ModelAttribute ProductionRequestDto productionRequestDto,
                                @RequestParam("files") List<MultipartFile> files,
                                RedirectAttributes redirectAttributes) {
        productionService.createProduction(exhibitionId, productionRequestDto, files);
        redirectAttributes.addFlashAttribute("successMessage", "작품이 성공적으로 등록되었습니다.");
        return "redirect:/admin/exhibitions/" + exhibitionId + "/productions";
    }

    @GetMapping
    public String getProductionList(@PathVariable Long exhibitionId, Model model) {
        ExhibitionResponseDto exhibition = exhibitionService.getExhibitionById(exhibitionId);
        model.addAttribute("exhibition", exhibition);

        Pageable pageable = Pageable.unpaged();
        Page<ProductionResponseDto> productionPage = productionService.getProductionsByExhibitionId(exhibitionId, pageable);
        model.addAttribute("productions", productionPage.getContent());
        model.addAttribute("currentPage", "dashboard");

        return "admin/production-list";
    }

    @GetMapping("/{productionId}/edit")
    public String showEditProductionForm(@PathVariable Long exhibitionId,
                                         @PathVariable Long productionId, Model model) {
        ProductionResponseDto productionDto = productionService.getProductionById(exhibitionId, productionId);
        model.addAttribute("productionDto", productionDto);
        model.addAttribute("exhibitionId", exhibitionId);
        model.addAttribute("currentPage", "dashboard");
        return "admin/production-edit-form";
    }

    @PostMapping("/{productionId}/edit")
    public String updateProduction(@PathVariable Long exhibitionId,
                                   @PathVariable Long productionId,
                                   @ModelAttribute ProductionUpdateRequestDto requestDto,
                                   @RequestParam("addFiles") List<MultipartFile> addFiles,
                                   RedirectAttributes redirectAttributes) {
        productionService.updateProduction(exhibitionId, productionId, requestDto, addFiles);
        redirectAttributes.addFlashAttribute("successMessage", "작품 정보가 수정되었습니다.");
        return "redirect:/admin/exhibitions/" + exhibitionId + "/productions";
    }

    @GetMapping("/{productionId}/delete")
    public String deleteProduction(@PathVariable Long exhibitionId,
                                   @PathVariable Long productionId,
                                   RedirectAttributes redirectAttributes) {
        productionService.deleteProduction(exhibitionId, productionId);
        redirectAttributes.addFlashAttribute("successMessage", "작품이 삭제되었습니다.");
        return "redirect:/admin/exhibitions/" + exhibitionId + "/productions";
    }
}
