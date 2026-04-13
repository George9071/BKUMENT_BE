package vn.edu.hcmut.identity.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "blog-service", url = "${app.services.blog}")
public interface BlogClient {
    @DeleteMapping("/internal/blogs/owner/{ownerId}")
    void deleteByOwnerId(@PathVariable("ownerId") String ownerId);
}
