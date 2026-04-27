package vn.edu.hcmut.identity.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "document-service", url = "${app.services.document}")
public interface DocumentClient {
    @DeleteMapping("/internal/documents/owner/{ownerId}")
    void deleteByOwnerId(@PathVariable("ownerId") String ownerId);
}
