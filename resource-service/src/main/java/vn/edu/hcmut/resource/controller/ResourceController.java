package vn.edu.hcmut.resource.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.resource.dto.request.ResourceMetadataRequest;
import vn.edu.hcmut.resource.dto.response.*;
import vn.edu.hcmut.resource.entity.Resource;
import vn.edu.hcmut.resource.service.MinioService;
import vn.edu.hcmut.resource.service.ResourceService;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceController {
    ResourceService resourceService;
    MinioService minioService;

    @GetMapping("")
    public APIResponse<List<String>> getAllFiles() {
        return APIResponse.<List<String>>builder()
                .result(minioService.listAllFiles())
                .build();
    }

    @PostMapping("/metadata")
    public APIResponse<ResourceMetadataResponse> createResource(@RequestBody @Valid ResourceMetadataRequest request) {
        Resource resource = resourceService.createResource(request, "");

        return APIResponse.<ResourceMetadataResponse>builder()
                .result(ResourceMetadataResponse.builder()
                        .id(resource.getId())
                        .name(resource.getTitle())
                        .build())
                .message("Resource created successfully")
                .build();
    }

    @GetMapping("/download/{resourceId}")
    public ResponseEntity<InputStreamResource> downloadResource(@PathVariable String resourceId) {
        ResourceDownloadResponse data = resourceService.downloadResource(resourceId);

        String fileName = data.getFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "downloaded-file";
        }

        String encodedFileName =
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(data.getContentType()))
                .contentLength(data.getFileSize())
                .body(new InputStreamResource(data.getInputStream()));
    }

    @GetMapping("/{resourceId}")
    public APIResponse<FileInfoResponse> getResourceInfo(@PathVariable String resourceId) {
        return APIResponse.<FileInfoResponse>builder()
                .result(resourceService.getResourceFileInfo(resourceId))
                .message("Get info successfully")
                .build();
    }

    @GetMapping("/document/{resourceId}")
    public APIResponse<DocumentResponse> getDocumentInfo(@PathVariable String resourceId) {
        return APIResponse.<DocumentResponse>builder()
                .result(resourceService.getDocumentInfo(resourceId))
                .message("Get document successfully")
                .build();
    }

    @DeleteMapping("/delete/{resourceId}")
    public APIResponse<String> deleteResource(@PathVariable String resourceId) {
        resourceService.deleteResource(resourceId);
        return APIResponse.<String>builder().message("Deleted successfully").build();
    }

    @GetMapping("/presign")
    public APIResponse<PresignResponse> getPresignUrl(@RequestParam(required = false) String fileName) {
        return APIResponse.<PresignResponse>builder()
                .result(resourceService.generatePresignedUrl(fileName))
                .message("Presign URL generated")
                .build();
    }
}
