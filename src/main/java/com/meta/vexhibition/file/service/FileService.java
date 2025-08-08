package com.meta.vexhibition.file.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.file.repository.FileRepository;
import com.meta.vexhibition.project.domain.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final S3Client s3Client; // Spring이 자동으로 생성 및 주입

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudFrontDomain;

    @Transactional
    public void uploadFile(Project project, MultipartFile multipartFile, int order) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }

        String originalFileName = multipartFile.getOriginalFilename();
        String storedFileName = createStoredFileName(originalFileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedFileName)
                    .contentType(multipartFile.getContentType())
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(
                    multipartFile.getInputStream(),
                    multipartFile.getSize()
            );

            s3Client.putObject(putObjectRequest, requestBody);

        } catch (IOException e) {
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }

//        URL s3Url = s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(bucket).key(storedFileName).build());
        String finalUrl = "https://" + cloudFrontDomain + "/" + storedFileName;

//        File fileEntity = new File(originalFileName, storedFileName, s3Url.toString(), project);
        File fileEntity = new File(originalFileName, storedFileName, finalUrl, project, order);
        fileRepository.save(fileEntity);
    }

    private String createStoredFileName(String originalFilename) {
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + fileExtension;
    }
}