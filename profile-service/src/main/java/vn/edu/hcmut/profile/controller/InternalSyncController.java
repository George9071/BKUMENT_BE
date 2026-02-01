package vn.edu.hcmut.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.profile.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.profile.service.SyncService;

@RestController
@RequestMapping("/internal/metadata")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalSyncController {
    SyncService syncService;

    @PostMapping("/subjects")
    public void syncSubjects(@RequestBody List<SubjectSyncRequest> requests) {
        syncService.syncSubjects(requests);
    }

    @PostMapping("/topics")
    public void syncTopics(@RequestBody List<TopicSyncRequest> requests) {
        syncService.syncTopics(requests);
    }
}
