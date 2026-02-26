package vn.edu.hcmut.communication.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.communication.dto.request.ConversationRequest;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.dto.response.ConversationResponse;
import vn.edu.hcmut.communication.service.ConversationService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {

    ConversationService conversationService;

    @PostMapping("/create")
    APIResponse<ConversationResponse> createConversation(@RequestBody @Valid ConversationRequest request) {
        return APIResponse.<ConversationResponse> builder()
                .result(conversationService.create(request))
                .build();
    }

    @GetMapping("/my-conversations")
    APIResponse<List<ConversationResponse>> myConversations() {
        return APIResponse.<List<ConversationResponse>> builder()
                .result(conversationService.myConversations())
                .build();
    }
}
