package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.request.NotificationRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.NotificationResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Notification management", description = "APIs for managing and fetching class notifications")
public class NotificationController {

    NotificationService notificationService;

    @Operation(summary = "Create a class notification",
            description = "Creates a notification for a specific class. Only the class tutor can perform this action.")
    @PostMapping("/class/{classId}")
    public APIResponse<NotificationResponse> createNotification(
            @Parameter(description = "ID of the class") @PathVariable String classId,
            @RequestBody NotificationRequest request) {
        return APIResponse.<NotificationResponse>builder()
                .result(notificationService.createNotification(classId, request))
                .build();
    }

    @Operation(summary = "Get class notifications",
            description = "Retrieves a list of notifications for a specific class. Accessible by the tutor and approved students.")
    @GetMapping("/class/{classId}")
    public APIResponse<PageResponse<NotificationResponse>> getClassNotifications(
            @Parameter(description = "ID of the class") @PathVariable String classId,
            @Parameter(description = "Page number (1-based index)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<NotificationResponse>>builder()
                .result(notificationService.getNotifications(classId, page, size))
                .build();
    }
}
