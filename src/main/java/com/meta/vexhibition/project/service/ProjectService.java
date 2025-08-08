package com.meta.vexhibition.project.service;

import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.exhibition.repository.ExhibitionRepository;
import com.meta.vexhibition.file.service.FileService;
import com.meta.vexhibition.project.domain.Project;
import com.meta.vexhibition.project.dto.ProjectRequestDto;
import com.meta.vexhibition.project.dto.ProjectResponseDto;
import com.meta.vexhibition.project.dto.ProjectUpdateRequestDto;
import com.meta.vexhibition.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final FileService fileService;

    @Transactional
    public ProjectResponseDto createProject(Long exhibitionId, ProjectRequestDto projectRequestDto, List<MultipartFile> files) {
        Exhibition exhibition = getValidExhibition(exhibitionId);

        Project project = new Project(
                projectRequestDto.getTitle(),
                projectRequestDto.getContent(),
                exhibition
        );
        Project savedProject = projectRepository.save(project);

        if (files != null && !files.isEmpty()) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file != null && !file.isEmpty()) {
                    fileService.uploadFile(savedProject, file, i);
                }
            }
        }

        return new ProjectResponseDto(savedProject);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getProjects(Pageable pageable) {
        Page<Project> projectPage = projectRepository.findAll(pageable);
        return projectPage.map(ProjectResponseDto::new);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponseDto> getProjectsByExhibitionId(Long exhibitionId, Pageable pageable) {
        getValidExhibition(exhibitionId);
        Page<Project> projectPage = projectRepository.findByExhibitionId(exhibitionId, pageable);
        return projectPage.map(ProjectResponseDto::new);
    }

    @Transactional(readOnly = true)
    public ProjectResponseDto getProjectById(Long exhibitionId, Long projectId) {
        Project project = getValidExhibitionAndProject(exhibitionId, projectId);
        return new ProjectResponseDto(project);
    }

    @Transactional
    public ProjectResponseDto updateProject(Long exhibitionId, Long projectId, ProjectUpdateRequestDto projectUpdateRequestDto) {
        Project project = getValidExhibitionAndProject(exhibitionId, projectId);

        project.update(
                projectUpdateRequestDto.getTitle(),
                projectUpdateRequestDto.getContent()
        );
        return new ProjectResponseDto(project);
    }

    @Transactional
    public void deleteProject(Long exhibitionId, Long projectId) {
        Project project = getValidExhibitionAndProject(exhibitionId, projectId);
        projectRepository.delete(project);
    }

    public Exhibition getValidExhibition(Long exhibitionId) {
        return exhibitionRepository.findById(exhibitionId).orElseThrow(() ->
                new IllegalArgumentException("해당 전시회를 찾을 수 없습니다. Exhibition ID: " + exhibitionId)
        );
    }

    public Project getValidExhibitionAndProject(Long exhibitionId, Long projectId) {
        getValidExhibition(exhibitionId);

        return projectRepository.findByIdAndExhibitionId(projectId, exhibitionId).orElseThrow(() ->
                new IllegalArgumentException("해당 전시회(ID: " + exhibitionId + ")에서 작품(ID: " + projectId + ")을 찾을 수 없습니다.")
        );
    }
}