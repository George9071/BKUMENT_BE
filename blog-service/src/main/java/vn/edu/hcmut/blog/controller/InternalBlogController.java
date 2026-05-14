package vn.edu.hcmut.blog.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.blog.dto.response.APIResponse;
import vn.edu.hcmut.blog.dto.response.ResourceContentSnapshot;
import vn.edu.hcmut.blog.service.PostService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/blogs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalBlogController {
    PostService postService;

    @GetMapping("/{id}/owner")
    public String getOwnerId(@PathVariable String id) {
        return postService.getOwnerId(id);
    }

    @GetMapping("/{id}/exists")
    public boolean existsById(@PathVariable String id) {
        return postService.existsById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteBlog(@PathVariable String id) {
        postService.deleteBlog(id);
    }

    @DeleteMapping("/owner/{ownerId}")
    public void deleteByOwnerId(@PathVariable String ownerId) {
        postService.deleteByOwnerId(ownerId);
    }

    @PostMapping("/metadata-batch")
    public APIResponse<Map<String, ResourceContentSnapshot>> getMetadataBatch(
            @RequestBody @NotNull List<String> blogIds) {

        Map<String, ResourceContentSnapshot> result = postService.getMetadataBatch(blogIds);
        return APIResponse.<Map<String, ResourceContentSnapshot>>builder()
                .code(1000)
                .result(result)
                .build();
    }
}
