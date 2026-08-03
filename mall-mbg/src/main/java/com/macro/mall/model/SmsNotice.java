package com.macro.mall.model;

import java.io.Serializable;
import java.util.Date;

/**
 * 商城公告表
 */
public class SmsNotice implements Serializable {
    private Long id;
    private String title;
    private String content;
    private Integer noticeType;
    private Integer status;
    private Integer sort;
    private Date createTime;
    private Date updateTime;

    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getNoticeType() { return noticeType; }
    public void setNoticeType(Integer noticeType) { this.noticeType = noticeType; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
