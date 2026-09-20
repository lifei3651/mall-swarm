package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.vo.OrderShipmentImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface OrderShipmentService {

    boolean shipOrder(Long orderId, ShopOrderShipDTO dto);

    /** 微信物流助手生成运单后的专用发货入口，便于后续安全识别并撤销官方运单。 */
    boolean shipWechatExpressOrder(Long orderId, ShopOrderShipDTO dto);

    boolean shipErpOrder(String orderNo, String deliveryCompany, String deliveryNo,
                         Integer shipmentQuantity, String providerCode);

    OrderShipmentImportResultVO importShipments(MultipartFile file);
}
