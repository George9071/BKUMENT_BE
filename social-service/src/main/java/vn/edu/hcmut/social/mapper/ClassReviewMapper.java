package vn.edu.hcmut.social.mapper;

import org.mapstruct.Mapper;

import vn.edu.hcmut.social.dto.response.ClassReviewResponse;
import vn.edu.hcmut.social.entity.ClassReview;

@Mapper(componentModel = "spring")
public interface ClassReviewMapper {
    ClassReviewResponse toResponse(ClassReview review);
}
