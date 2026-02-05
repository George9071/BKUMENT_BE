package vn.edu.hcmut.lms.mapper;

import org.mapstruct.*;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.ClassSchedule;

@Mapper(componentModel = "spring")
public interface ClassRoomMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "tutor", ignore = true)
    @Mapping(target = "topic", ignore = true)
    ClassRoom toClassRoom(ClassRoomCreationRequest request);

    ClassSchedule toScheduleEntity(ClassRoomCreationRequest.ScheduleRequest request);

    @Mapping(target = "tutorId", source = "tutor.id")
    @Mapping(target = "tutorName", source = "tutor.name")
    @Mapping(target = "tutorAvatar", source = "tutor.avatar")
    @Mapping(target = "topicName", source = "topic.name")
    @Mapping(target = "subjectName", source = "topic.subject.name")
    ClassRoomResponse toResponse(ClassRoom classRoom);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tutor", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "topic", ignore = true)
    void updateClass(@MappingTarget ClassRoom classRoom, ClassRoomUpdateRequest request);

    @AfterMapping
    default void linkSchedules(@MappingTarget ClassRoom classRoom) {
        if (classRoom.getSchedules() != null) {
            classRoom.getSchedules().forEach(schedule -> schedule.setClassRoom(classRoom));
        }
    }
}
