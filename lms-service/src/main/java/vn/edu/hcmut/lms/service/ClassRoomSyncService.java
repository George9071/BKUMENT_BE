package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.sync.ClassRoomSyncRequest;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClassRoomSyncService {
    GraphSyncService syncService;

    public void synchronization(ClassRoom classroom){
        try {
            var request = ClassRoomSyncRequest.builder()
                    .id(classroom.getId())
                    .name(classroom.getName())
                    .status(classroom.getStatus() != null ? classroom.getStatus().name() : null)
                    .format(classroom.getFormat() != null ? classroom.getFormat().name() : null)
                    .topicId(classroom.getTopic() != null ? classroom.getTopic().getId() : null)
                    .build();
            syncService.syncClassRoom(request);
        } catch (Exception e) {
            log.error("Failed to sync classroom {} to Neo4j.", classroom.getId(), e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }

    public void remove(String classId) {
        try {
            syncService.deleteClassRoom(classId);
        } catch (Exception e) {
            log.error("Failed to delete classroom {} from Neo4j.", classId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
            // TODO: Kafka/Message Queue here if require integrity.
        }
    }
}
