package vn.edu.hcmut.blog.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.blog.service.PostService;

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
}
