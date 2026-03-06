package vn.edu.hcmut.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.profile.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.profile.service.SyncService;

@RestController
@RequestMapping("/internal/metadata")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InternalSyncController {
    SyncService syncService;

    /**
     * Synchronizes the list of Subjects into the system.
     * Usually called once when the system is initialized or when there is a large data set that needs patching.
     *
     * @param requests  List of subject data to be synchronized.
     */
    @PostMapping("/subjects")
    public void syncSubjects(@RequestBody List<SubjectSyncRequest> requests) {
        syncService.syncSubjects(requests);
        log.info("Successfully synced {} subjects.", requests.size());
    }

    /**
     * Synchronize the list of Topics and associate them with their corresponding Subjects.
     * NOTE: Ensure the syncSubjects API is running beforehand to avoid the Topic not finding its parent Subject.
     *
     * @param requests  List of topic data to synchronize.
     */
    @PostMapping("/topics")
    public void syncTopics(@RequestBody List<TopicSyncRequest> requests) {
        syncService.syncTopics(requests);
        log.info("Successfully synced {} topics.", requests.size());
    }
}
