package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientErrorReportDTO {
    @NotBlank
    @Pattern(regexp = "shop|admin")
    private String app;
    @NotBlank
    @Pattern(regexp = "vue|window|promise")
    private String source;
    @Size(max = 80)
    private String name;
    @NotBlank
    @Size(max = 500)
    private String message;
    @Size(max = 180)
    private String route;
    @Size(max = 180)
    private String info;
}
