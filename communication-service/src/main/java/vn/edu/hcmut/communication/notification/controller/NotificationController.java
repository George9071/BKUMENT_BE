package vn.edu.hcmut.communication.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.notification.dto.response.NotificationResponse;
import vn.edu.hcmut.communication.dto.response.PageResponse;
import vn.edu.hcmut.communication.notification.service.NotificationService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Notification API", description = "Manage user notification systems.")
public class NotificationController {
    NotificationService notificationService;

    @Operation(summary = "Get my notification list (with pagination)")
    @GetMapping
    public APIResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = getProfileIdFromToken();
        var result = notificationService.getMyNotifications(userId, page, size);

        return APIResponse.<PageResponse<NotificationResponse>>builder()
                .result(result)
                .build();
    }

    @Operation(summary = "Count the number of unread notifications")
    @GetMapping("/unread-count")
    public APIResponse<Long> getUnreadCount() {
        String userId = getProfileIdFromToken();
        long count = notificationService.getUnreadCount(userId);

        return APIResponse.<Long>builder()
                .result(count)
                .build();
    }

    @Operation(summary = "Mark a notification as read.")
    @PatchMapping("/{id}/read")
    public APIResponse<Void> markAsRead(@PathVariable("id") String id) {
        String userId = getProfileIdFromToken();
        notificationService.markAsRead(id, userId);

        return APIResponse.<Void>builder()
                .message("Notification marked as read")
                .build();
    }

    @Operation(summary = "Mark all notifications as read.")
    @PatchMapping("/read-all")
    public APIResponse<Void> markAllAsRead() {
        String userId = getProfileIdFromToken();
        notificationService.markAllAsRead(userId);

        return APIResponse.<Void>builder()
                .message("All notifications marked as read")
                .build();
    }

    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }
}
