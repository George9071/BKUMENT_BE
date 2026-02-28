package vn.edu.hcmut.identity.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "lms-service", url = "${app.services.lms}")
public interface LmsClient {
    @DeleteMapping("/internal/tutors/{profileId}")
    void deleteTutor(@PathVariable("profileId") String profileId);
}
