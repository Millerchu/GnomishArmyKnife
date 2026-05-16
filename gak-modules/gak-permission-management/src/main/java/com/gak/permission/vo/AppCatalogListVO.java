package com.gak.permission.vo;

import java.util.List;

/**
 * 应用目录列表响应。
 */
public class AppCatalogListVO {

    private List<AppCatalogVO> list;
    private String catalogSource;

    public List<AppCatalogVO> getList() {
        return list;
    }

    public void setList(List<AppCatalogVO> list) {
        this.list = list;
    }

    public String getCatalogSource() {
        return catalogSource;
    }

    public void setCatalogSource(String catalogSource) {
        this.catalogSource = catalogSource;
    }
}
