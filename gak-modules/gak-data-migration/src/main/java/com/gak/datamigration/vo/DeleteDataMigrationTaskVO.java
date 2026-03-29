package com.gak.datamigration.vo;

/**
 * 删除迁移任务结果。
 */
public class DeleteDataMigrationTaskVO {

    private Long id;
    private Boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
