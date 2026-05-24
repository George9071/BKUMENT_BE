package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import vn.edu.hcmut.social.dto.request.internal.InternalClassRatingRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ClassRoomResponse;

@FeignClient(name = "lms-service", url = "${app.services.lms}")
public interface LmsClient {
    @GetMapping("/classes/{classId}")
    APIResponse<ClassRoomResponse> getClassRoom(@PathVariable("classId") String classId);

    @PostMapping("/internal/classes/{classId}/rating")
    void updateClassRating(
            @PathVariable("classId") String classId, @RequestBody InternalClassRatingRequest request);
}
