package com.macro.mall.model;

import java.io.Serializable;
import java.util.Date;

public class OmsLogisticsTrace implements Serializable {
    private Long id;
    private Long orderId;
    private String orderSn;
    private String deliveryCompany;
    private String deliverySn;
    private String traceStatus;
    private String traceContent;
    private Date traceTime;
    private Date createTime;
    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderSn() { return orderSn; }
    public void setOrderSn(String orderSn) { this.orderSn = orderSn; }
    public String getDeliveryCompany() { return deliveryCompany; }
    public void setDeliveryCompany(String deliveryCompany) { this.deliveryCompany = deliveryCompany; }
    public String getDeliverySn() { return deliverySn; }
    public void setDeliverySn(String deliverySn) { this.deliverySn = deliverySn; }
    public String getTraceStatus() { return traceStatus; }
    public void setTraceStatus(String traceStatus) { this.traceStatus = traceStatus; }
    public String getTraceContent() { return traceContent; }
    public void setTraceContent(String traceContent) { this.traceContent = traceContent; }
    public Date getTraceTime() { return traceTime; }
    public void setTraceTime(Date traceTime) { this.traceTime = traceTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
