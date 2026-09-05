package com.gak.message.controller;

import com.gak.framework.response.*;
import com.gak.message.dto.*;
import com.gak.message.service.MessageService;
import com.gak.message.vo.*;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 消息 HTTP 入口；/api 前缀由现有代理统一去除。 */
@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService messages;
    private final TokenService tokens;
    public MessageController(MessageService messages, TokenService tokens) { this.messages = messages; this.tokens = tokens; }
    @GetMapping("/inbox")
    public ApiResponse<PagedResult<InboxMessageVO>> inbox(HttpServletRequest http, @Valid @ModelAttribute MessageQuery query) {
        return ApiResponse.success(messages.inbox(tokens.requireCurrentUserId(http), query));
    }
    @GetMapping("/summary")
    public ApiResponse<MessageSummaryVO> summary(HttpServletRequest http) { return ApiResponse.success(messages.summary(tokens.requireCurrentUserId(http))); }
    @GetMapping("/inbox/{id}")
    public ApiResponse<InboxMessageVO> detail(HttpServletRequest http, @PathVariable Long id) { return ApiResponse.success(messages.detail(tokens.requireCurrentUserId(http), id)); }
    @PutMapping("/inbox/{id}/read")
    public ApiResponse<Void> read(HttpServletRequest http, @PathVariable Long id) { messages.read(tokens.requireCurrentUserId(http), id); return ApiResponse.success(); }
    @PutMapping("/inbox/read-all")
    public ApiResponse<Void> readAll(HttpServletRequest http) { messages.readAll(tokens.requireCurrentUserId(http)); return ApiResponse.success(); }
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(HttpServletRequest http) {
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").cacheControl(CacheControl.noStore()).body(messages.connect(tokens.requireCurrentUserId(http)));
    }
    @PostMapping("/admin/send")
    public ApiResponse<Long> send(HttpServletRequest http, @Valid @RequestBody SendMessageRequest request) { return ApiResponse.success(messages.send(tokens.requireCurrentUserId(http), request)); }
    @GetMapping("/admin/sent")
    public ApiResponse<PagedResult<SentMessageVO>> sent(HttpServletRequest http, @Valid @ModelAttribute MessageQuery query) { return ApiResponse.success(messages.sent(tokens.requireCurrentUserId(http), query)); }
    @GetMapping("/admin/sent/{id}/recipients")
    public ApiResponse<PagedResult<RecipientVO>> recipients(HttpServletRequest http, @PathVariable Long id, @Valid @ModelAttribute MessageQuery query) { return ApiResponse.success(messages.recipients(tokens.requireCurrentUserId(http), id, query)); }
    @GetMapping("/admin/recipient-options")
    public ApiResponse<PagedResult<RecipientOptionVO>> options(HttpServletRequest http, @Valid @ModelAttribute MessageQuery query) { return ApiResponse.success(messages.recipientOptions(tokens.requireCurrentUserId(http), query)); }
}
