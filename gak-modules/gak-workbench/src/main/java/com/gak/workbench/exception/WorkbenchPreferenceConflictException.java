package com.gak.workbench.exception;

import com.gak.workbench.vo.WorkbenchPreferenceVO;

/** 并发冲突携带最新文档，不继承始终映射为 400 的 BusinessException。 */
public class WorkbenchPreferenceConflictException extends RuntimeException {
    public static final String CODE = "WORKBENCH_PREFERENCE_VERSION_CONFLICT";
    private final WorkbenchPreferenceVO latest;

    public WorkbenchPreferenceConflictException(WorkbenchPreferenceVO latest) {
        super("布局已在其他设备更新，请查看后选择保留云端或用本地覆盖。");
        this.latest = latest;
    }

    public WorkbenchPreferenceVO getLatest() {
        return latest;
    }
}
