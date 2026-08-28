package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopServiceTicketAdminActionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写给会员的处理说明")
    @Size(max = 1000, message = "处理说明不能超过1000个字")
    private String content;

    @NotBlank(message = "请选择处理后的状态")
    @Size(max = 32, message = "工单状态不正确")
    private String nextStatus;
}
