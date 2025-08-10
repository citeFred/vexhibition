package com.meta.vexhibition.project.controller;

import com.meta.vexhibition.project.dto.ProjectRequestDto;
import com.meta.vexhibition.project.dto.ProjectResponseDto;
import com.meta.vexhibition.project.dto.ProjectUpdateRequestDto;
import com.meta.vexhibition.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping("/exhibitions/{exhibitionId}/projects")
    public ResponseEntity<ProjectResponseDto> createProjectForExhibition(
            @PathVariable Long exhibitionId,
            @ModelAttribute ProjectRequestDto projectRequestDto,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        ProjectResponseDto createdProject = projectService.createProject(exhibitionId, projectRequestDto, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProject);
    }

    @GetMapping("/exhibitions/{exhibitionId}/projects")
    public ResponseEntity<Page<ProjectResponseDto>> getProjectsByExhibitionId(
            @PathVariable Long exhibitionId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProjectResponseDto> projectResponseDtoPage = projectService.getProjectsByExhibitionId(exhibitionId, pageable);
        return ResponseEntity.ok(projectResponseDtoPage);
    }

    @GetMapping("/projects")
    public ResponseEntity<Page<ProjectResponseDto>> getProjects(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProjectResponseDto> projectResponseDtoPage = projectService.getProjects(pageable);
        return ResponseEntity.ok(projectResponseDtoPage);
    }

    @GetMapping("/exhibitions/{exhibitionId}/projects/{id}")
    public ResponseEntity<ProjectResponseDto> getProjectById(
            @PathVariable Long exhibitionId,
            @PathVariable Long id) {
        ProjectResponseDto projectResponseDto = projectService.getProjectById(exhibitionId, id);
        return ResponseEntity.ok(projectResponseDto);
    }

    @PutMapping("/exhibitions/{exhibitionId}/projects/{id}")
    public ResponseEntity<ProjectResponseDto> updateProject(
            @PathVariable Long exhibitionId,
            @PathVariable Long id,
            @ModelAttribute ProjectUpdateRequestDto projectUpdateRequestDto,
            @RequestParam(value = "addFiles", required = false) List<MultipartFile> addFiles) {
        ProjectResponseDto updatedProject = projectService.updateProject(exhibitionId, id, projectUpdateRequestDto, addFiles);
        return ResponseEntity.ok(updatedProject);
    }

    @DeleteMapping("/exhibitions/{exhibitionId}/projects/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long exhibitionId,
            @PathVariable Long id) {
        projectService.deleteProject(exhibitionId, id);
        return ResponseEntity.noContent().build();
    }
}