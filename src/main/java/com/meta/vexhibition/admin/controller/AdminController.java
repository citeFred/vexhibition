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
@RequestMapping("/admin") // 모든 관리자 페이지는 /admin 경로로 시작하도록 설정
@RequiredArgsConstructor
public class AdminController {

    private final ExhibitionService exhibitionService;

    @GetMapping
    public String adminMain(Model model) {
        List<ExhibitionResponseDto> exhibitions = exhibitionService.getExhibitions();
        
        model.addAttribute("exhibitions", exhibitions);

        return "admin/index";
    }
}