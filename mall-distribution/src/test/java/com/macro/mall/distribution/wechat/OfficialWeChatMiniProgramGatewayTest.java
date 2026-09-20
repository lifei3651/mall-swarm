package com.macro.mall.distribution.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficialWeChatMiniProgramGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesOfficialDeliveryListResponse() throws Exception {
        var response = objectMapper.readTree("""
                {"errcode":0,"count":2,"delivery_list":[
                  {"delivery_id":"YTO","delivery_name":"圆通速递"},
                  {"delivery_id":"SF","delivery_name":"顺丰速运"}
                ]}
                """);

        assertEquals(List.of(
                        new WeChatMiniProgramGateway.DeliveryCompany("YTO", "圆通速递"),
                        new WeChatMiniProgramGateway.DeliveryCompany("SF", "顺丰速运")),
                OfficialWeChatMiniProgramGateway.parseDeliveryCompanies(response));
    }

    @Test
    void keepsCompatibilityWithLegacyDataField() throws Exception {
        var response = objectMapper.readTree("""
                {"errcode":0,"data":[{"delivery_id":"ZTO","delivery_name":"中通快递"}]}
                """);

        assertEquals(List.of(new WeChatMiniProgramGateway.DeliveryCompany("ZTO", "中通快递")),
                OfficialWeChatMiniProgramGateway.parseDeliveryCompanies(response));
    }
}
