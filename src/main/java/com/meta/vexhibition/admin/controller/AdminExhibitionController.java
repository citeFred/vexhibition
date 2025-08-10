package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.dto.ExhibitionRequestDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/exhibitions")
@RequiredArgsConstructor
public class AdminExhibitionController {

    private final ExhibitionService exhibitionService;

    @GetMapping("/new")
    public String showAddExhibitionForm(Model model) {
        model.addAttribute("exhibitionRequestDto", new ExhibitionRequestDto());
        return "admin/exhibition-form";
    }

    @PostMapping
    public String addExhibition(@ModelAttribute ExhibitionRequestDto requestDto) {
        exhibitionService.createExhibition(requestDto);
        return "redirect:/admin";
    }

    @GetMapping("/{exhibitionId}/delete")
    public String deleteExhibition(@PathVariable Long exhibitionId) {
        exhibitionService.deleteExhibition(exhibitionId);

        return "redirect:/admin";
    }
}