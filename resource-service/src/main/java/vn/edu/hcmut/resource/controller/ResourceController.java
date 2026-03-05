package vn.edu.hcmut.resource.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.resource.dto.response.APIResponse;
import vn.edu.hcmut.resource.dto.response.FileInfoResponse;
import vn.edu.hcmut.resource.dto.response.PresignResponse;
import vn.edu.hcmut.resource.dto.response.ResourceDownloadResponse;
import vn.edu.hcmut.resource.service.ResourceService;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceController {
    ResourceService resourceService;

    @GetMapping("")
    public String healthCheck() {
        return "Resource Service is running";
    }

    @GetMapping("/list")
    public APIResponse<List<String>> listAllFiles() {
        return APIResponse.<List<String>>builder()
                .result(resourceService.listAllFiles())
                .message("Listed all files successfully")
                .build();
    }

    @GetMapping("/info/asset/{assetId}")
    public APIResponse<FileInfoResponse> getFileInfo(@PathVariable String assetId) {
        return APIResponse.<FileInfoResponse>builder()
                .result(resourceService.getFileInfo(assetId))
                .message("Get file info successfully")
                .build();
    }

    @GetMapping("/download/asset/{assetId}")
    public ResponseEntity<StreamingResponseBody> downloadByAssetId(@PathVariable String assetId) {
        ResourceDownloadResponse data = resourceService.downloadByAssetId(assetId);

        String fileName = (data.getFileName() == null || data.getFileName().isBlank()) ? "file" : data.getFileName();
        String encodedFileName =
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = data.getInputStream()) {
                is.transferTo(outputStream);
                outputStream.flush();
            } catch (IOException e) {
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .contentType(MediaType.parseMediaType(data.getContentType()))
                .contentLength(data.getFileSize())
                .body(responseBody);
    }

    @DeleteMapping("/asset/{assetId}")
    public APIResponse<String> deleteFile(@PathVariable String assetId) {
        resourceService.deleteFile(assetId);
        return APIResponse.<String>builder()
                .message("File deleted successfully")
                .build();
    }

    @GetMapping("/presign")
    public APIResponse<PresignResponse> getPresignUrl(@RequestParam(required = false) String fileName) {
        return APIResponse.<PresignResponse>builder()
                .result(resourceService.generatePresignedUrl(fileName))
                .message("Presign URL generated")
                .build();
    }
}
