package vn.edu.hcmut.lms.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.Topic;

@Mapper(componentModel = "spring")
public interface SubjectMapper {
    TopicResponse toTopicResponse(Topic topic);

    @Mapping(target = "topics", source = "topics")
    SubjectResponse toSubjectResponse(Subject subject);
}
