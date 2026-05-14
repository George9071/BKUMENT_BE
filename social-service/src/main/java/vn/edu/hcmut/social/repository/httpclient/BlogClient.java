package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ResourceContentSnapshot;

import java.util.List;
import java.util.Map;

@FeignClient(name = "blog-service", url = "${app.services.blog}")
public interface BlogClient {
    @GetMapping("/internal/blogs/{id}/owner")
    String getOwnerId(@PathVariable String id);

    @GetMapping("/internal/blogs/{id}/exists")
    boolean exists(@PathVariable String id);

    @DeleteMapping("/internal/blogs/{id}")
    void delete(@PathVariable String id);

    @PostMapping("/internal/blogs/metadata-batch")
    APIResponse<Map<String, ResourceContentSnapshot>> getBlogMetadataBatch(
            @RequestBody List<String> blogIds);
}
