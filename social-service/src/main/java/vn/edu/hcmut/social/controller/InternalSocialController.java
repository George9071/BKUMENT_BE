package vn.edu.hcmut.social.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.RatingRepository;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InternalSocialController {
    RatingRepository ratingRepository;
    CommentRepository commentRepository;

    @DeleteMapping("/resource/{resourceId}")
    public void deleteSocialByResourceId(@PathVariable String resourceId) {
        log.info("[INTERNAL-CLEANUP] Starting social data cleanup for resource: {}", resourceId);

        int deletedRatings = ratingRepository.deleteByResourceId(resourceId);
        int deletedComments = commentRepository.deleteByResourceId(resourceId);

        log.info(
                "[INTERNAL-CLEANUP] Resource {}: removed {} ratings and {} comments",
                resourceId,
                deletedRatings,
                deletedComments);
    }
}
