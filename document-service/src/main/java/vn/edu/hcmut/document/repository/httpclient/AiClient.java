package vn.edu.hcmut.document.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmut.document.configuration.FeignMultipartConfig;
import vn.edu.hcmut.document.dto.response.DocumentProcessResponse;

@FeignClient(name = "ai-service", url = "${app.services.ai}", configuration = FeignMultipartConfig.class)
public interface AiClient {

    @PostMapping(value = "/internal/process-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DocumentProcessResponse processDocument(@RequestPart("file") MultipartFile file);
}
