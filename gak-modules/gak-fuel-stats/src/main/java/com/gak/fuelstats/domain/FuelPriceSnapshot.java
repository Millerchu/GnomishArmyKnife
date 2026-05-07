package com.gak.fuelstats.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 最新油价快照实体。
 */
@TableName("gak_fuel_price_snapshot")
public class FuelPriceSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private LocalDateTime publishDate;
    private BigDecimal price92;
    private BigDecimal price95;
    private BigDecimal price98;
    private BigDecimal priceDiesel;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDateTime publishDate) {
        this.publishDate = publishDate;
    }

    public BigDecimal getPrice92() {
        return price92;
    }

    public void setPrice92(BigDecimal price92) {
        this.price92 = price92;
    }

    public BigDecimal getPrice95() {
        return price95;
    }

    public void setPrice95(BigDecimal price95) {
        this.price95 = price95;
    }

    public BigDecimal getPrice98() {
        return price98;
    }

    public void setPrice98(BigDecimal price98) {
        this.price98 = price98;
    }

    public BigDecimal getPriceDiesel() {
        return priceDiesel;
    }

    public void setPriceDiesel(BigDecimal priceDiesel) {
        this.priceDiesel = priceDiesel;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
