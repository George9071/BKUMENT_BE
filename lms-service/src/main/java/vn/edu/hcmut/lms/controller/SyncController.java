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
    @PostMapping("/partial")

    public String syncSpecificData() {
        syncService.syncSubjects();
        return "Sync process triggered for specific subjects/topics.";
    }
}
