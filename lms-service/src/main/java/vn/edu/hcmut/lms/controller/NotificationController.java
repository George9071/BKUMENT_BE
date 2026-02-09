package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.request.NotificationRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.NotificationResponse;
import vn.edu.hcmut.lms.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    NotificationService notificationService;

    @PostMapping("/class/{classId}")
    public APIResponse<NotificationResponse> createNotification(
            @PathVariable String classId,
            @RequestBody NotificationRequest request) {
        return APIResponse.<NotificationResponse>builder()
                .result(notificationService.createNotification(classId, request))
                .build();
    }

    @GetMapping("/class/{classId}")
    public APIResponse<List<NotificationResponse>> getClassNotifications(@PathVariable String classId) {
        return APIResponse.<List<NotificationResponse>>builder()
                .result(notificationService.getNotifications(classId))
                .build();
    }
}
