package vn.edu.hcmut.social.controller.internal;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ResourceCleanupResponse;
import vn.edu.hcmut.social.service.ResourceCleanupService;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalResourceController {
    ResourceCleanupService resourceCleanupService;

    @DeleteMapping("/resources/{resourceId}")
    public APIResponse<ResourceCleanupResponse> deleteResourceSocialData(
            @PathVariable @NotBlank String resourceId) {

        log.info("[INTERNAL-CLEANUP] Starting social data cleanup for resource: {}", resourceId);
        ResourceCleanupResponse result = resourceCleanupService.cleanupResource(resourceId);

        return APIResponse.<ResourceCleanupResponse>builder()
                .code(1000)
                .message("Resource social data cleaned up")
                .result(result)
                .build();
    }
}
