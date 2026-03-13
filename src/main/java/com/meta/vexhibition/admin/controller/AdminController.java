package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ExhibitionService exhibitionService;

    @GetMapping
    public String adminMain(Model model) {
        List<ExhibitionResponseDto> exhibitions = exhibitionService.getExhibitions();

        long totalProductions = exhibitions.stream()
                .mapToLong(e -> e.getProductions().size())
                .sum();
        long totalFiles = exhibitions.stream()
                .flatMap(e -> e.getProductions().stream())
                .mapToLong(p -> p.getFiles().size())
                .sum();

        model.addAttribute("exhibitions", exhibitions);
        model.addAttribute("totalExhibitions", exhibitions.size());
        model.addAttribute("totalProductions", totalProductions);
        model.addAttribute("totalFiles", totalFiles);
        model.addAttribute("currentPage", "dashboard");

        return "admin/index";
    }

    @GetMapping("/docent-test")
    public String docentTest(Model model) {
        List<ExhibitionResponseDto> exhibitions = exhibitionService.getExhibitions();
        model.addAttribute("exhibitions", exhibitions);
        model.addAttribute("currentPage", "docent-test");
        return "admin/docent-test";
    }

    @GetMapping("/rag")
    public String ragManagement(Model model) {
        List<ExhibitionResponseDto> exhibitions = exhibitionService.getExhibitions();

        long totalProductions = exhibitions.stream()
                .mapToLong(e -> e.getProductions().size())
                .sum();

        model.addAttribute("exhibitions", exhibitions);
        model.addAttribute("totalProductions", totalProductions);
        model.addAttribute("currentPage", "rag");

        return "admin/rag";
    }
}
