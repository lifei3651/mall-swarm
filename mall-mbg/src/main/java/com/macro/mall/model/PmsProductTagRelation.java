package com.macro.mall.model;

import java.io.Serializable;

/**
 * 商品标签关联表
 */
public class PmsProductTagRelation implements Serializable {
    private Long id;
    private Long productId;
    private Long tagId;

    private static final long serialVersionUID = 1L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
