package com.macro.mall.model;

import java.io.Serializable;
import java.util.Date;

public class SmsGroupOrder implements Serializable {
    private Long id;
    private Long groupBuyId;
    private String groupNo;
    private Long orderId;
    private String orderSn;
    private Long memberId;
    private String memberName;
    private Integer isLeader;
    private Integer status;
    private Date expireTime;
    private Date createTime;
    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupBuyId() { return groupBuyId; }
    public void setGroupBuyId(Long groupBuyId) { this.groupBuyId = groupBuyId; }
    public String getGroupNo() { return groupNo; }
    public void setGroupNo(String groupNo) { this.groupNo = groupNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderSn() { return orderSn; }
    public void setOrderSn(String orderSn) { this.orderSn = orderSn; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public Integer getIsLeader() { return isLeader; }
    public void setIsLeader(Integer isLeader) { this.isLeader = isLeader; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
