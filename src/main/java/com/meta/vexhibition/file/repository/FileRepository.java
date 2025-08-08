package com.meta.vexhibition.file.repository;

import com.meta.vexhibition.file.domain.File;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File, Long> {
}