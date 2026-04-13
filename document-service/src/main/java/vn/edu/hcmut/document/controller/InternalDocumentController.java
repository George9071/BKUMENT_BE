package vn.edu.hcmut.document.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.service.DocumentService;

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
}
