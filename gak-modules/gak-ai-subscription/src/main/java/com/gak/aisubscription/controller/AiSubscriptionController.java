package com.gak.aisubscription.controller;
import com.gak.aisubscription.dto.*;
import com.gak.aisubscription.vo.*;
import com.gak.aisubscription.service.*;
import com.gak.framework.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
/** 智订 API：身份与应用授权统一入口，服务执行业务与事务。 */
@RestController
@RequestMapping("/ai-subscriptions")
public class AiSubscriptionController {
 private final AiAccessService access;private final AiAccountService accounts;private final AiSubscriptionService subscriptions;private final AiUsageService usage;private final AiPaymentService payments;private final AiSummaryService summary;
 public AiSubscriptionController(AiAccessService access,AiAccountService accounts,AiSubscriptionService subscriptions,AiUsageService usage,AiPaymentService payments,AiSummaryService summary){this.access=access;this.accounts=accounts;this.subscriptions=subscriptions;this.usage=usage;this.payments=payments;this.summary=summary;}
 @GetMapping("/tools") public ApiResponse<List<AiToolVO>> tools(HttpServletRequest h){access.requireCurrentUserId(h);return ApiResponse.success(AiCatalog.TOOLS);}
 @GetMapping("/accounts") public ApiResponse<List<AiAccountVO>> accounts(HttpServletRequest h){return ApiResponse.success(accounts.list(access.requireCurrentUserId(h)));}
 @PostMapping("/accounts") public ApiResponse<AiAccountVO> account(@Valid @RequestBody SaveAiAccountRequest r,HttpServletRequest h){return ApiResponse.success(accounts.save(access.requireCurrentUserId(h),null,r));}
 @PutMapping("/accounts/{id}") public ApiResponse<AiAccountVO> account(@PathVariable Long id,@Valid @RequestBody SaveAiAccountRequest r,HttpServletRequest h){return ApiResponse.success(accounts.save(access.requireCurrentUserId(h),id,r));}
 @DeleteMapping("/accounts/{id}") public ApiResponse<Void> archive(@PathVariable Long id,HttpServletRequest h){accounts.archive(access.requireCurrentUserId(h),id);return ApiResponse.success();}
 @GetMapping("/password-memo-options") public ApiResponse<List<AiMemoOptionVO>> memos(HttpServletRequest h){return ApiResponse.success(accounts.memoOptions(access.requireCurrentUserId(h)));}
 @GetMapping("/subscriptions") public ApiResponse<List<AiSubscriptionVO>> subscriptions(HttpServletRequest h){return ApiResponse.success(subscriptions.list(access.requireCurrentUserId(h)));}
 @PostMapping("/subscriptions") public ApiResponse<AiSubscriptionVO> subscription(@Valid @RequestBody SaveAiSubscriptionRequest r,HttpServletRequest h){return ApiResponse.success(subscriptions.save(access.requireCurrentUserId(h),null,r));}
 @PutMapping("/subscriptions/{id}") public ApiResponse<AiSubscriptionVO> subscription(@PathVariable Long id,@Valid @RequestBody SaveAiSubscriptionRequest r,HttpServletRequest h){return ApiResponse.success(subscriptions.save(access.requireCurrentUserId(h),id,r));}
 @DeleteMapping("/subscriptions/{id}") public ApiResponse<Void> terminate(@PathVariable Long id,HttpServletRequest h){subscriptions.terminate(access.requireCurrentUserId(h),id);return ApiResponse.success();}
 @GetMapping("/usage-records") public ApiResponse<List<AiUsageRecordVO>> usage(HttpServletRequest h){return ApiResponse.success(usage.list(access.requireCurrentUserId(h)));}
 @PostMapping("/usage-records") public ApiResponse<AiUsageRecordVO> usage(@Valid @RequestBody SaveAiUsageRequest r,HttpServletRequest h){return ApiResponse.success(usage.create(access.requireCurrentUserId(h),r));}
 @PostMapping("/usage-records/{id}/resolution") public ApiResponse<AiUsageRecordVO> resolve(@PathVariable Long id,@Valid @RequestBody AiUsageResolutionRequest r,HttpServletRequest h){return ApiResponse.success(usage.resolve(access.requireCurrentUserId(h),id,r.action()));}
 @GetMapping("/payments") public ApiResponse<List<AiPaymentVO>> payments(HttpServletRequest h){return ApiResponse.success(payments.list(access.requireCurrentUserId(h)));}
 @PostMapping("/payments") public ApiResponse<AiPaymentVO> payment(@Valid @RequestBody SaveAiPaymentRequest r,HttpServletRequest h){return ApiResponse.success(payments.create(access.requireCurrentUserId(h),r));}
 @GetMapping("/summary") public ApiResponse<AiSummaryVO> summary(@RequestParam(required=false) String month,HttpServletRequest h){return ApiResponse.success(summary.summary(access.requireCurrentUserId(h),month));}
 @GetMapping("/insights") public ApiResponse<AiInsightsVO> insights(@RequestParam(required=false) String month,HttpServletRequest request){return ApiResponse.success(summary.insights(access.requireCurrentUserId(request),month));}
}
