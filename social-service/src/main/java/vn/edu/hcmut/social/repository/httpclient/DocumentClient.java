package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ResourceContentSnapshot;

import java.util.List;
import java.util.Map;

@FeignClient(name = "document-service", url = "${app.services.document}")
public interface DocumentClient {
    @GetMapping("/internal/documents/{id}/owner")
    String getOwnerId(@PathVariable String id);

    @GetMapping("/internal/documents/{id}/exists")
    boolean exists(@PathVariable String id);

    @DeleteMapping("/internal/documents/{id}")
    void delete(@PathVariable String id);

    @PostMapping("internal/documents/metadata-batch")
    APIResponse<Map<String, ResourceContentSnapshot>> getDocumentMetadataBatch(
            @RequestBody List<String> documentIds);
}
