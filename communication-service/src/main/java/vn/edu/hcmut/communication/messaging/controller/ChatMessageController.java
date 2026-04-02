package vn.edu.hcmut.communication.messaging.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.communication.messaging.dto.request.ChatMessageRequest;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.messaging.dto.response.ChatMessageResponse;
import vn.edu.hcmut.communication.messaging.service.ChatMessageService;

@RestController
@RequiredArgsConstructor
@RequestMapping("messages")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageController {
    ChatMessageService chatMessageService;

    @PostMapping("/create")
    APIResponse<ChatMessageResponse> create(
            @RequestBody @Valid ChatMessageRequest request) {
        return APIResponse.<ChatMessageResponse>builder()
                .result(chatMessageService.create(request))
                .build();
    }

    @GetMapping
    APIResponse<Page<ChatMessageResponse>> getMessages(
            @RequestParam("conversationId") String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<ChatMessageResponse>>builder()
                .result(chatMessageService.getMessages(conversationId, pageable))
                .build();
    }
}
