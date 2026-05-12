package com.gak.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 保存经验条目请求。
 */
public class SaveKnowledgeEntryRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 64, message = "title 长度不能超过 64")
    private String title;

    @NotBlank(message = "category 不能为空")
    @Size(max = 32, message = "category 长度不能超过 32")
    private String category;

    @NotBlank(message = "scenario 不能为空")
    @Size(max = 80, message = "scenario 长度不能超过 80")
    private String scenario;

    @Size(max = 80, message = "source 长度不能超过 80")
    private String source;

    private List<String> tags;

    @NotBlank(message = "summary 不能为空")
    @Size(max = 180, message = "summary 长度不能超过 180")
    private String summary;

    @NotBlank(message = "content 不能为空")
    @Size(max = 2000, message = "content 长度不能超过 2000")
    private String content;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
