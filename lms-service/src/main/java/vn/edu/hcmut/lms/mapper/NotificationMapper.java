package vn.edu.hcmut.lms.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.lms.dto.response.NotificationResponse;
import vn.edu.hcmut.lms.entity.ClassNotification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(target = "id", expression = "java(String.format(\"#%05d\", notification.getId()))")
    @Mapping(target = "classId", source = "classRoom.id")
    @Mapping(target = "className", source = "classRoom.name")
    NotificationResponse toResponse(ClassNotification notification);
}
