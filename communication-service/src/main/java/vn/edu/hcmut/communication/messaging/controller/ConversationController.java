package vn.edu.hcmut.communication.messaging.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;

import vn.edu.hcmut.communication.messaging.dto.request.ConversationMetadataRequest;
import vn.edu.hcmut.communication.messaging.dto.request.ConversationRequest;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.messaging.dto.response.ConversationResponse;
import vn.edu.hcmut.communication.messaging.service.ConversationService;

@RestController
@RequiredArgsConstructor
@RequestMapping("conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {

    ConversationService conversationService;

    @PostMapping("/create")
    APIResponse<ConversationResponse> createConversation(@RequestBody @Valid ConversationRequest request) {
        return APIResponse.<ConversationResponse>builder()
                .result(conversationService.create(request))
                .build();
    }

    @GetMapping("/my-conversations")
    public APIResponse<Page<ConversationResponse>> myConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        
        return APIResponse.<Page<ConversationResponse>>builder()
                .result(conversationService.myConversations(pageable))
                .message("Get conversations successfully") 
                .build();
    }

    @PutMapping("/{id}/metadata")
    public APIResponse<ConversationResponse> updateMetadata(
            @PathVariable String id,
            @RequestBody ConversationMetadataRequest request) {
        return APIResponse.<ConversationResponse>builder()
                .result(conversationService.updateMetadata(id, request))
                .build();
    }
}
