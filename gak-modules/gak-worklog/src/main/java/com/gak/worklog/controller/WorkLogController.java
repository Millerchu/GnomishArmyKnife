package com.gak.worklog.controller;

import com.gak.worklog.dto.CreateWorkLogRequest;
import com.gak.worklog.dto.UpdateWorkLogRequest;
import com.gak.worklog.dto.WeeklyWorkLogBriefResponse;
import com.gak.worklog.dto.WorkLogResponse;
import com.gak.worklog.service.WorkLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作日志控制器。
 */
@RestController
@RequestMapping("/work-logs")
public class WorkLogController {

    private final WorkLogService workLogService;

    public WorkLogController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    /**
     * 新增工作日志。
     *
     * @param request 请求参数
     * @return 工作日志详情
     */
    @PostMapping
    public WorkLogResponse create(@Valid @RequestBody CreateWorkLogRequest request) {
        return workLogService.create(request);
    }

    /**
     * 删除工作日志。
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        workLogService.delete(id);
    }

    /**
     * 更新工作日志。
     *
     * @param id 主键 ID
     * @param request 请求参数
     * @return 更新后详情
     */
    @PutMapping("/{id}")
    public WorkLogResponse update(@PathVariable Long id, @Valid @RequestBody UpdateWorkLogRequest request) {
        return workLogService.update(id, request);
    }

    /**
     * 查询工作日志详情。
     *
     * @param id 主键 ID
     * @return 工作日志详情
     */
    @GetMapping("/{id}")
    public WorkLogResponse get(@PathVariable Long id) {
        return workLogService.get(id);
    }

    /**
     * 条件查询工作日志列表。
     *
     * @param userId 用户 ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param typeCode 类型编码
     * @return 工作日志列表
     */
    @GetMapping
    public List<WorkLogResponse> list(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String typeCode) {
        return workLogService.list(userId, startDate, endDate, typeCode);
    }

    /**
     * 查询最近一周日志简述。
     *
     * @param userId 用户 ID
     * @param refDate 参考日期
     * @return 最近一周日志简述
     */
    @GetMapping("/weekly-brief")
    public List<WeeklyWorkLogBriefResponse> listWeeklyBrief(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate) {
        return workLogService.listWeeklyBrief(userId, refDate);
    }
}
