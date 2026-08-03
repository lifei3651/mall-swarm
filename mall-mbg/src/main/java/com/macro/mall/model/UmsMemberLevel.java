package com.macro.mall.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class UmsMemberLevel implements Serializable {
    private Long id;
    private String name;
    private Integer growthPoint;
    private BigDecimal discountRate;
    private BigDecimal freeFreightPoint;
    private Integer commentGrowthPoint;
    private Integer privilegeFreeFreight;
    private Integer privilegeMemberPrice;
    private Integer privilegeBirthday;
    private String note;
    private Integer status;
    private Integer sort;
    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getGrowthPoint() { return growthPoint; }
    public void setGrowthPoint(Integer growthPoint) { this.growthPoint = growthPoint; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
    public BigDecimal getFreeFreightPoint() { return freeFreightPoint; }
    public void setFreeFreightPoint(BigDecimal freeFreightPoint) { this.freeFreightPoint = freeFreightPoint; }
    public Integer getCommentGrowthPoint() { return commentGrowthPoint; }
    public void setCommentGrowthPoint(Integer commentGrowthPoint) { this.commentGrowthPoint = commentGrowthPoint; }
    public Integer getPrivilegeFreeFreight() { return privilegeFreeFreight; }
    public void setPrivilegeFreeFreight(Integer privilegeFreeFreight) { this.privilegeFreeFreight = privilegeFreeFreight; }
    public Integer getPrivilegeMemberPrice() { return privilegeMemberPrice; }
    public void setPrivilegeMemberPrice(Integer privilegeMemberPrice) { this.privilegeMemberPrice = privilegeMemberPrice; }
    public Integer getPrivilegeBirthday() { return privilegeBirthday; }
    public void setPrivilegeBirthday(Integer privilegeBirthday) { this.privilegeBirthday = privilegeBirthday; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
