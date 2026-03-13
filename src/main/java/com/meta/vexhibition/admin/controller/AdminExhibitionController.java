package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.dto.ExhibitionRequestDto;
import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/exhibitions")
@RequiredArgsConstructor
public class AdminExhibitionController {

    private final ExhibitionService exhibitionService;

    @GetMapping("/new")
    public String showAddExhibitionForm(Model model) {
        model.addAttribute("exhibitionRequestDto", new ExhibitionRequestDto());
        model.addAttribute("currentPage", "dashboard");
        return "admin/exhibition-form";
    }

    @PostMapping
    public String addExhibition(@ModelAttribute ExhibitionRequestDto requestDto,
                                RedirectAttributes redirectAttributes) {
        exhibitionService.createExhibition(requestDto);
        redirectAttributes.addFlashAttribute("successMessage", "전시회가 성공적으로 등록되었습니다.");
        return "redirect:/admin";
    }

    @GetMapping("/{exhibitionId}/edit")
    public String showEditExhibitionForm(@PathVariable Long exhibitionId, Model model) {
        ExhibitionResponseDto exhibition = exhibitionService.getExhibitionById(exhibitionId);
        ExhibitionRequestDto requestDto = new ExhibitionRequestDto();
        requestDto.setTitle(exhibition.getTitle());
        model.addAttribute("exhibitionRequestDto", requestDto);
        model.addAttribute("exhibitionId", exhibitionId);
        model.addAttribute("currentPage", "dashboard");
        return "admin/exhibition-edit-form";
    }

    @PostMapping("/{exhibitionId}/edit")
    public String updateExhibition(@PathVariable Long exhibitionId,
                                   @ModelAttribute ExhibitionRequestDto requestDto,
                                   RedirectAttributes redirectAttributes) {
        exhibitionService.updateExhibition(exhibitionId, requestDto);
        redirectAttributes.addFlashAttribute("successMessage", "전시회 정보가 수정되었습니다.");
        return "redirect:/admin";
    }

    @GetMapping("/{exhibitionId}/delete")
    public String deleteExhibition(@PathVariable Long exhibitionId,
                                   RedirectAttributes redirectAttributes) {
        exhibitionService.deleteExhibition(exhibitionId);
        redirectAttributes.addFlashAttribute("successMessage", "전시회가 삭제되었습니다.");
        return "redirect:/admin";
    }
}
