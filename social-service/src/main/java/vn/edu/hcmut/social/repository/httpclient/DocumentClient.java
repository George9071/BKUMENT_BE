package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "document-service", url = "${app.services.document}")
public interface DocumentClient {
    @GetMapping("/internal/documents/{id}/owner")
    String getOwnerId(@PathVariable String id);
}
