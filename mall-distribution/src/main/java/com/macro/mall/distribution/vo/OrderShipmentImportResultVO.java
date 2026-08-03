package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrderShipmentImportResultVO {

    private int totalRows;

    private int shippedCount;

    private int skippedCount;

    private int failedCount;

    private boolean success;

    private String message;

    private List<RowError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {

        private int rowNumber;

        private String orderNo;

        private String message;
    }
}
