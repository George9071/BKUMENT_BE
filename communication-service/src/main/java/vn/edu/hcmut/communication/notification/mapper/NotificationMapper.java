package vn.edu.hcmut.communication.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.communication.notification.dto.response.NotificationResponse;
import vn.edu.hcmut.communication.notification.entity.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(source = "createdDate", target = "timestamp")
    NotificationResponse toResponse(Notification notification);
}
