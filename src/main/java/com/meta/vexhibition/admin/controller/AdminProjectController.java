package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import com.meta.vexhibition.project.dto.ProjectRequestDto;
import com.meta.vexhibition.project.dto.ProjectResponseDto;
import com.meta.vexhibition.project.dto.ProjectUpdateRequestDto;
import com.meta.vexhibition.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/admin/exhibitions/{exhibitionId}/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectService projectService;
    private final ExhibitionService exhibitionService;

    @GetMapping("/new")
    public String showAddProjectForm(@PathVariable Long exhibitionId, Model model) {
        model.addAttribute("projectRequestDto", new ProjectRequestDto());
        model.addAttribute("exhibitionId", exhibitionId);
        return "admin/project-form";
    }

    @PostMapping
    public String addProject(@PathVariable Long exhibitionId,
                             @ModelAttribute ProjectRequestDto projectRequestDto,
                             @RequestParam("files") List<MultipartFile> files) {
        projectService.createProject(exhibitionId, projectRequestDto, files);
        return "redirect:/admin";
    }

    @GetMapping
    public String getProjectList(@PathVariable Long exhibitionId, Model model) {
        ExhibitionResponseDto exhibition = exhibitionService.getExhibitionById(exhibitionId);
        model.addAttribute("exhibition", exhibition);

        Pageable pageable = Pageable.unpaged();
        Page<ProjectResponseDto> projectPage = projectService.getProjectsByExhibitionId(exhibitionId, pageable);
        model.addAttribute("projects", projectPage.getContent());

        return "admin/project-list";
    }

    @GetMapping("/{projectId}/edit")
    public String showEditProjectForm(@PathVariable Long exhibitionId,
                                      @PathVariable Long projectId, Model model) {
        ProjectResponseDto projectDto = projectService.getProjectById(exhibitionId, projectId);
        model.addAttribute("projectDto", projectDto);
        model.addAttribute("exhibitionId", exhibitionId);
        return "admin/project-edit-form";
    }

    @PostMapping("/{projectId}/edit")
    public String updateProject(@PathVariable Long exhibitionId,
                                @PathVariable Long projectId,
                                @ModelAttribute ProjectUpdateRequestDto requestDto,
                                @RequestParam("addFiles") List<MultipartFile> addFiles) {
        projectService.updateProject(exhibitionId, projectId, requestDto, addFiles);
        return "redirect:/admin/exhibitions/" + exhibitionId + "/projects";
    }

    @GetMapping("/{projectId}/delete")
    public String deleteProject(@PathVariable Long exhibitionId,
                                @PathVariable Long projectId) {
        projectService.deleteProject(exhibitionId, projectId);

        return "redirect:/admin/exhibitions/" + exhibitionId + "/projects";
    }
}