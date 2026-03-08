package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.request.NotificationRequest;
import vn.edu.hcmut.lms.dto.response.NotificationResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.entity.ClassNotification;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.NotificationMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
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
    EnrollmentRepository enrollmentRepository;
    NotificationMapper notificationMapper;

    /**
     * Create a new announcement for the class (Only tutors of the class can create one)
     */
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse createNotification(String classId, NotificationRequest request) {
        String userId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(userId))
            throw new AppException(ErrorCode.ACCESS_DENIED);

        ClassNotification notification = ClassNotification.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .sentAt(LocalDateTime.now())
                .classRoom(classRoom)
                .build();

        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    /**
     * Get the class announcement list
     * Only tutor and members of the class can view it.
     */
    public PageResponse<NotificationResponse> getNotifications(String classId, int page, int size) {
        String userId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        boolean isTutor = classRoom.getTutor().getId().equals(userId);

        boolean isMember = enrollmentRepository.findByClassRoomIdAndStudentProfileId(classId, userId)
                .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.APPROVED)
                .orElse(false);

        if (!isTutor && !isMember) {
            log.warn("User {} attempted to view notifications for class {} without permission", userId, classId);
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<ClassNotification> notificationPage = notificationRepository
                .findByClassRoomIdOrderBySentAtDesc(classId, pageable);

        List<NotificationResponse> responses = notificationPage.getContent().stream()
                .map(notificationMapper::toResponse)
                .toList();

        return PageResponse.<NotificationResponse>builder()
                .currentPage(page)
                .totalPages(notificationPage.getTotalPages())
                .pageSize(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .data(responses)
                .build();
    }

    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }
}
