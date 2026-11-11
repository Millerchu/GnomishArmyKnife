package com.gak.workbench.exception;

import com.gak.framework.exception.BusinessException;

/** 工作台专用校验码，沿用已有业务异常的 HTTP 400 路径。 */
public class WorkbenchPreferenceInvalidException extends BusinessException {
    public static final String CODE = "WORKBENCH_PREFERENCE_INVALID";

    public WorkbenchPreferenceInvalidException(String message) {
        super(CODE, message);
    }
}
