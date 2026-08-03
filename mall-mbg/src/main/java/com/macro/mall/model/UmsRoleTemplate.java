package com.macro.mall.model;

import java.io.Serializable;
import java.util.Date;

public class UmsRoleTemplate implements Serializable {
    private Long id;
    private String name;
    private String description;
    private String resourceIds;
    private String menuIds;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResourceIds() { return resourceIds; }
    public void setResourceIds(String resourceIds) { this.resourceIds = resourceIds; }
    public String getMenuIds() { return menuIds; }
    public void setMenuIds(String menuIds) { this.menuIds = menuIds; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
