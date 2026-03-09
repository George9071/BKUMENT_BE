package vn.edu.hcmut.lms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.lms.service.SyncService;

@RestController
@RequestMapping("/admin/sync")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;
    @PostMapping("/subject-topic")
    public String syncData() {
        syncService.syncAllMetadata();
        return "Sync process triggered";
    }

    @PostMapping("/subject-tutor")
    public String syncTutorSubject() {
        syncService.syncAllTutorSubjects();
        return "Sync process triggered";
    }

    @PostMapping("/class-topic")
    public String syncClassTopic() {
        syncService.syncAllClasses();
        return "Sync process triggered";
    }
}
