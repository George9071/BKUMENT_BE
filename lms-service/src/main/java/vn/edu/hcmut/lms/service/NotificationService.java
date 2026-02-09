package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.request.NotificationRequest;
import vn.edu.hcmut.lms.dto.response.NotificationResponse;
import vn.edu.hcmut.lms.entity.ClassNotification;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.NotificationMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class NotificationService {

    NotificationRepository notificationRepository;
    ClassRoomRepository classRoomRepository;
    NotificationMapper notificationMapper;

    public NotificationResponse createNotification(String classId, NotificationRequest request) {
        String userId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(userId))
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);

        ClassNotification notification = ClassNotification.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .sentAt(LocalDateTime.now())
                .classRoom(classRoom)
                .build();

        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    public List<NotificationResponse> getNotifications(String classId) {
        // TODO: check if the user is a member of the class
        // Temporarily ignore the membership check logic to focus on the notification retrieval logic

        return notificationRepository.findByClassRoomIdOrderBySentAtDesc(classId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }
}
