package com.gak.requirementboard.vo;

import java.util.List;

/**
 * 含完整进度记录的需求详情。
 */
public class RequirementDetailVO extends RequirementListVO {

    private List<RequirementProgressLogVO> progressLogs;

    public List<RequirementProgressLogVO> getProgressLogs() {
        return progressLogs;
    }

    public void setProgressLogs(List<RequirementProgressLogVO> progressLogs) {
        this.progressLogs = progressLogs;
    }
}
