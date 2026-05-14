package vn.edu.hcmut.document.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.dto.response.APIResponse;
import vn.edu.hcmut.document.dto.response.ResourceContentSnapshot;
import vn.edu.hcmut.document.service.DocumentService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalDocumentController {
    DocumentService documentService;

    @GetMapping("/{id}/owner")
    public String getOwnerId(@PathVariable String id) {
        return documentService.getOwnerId(id);
    }

    @GetMapping("/{id}/exists")
    public boolean existsById(@PathVariable String id) {
        return documentService.existsById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable String id) {
        documentService.deleteDocument(id);
    }

    @DeleteMapping("/owner/{ownerId}")
    public void deleteByOwnerId(@PathVariable String ownerId) {
        documentService.deleteByOwnerId(ownerId);
    }

    @PostMapping("/metadata-batch")
    public APIResponse<Map<String, ResourceContentSnapshot>> getMetadataBatch(
            @RequestBody @NotNull List<String> documentIds) {
        return APIResponse.<Map<String, ResourceContentSnapshot>>builder()
                .code(1000)
                .result(documentService.getMetadataBatch(documentIds))
                .build();
    }
}
