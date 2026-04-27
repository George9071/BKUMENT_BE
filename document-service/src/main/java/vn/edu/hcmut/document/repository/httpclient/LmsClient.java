package vn.edu.hcmut.document.repository.httpclient;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.hcmut.document.dto.response.APIResponse;

@FeignClient(name = "lms-service", url = "${app.services.lms}")
public interface LmsClient {

    @GetMapping("/classes/internal/batch")
    APIResponse<Map<String, String>> getClassNamesBatch(@RequestParam("ids") List<String> ids);

    @GetMapping("/subjects/topics/internal/batch")
    APIResponse<Map<String, String>> getTopicNamesBatch(@RequestParam("ids") List<String> ids);
}
