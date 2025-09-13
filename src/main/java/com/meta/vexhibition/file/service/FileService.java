package com.meta.vexhibition.file.service;

import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.file.repository.FileRepository;
import com.meta.vexhibition.production.domain.Production;
import lombok.RequiredArgsConstructor;
import org.apache.commons.imaging.Imaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.cloudfront.domain}")
    private String cloudFrontDomain;

    /**
     * 단일 정적 이미지 파일(JPG, PNG 등)을 업로드합니다.
     */
    @Transactional
    public void uploadFile(Production production, MultipartFile multipartFile, int order) {
        if (isInvalidFile(multipartFile)) return;

        String originalFileName = multipartFile.getOriginalFilename();
        String storedFileName = createStoredFileName(originalFileName);

        try {
            uploadToS3(storedFileName, multipartFile.getBytes(), multipartFile.getContentType());
            saveFileEntity(production, originalFileName, storedFileName, order);
        } catch (IOException e) {
            throw new RuntimeException("파일을 바이트 배열로 변환하는 데 실패했습니다.", e);
        }
    }

    /**
     * GIF 파일을 여러 개의 PNG 프레임으로 분할하여 업로드합니다.
     * @return 생성된 프레임의 개수를 반환합니다.
     */
    @Transactional
    public int uploadGifAsFrames(Production production, MultipartFile gifFile, int startOrder) {
        if (isInvalidFile(gifFile)) return 0;

        try {
            // [수정된 부분] InputStream을 byte[]로 변환하여 메소드에 전달합니다.
            final List<BufferedImage> frames = Imaging.getAllBufferedImages(gifFile.getInputStream().readAllBytes());

            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                // 각 프레임을 PNG 바이트 배열로 변환
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(frame, "png", baos);
                byte[] pngBytes = baos.toByteArray();

                // 프레임별 파일 이름 생성 (예: uuid-original_frame_0.png)
                String originalFrameName = stripExtension(gifFile.getOriginalFilename()) + "_frame_" + i + ".png";
                String storedFrameName = createStoredFileName(originalFrameName);

                // 각 PNG 프레임을 S3에 업로드
                uploadToS3(storedFrameName, pngBytes, "image/png");

                // 각 프레임에 대한 파일 정보를 DB에 저장 (순서가 중요)
                saveFileEntity(production, originalFrameName, storedFrameName, startOrder + i);
            }
            return frames.size(); // 업로드된 프레임 수 반환
        } catch (Exception e) {
            throw new RuntimeException("GIF 파일을 PNG 프레임으로 분할하는 데 실패했습니다.", e);
        }
    }

    public void deleteFile(String storedFileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(storedFileName)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            throw new RuntimeException("S3 파일 삭제에 실패했습니다.", e);
        }
    }

    // --- 내부 헬퍼 메소드 ---

    private void uploadToS3(String key, byte[] data, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
    }

    private void saveFileEntity(Production production, String originalFileName, String storedFileName, int order) {
        String finalUrl = "https://" + cloudFrontDomain + "/" + storedFileName;
        File fileEntity = new File(originalFileName, storedFileName, finalUrl, production, order);
        fileRepository.save(fileEntity);
    }

    private boolean isInvalidFile(MultipartFile multipartFile) {
        return multipartFile == null || multipartFile.isEmpty();
    }

    private String createStoredFileName(String originalFilename) {
        return UUID.randomUUID().toString() + "-" + originalFilename;
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return null;
        int pos = fileName.lastIndexOf(".");
        if (pos == -1) return fileName;
        return fileName.substring(0, pos);
    }
}