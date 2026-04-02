package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "blog-service", url = "${app.services.blog}")
public interface BlogClient {
    @GetMapping("/internal/blogs/{id}/owner")
    String getOwnerId(@PathVariable String id);
}
