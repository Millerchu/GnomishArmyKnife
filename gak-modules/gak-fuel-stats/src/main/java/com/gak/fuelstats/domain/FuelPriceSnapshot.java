package com.gak.fuelstats.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /**
     * 下一次成品油调价时间点。
     */
    private LocalDateTime nextAdjustTime;
    /**
     * 下一次调价窗口说明，例如“5月19日24时”。
     */
    private String adjustWindow;
    /**
     * 调价趋势或简要说明。
     */
    private String priceChangeHint;
    @TableField("price_92")
    private BigDecimal price92;
    @TableField("price_95")
    private BigDecimal price95;
    @TableField("price_98")
    private BigDecimal price98;
    @TableField("price_diesel")
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

    public LocalDateTime getNextAdjustTime() {
        return nextAdjustTime;
    }

    public void setNextAdjustTime(LocalDateTime nextAdjustTime) {
        this.nextAdjustTime = nextAdjustTime;
    }

    public String getAdjustWindow() {
        return adjustWindow;
    }

    public void setAdjustWindow(String adjustWindow) {
        this.adjustWindow = adjustWindow;
    }

    public String getPriceChangeHint() {
        return priceChangeHint;
    }

    public void setPriceChangeHint(String priceChangeHint) {
        this.priceChangeHint = priceChangeHint;
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
