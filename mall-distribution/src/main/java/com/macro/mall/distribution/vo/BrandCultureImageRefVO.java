package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 品牌文化详情图引用；大小由服务端按实际存储文件回填。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandCultureImageRefVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String url;
    private Long size;
}
