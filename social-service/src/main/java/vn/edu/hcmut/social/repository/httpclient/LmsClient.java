package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import vn.edu.hcmut.social.dto.request.internal.InternalTutorRatingRequest;

@FeignClient(name = "lms-service", url = "${app.services.lms}")
public interface LmsClient {
    @PostMapping("/internal/tutors/{profileId}/rating")
    void updateTutorRating(
            @PathVariable("profileId") String profileId, @RequestBody InternalTutorRatingRequest request);
}
