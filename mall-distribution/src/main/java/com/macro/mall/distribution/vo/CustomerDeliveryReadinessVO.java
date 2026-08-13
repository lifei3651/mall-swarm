package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDeliveryReadinessVO {
    private boolean ready;
    private int passedRequired;
    private int totalRequired;
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String code;
        private String groupName;
        private String title;
        private boolean required;
        private boolean passed;
        private String detail;
        private String actionPath;
    }
}
