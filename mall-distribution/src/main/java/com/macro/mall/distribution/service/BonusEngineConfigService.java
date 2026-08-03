package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.BonusSimulationDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.vo.BonusSimulationVO;

import java.util.List;

public interface BonusEngineConfigService {

    DmsTenantDisplayConfig getDisplayConfig(Long tenantId);

    DmsTenantDisplayConfig saveDisplayConfig(DmsTenantDisplayConfig config);

    List<DmsProductPvConfig> listProductPvConfigs(Long tenantId, String keyword, Integer status);

    DmsProductPvConfig saveProductPvConfig(DmsProductPvConfig config);

    boolean updateProductPvStatus(Long id, Integer status);

    boolean deleteProductPvConfig(Long id);

    List<DmsOrderPvDetail> listOrderPvDetails(Long orderId);

    List<DmsBonusCalculationSnapshot> listCalculationSnapshots(Long orderId);

    BonusSimulationVO simulate(BonusSimulationDTO dto);
}
