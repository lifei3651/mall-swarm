package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.dto.ImportOrderDTO;
import com.macro.mall.distribution.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 导入服务接口
 */
public interface ImportService {

    /**
     * 批量导入代理（Excel文件）
     * @param file Excel文件
     * @param operatorId 操作人ID
     * @param operatorName 操作人名称
     * @return 导入结果
     */
    ImportResultVO importAgents(MultipartFile file, Long operatorId, String operatorName);

    /**
     * 批量导入代理（数据列表）
     * @param agentList 代理数据列表
     * @param operatorId 操作人ID
     * @param operatorName 操作人名称
     * @return 导入结果
     */
    ImportResultVO importAgents(List<ImportAgentDTO> agentList, Long operatorId, String operatorName);

    /**
     * 批量导入订单（Excel文件）
     * @param file Excel文件
     * @param operatorId 操作人ID
     * @param operatorName 操作人名称
     * @return 导入结果
     */
    ImportResultVO importOrders(MultipartFile file, Long operatorId, String operatorName);

    /**
     * 批量导入订单（数据列表）
     * @param orderList 订单数据列表
     * @param operatorId 操作人ID
     * @param operatorName 操作人名称
     * @return 导入结果
     */
    ImportResultVO importOrders(List<ImportOrderDTO> orderList, Long operatorId, String operatorName);

    /**
     * 查询导入批次详情
     * @param batchNo 批次编号
     * @return 导入结果
     */
    ImportResultVO getImportResult(String batchNo);
}
