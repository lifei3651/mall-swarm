package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsImportBatchDao;
import com.macro.mall.distribution.dao.DmsImportDetailDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.dto.ImportOrderDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsImportBatch;
import com.macro.mall.distribution.entity.DmsImportDetail;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.enums.ImportTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.ImportService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.ImportResultVO;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.Set;

/**
 * 导入服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final DmsImportBatchDao batchDao;
    private final DmsImportDetailDao detailDao;
    private final DmsAgentDao agentDao;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final ImportTransactionHelper importTransactionHelper;
    private final ImportExecutionGuard importExecutionGuard;
    private final Validator validator;

    @Override
    public ImportResultVO importAgents(MultipartFile file, Long operatorId, String operatorName) {
        return importAgents(file, operatorId, operatorName, null);
    }

    @Override
    public ImportResultVO importAgents(MultipartFile file, Long operatorId, String operatorName, String batchNo) {
        return importExecutionGuard.execute(() -> {
            List<ImportAgentDTO> agentList = requireImportList(parseAgentFile(file));
            return importAgentsInternal(agentList, operatorId, operatorName, batchNo);
        });
    }

    @Override
    public ImportResultVO importAgents(List<ImportAgentDTO> agentList, Long operatorId, String operatorName) {
        return importExecutionGuard.execute(() -> importAgentsInternal(
                requireImportList(agentList), operatorId, operatorName, null));
    }

    private ImportResultVO importAgentsInternal(List<ImportAgentDTO> agentList, Long operatorId,
                                                String operatorName, String requestedBatchNo) {
        // 创建导入批次
        DmsImportBatch batch = new DmsImportBatch();
        batch.setBatchNo(resolveBatchNo(requestedBatchNo));
        batch.setBatchName("代理导入-" + System.currentTimeMillis());
        batch.setImportType(ImportTypeEnum.AGENT.getValue());
        batch.setTotalCount(agentList.size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch.setStatus(1); // 处理中
        batch.setOperatorId(operatorId);
        batch.setOperatorName(operatorName);
        batchDao.insert(batch);

        int successCount = 0;
        int failCount = 0;
        List<String> errorMessages = new ArrayList<>();

        // 逐行处理
        for (int i = 0; i < agentList.size(); i++) {
            ImportAgentDTO agentDTO = agentList.get(i);
            DmsImportDetail detail = new DmsImportDetail();
            detail.setBatchId(batch.getId());
            detail.setBatchNo(batch.getBatchNo());
            detail.setRowNum(i + 1);
            try {
                detail.setRawData(objectMapper.writeValueAsString(agentDTO));
            } catch (Exception e) {
                detail.setRawData("{}");
            }
            detail.setStatus(0); // 待处理

            try {
                validateRow(agentDTO);
                // 使用独立事务处理每条记录，避免单条失败导致整个批次回滚
                DmsAgent agent = importTransactionHelper.processAgentImport(agentDTO);
                detail.setStatus(1); // 成功
                detail.setTargetId(agent.getId());
                successCount++;

                log.info("导入代理成功: row={}, agentCode={}", i + 1, agent.getAgentCode());
            } catch (Exception e) {
                detail.setStatus(2); // 失败
                detail.setErrorMsg(e.getMessage());
                failCount++;
                errorMessages.add("第" + (i + 1) + "行: " + e.getMessage());

                log.error("导入代理失败: row={}", i + 1, e);
            }

            detailDao.insert(detail);
            batchDao.updateCounts(batch.getId(), successCount, failCount);
        }

        // 更新批次统计
        batch.setSuccessCount(successCount);
        batch.setFailCount(failCount);
        batch.setStatus(2); // 处理完成
        batchDao.update(batch);

        // 构建返回结果
        ImportResultVO result = buildResult(batch);
        result.setErrorMessages(errorMessages);

        log.info("批量导入代理完成: batchNo={}, total={}, success={}, fail={}",
                batch.getBatchNo(), batch.getTotalCount(), successCount, failCount);

        return result;
    }

    @Override
    public ImportResultVO importOrders(MultipartFile file, Long operatorId, String operatorName) {
        return importOrders(file, operatorId, operatorName, null);
    }

    @Override
    public ImportResultVO importOrders(MultipartFile file, Long operatorId, String operatorName, String batchNo) {
        return importExecutionGuard.execute(() -> {
            List<ImportOrderDTO> orderList = requireImportList(parseOrderFile(file));
            return importOrdersInternal(orderList, operatorId, operatorName, batchNo);
        });
    }

    @Override
    public ImportResultVO importOrders(List<ImportOrderDTO> orderList, Long operatorId, String operatorName) {
        return importExecutionGuard.execute(() -> importOrdersInternal(
                requireImportList(orderList), operatorId, operatorName, null));
    }

    private ImportResultVO importOrdersInternal(List<ImportOrderDTO> orderList, Long operatorId,
                                                String operatorName, String requestedBatchNo) {
        // 创建导入批次
        DmsImportBatch batch = new DmsImportBatch();
        batch.setBatchNo(resolveBatchNo(requestedBatchNo));
        batch.setBatchName("订单导入-" + System.currentTimeMillis());
        batch.setImportType(ImportTypeEnum.ORDER.getValue());
        batch.setTotalCount(orderList.size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch.setStatus(1); // 处理中
        batch.setOperatorId(operatorId);
        batch.setOperatorName(operatorName);
        batchDao.insert(batch);

        int successCount = 0;
        int failCount = 0;
        List<String> errorMessages = new ArrayList<>();

        // 逐行处理
        for (int i = 0; i < orderList.size(); i++) {
            ImportOrderDTO orderDTO = orderList.get(i);
            DmsImportDetail detail = new DmsImportDetail();
            detail.setBatchId(batch.getId());
            detail.setBatchNo(batch.getBatchNo());
            detail.setRowNum(i + 1);
            try {
                detail.setRawData(objectMapper.writeValueAsString(orderDTO));
            } catch (Exception e) {
                detail.setRawData("{}");
            }
            detail.setStatus(0); // 待处理

            try {
                validateRow(orderDTO);
                // 使用独立事务处理每条记录，避免单条失败导致整个批次回滚
                Long orderId = importTransactionHelper.processOrderImport(orderDTO);
                detail.setStatus(1);
                detail.setTargetId(orderId);

                successCount++;
                log.info("导入订单成功: row={}, orderNo={}", i + 1, orderDTO.getOrderNo());
            } catch (Exception e) {
                detail.setStatus(2); // 失败
                detail.setErrorMsg(e.getMessage());
                failCount++;
                errorMessages.add("第" + (i + 1) + "行: " + e.getMessage());

                log.error("导入订单失败: row={}", i + 1, e);
            }

            detailDao.insert(detail);
            batchDao.updateCounts(batch.getId(), successCount, failCount);
        }

        // 更新批次统计
        batch.setSuccessCount(successCount);
        batch.setFailCount(failCount);
        batch.setStatus(2); // 处理完成
        batchDao.update(batch);

        // 构建返回结果
        ImportResultVO result = buildResult(batch);
        result.setErrorMessages(errorMessages);

        log.info("批量导入订单完成: batchNo={}, total={}, success={}, fail={}",
                batch.getBatchNo(), batch.getTotalCount(), successCount, failCount);

        return result;
    }

    @Override
    public ImportResultVO getImportResult(String batchNo) {
        DmsImportBatch batch = batchDao.selectByBatchNo(batchNo);
        if (batch == null) {
            return null;
        }

        ImportResultVO result = buildResult(batch);

        // 查询失败详情
        List<DmsImportDetail> failDetails = detailDao.selectByBatchIdAndStatus(batch.getId(), 2);
        List<String> errorMessages = new ArrayList<>();
        for (DmsImportDetail detail : failDetails) {
            errorMessages.add("第" + detail.getRowNum() + "行: " + detail.getErrorMsg());
        }
        result.setErrorMessages(errorMessages);

        return result;
    }

    /**
     * 从DTO创建代理实体
     */
    private DmsAgent createAgentFromDTO(ImportAgentDTO dto) {
        if (dto.getAgentName() == null || dto.getAgentName().isBlank()) {
            Asserts.fail("代理名称不能为空");
        }
        if (!PhoneNumberUtils.isValidMainlandMobile(dto.getPhone())) {
            Asserts.fail("请输入正确的11位手机号");
        }
        dto.setPhone(PhoneNumberUtils.normalize(dto.getPhone()));

        AgentRegisterDTO registerDTO = new AgentRegisterDTO();
        registerDTO.setUserId(dto.getUserId() != null ? dto.getUserId() : IdUtil.getSnowflakeNextId());
        registerDTO.setAgentName(dto.getAgentName());
        registerDTO.setPhone(dto.getPhone());
        registerDTO.setRealName(dto.getRealName());
        registerDTO.setIdCard(dto.getIdCard());
        registerDTO.setSourceType(AgentSourceTypeEnum.BATCH_IMPORT.getValue());
        if (dto.getParentAgentCode() != null && !dto.getParentAgentCode().isEmpty()) {
            DmsAgent parentAgent = agentDao.selectByAgentCode(dto.getParentAgentCode());
            if (parentAgent == null) {
                Asserts.fail("上级代理编号不存在: " + dto.getParentAgentCode());
            }
            registerDTO.setInviteCode(parentAgent.getInviteCode());
        }

        AgentInfoVO agentInfo = agentService.register(registerDTO);
        DmsAgent agent = agentDao.selectById(agentInfo.getId());
        agent.setBankName(dto.getBankName());
        agent.setBankAccount(dto.getBankAccount());
        agent.setRemark(dto.getRemark());
        agentDao.update(agent);
        return agent;
    }

    private List<ImportAgentDTO> parseAgentFile(MultipartFile file) {
        List<List<String>> rows = readRows(file);
        List<ImportAgentDTO> result = new ArrayList<>();
        for (List<String> row : rows) {
            if (isBlankRow(row)) {
                continue;
            }
            ImportAgentDTO dto = new ImportAgentDTO();
            dto.setUserId(parseLong(get(row, 0)));
            dto.setAgentName(get(row, 1));
            dto.setPhone(get(row, 2));
            dto.setRealName(get(row, 3));
            dto.setIdCard(get(row, 4));
            dto.setParentAgentCode(get(row, 5));
            dto.setBankName(get(row, 6));
            dto.setBankAccount(get(row, 7));
            dto.setRemark(get(row, 8));
            result.add(dto);
        }
        return result;
    }

    private List<ImportOrderDTO> parseOrderFile(MultipartFile file) {
        List<List<String>> rows = readRows(file);
        List<ImportOrderDTO> result = new ArrayList<>();
        for (List<String> row : rows) {
            if (isBlankRow(row)) {
                continue;
            }
            ImportOrderDTO dto = new ImportOrderDTO();
            dto.setOrderNo(get(row, 0));
            dto.setOrderAmount(parseDecimal(get(row, 1)));
            dto.setOrderTime(parseDateTime(get(row, 2)));
            dto.setOwnerAgentCode(get(row, 3));
            dto.setProductName(get(row, 4));
            dto.setQuantity(parseInteger(get(row, 5)));
            dto.setRemark(get(row, 6));
            if (dto.getOrderNo() == null || dto.getOrderNo().isBlank()) {
                Asserts.fail("订单编号不能为空");
            }
            if (dto.getOrderAmount() == null || dto.getOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
                Asserts.fail("订单金额必须大于0");
            }
            if (dto.getOwnerAgentCode() == null || dto.getOwnerAgentCode().isBlank()) {
                Asserts.fail("订单归属登录账号不能为空");
            }
            result.add(dto);
        }
        return result;
    }

    private List<List<String>> readRows(MultipartFile file) {
        String extension = ImportFilePolicy.requireSupportedExtension(file);
        try {
            if ("csv".equals(extension) || "txt".equals(extension)) {
                return readTextRows(file);
            }
            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                ImportFilePolicy.requireRowCount(sheet.getLastRowNum(), ImportFilePolicy.MAX_IMPORT_ROWS);
                DataFormatter formatter = new DataFormatter();
                List<List<String>> rows = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }
                    List<String> values = new ArrayList<>();
                    short lastCellNum = row.getLastCellNum();
                    ImportFilePolicy.requireColumnCount(Math.max(lastCellNum, 0));
                    for (int j = 0; j < Math.max(lastCellNum, 0); j++) {
                        values.add(ImportFilePolicy.requireCellLength(formatter.formatCellValue(row.getCell(j))));
                    }
                    rows.add(values);
                }
                return rows;
            }
        } catch (Exception e) {
            if (e instanceof com.macro.mall.common.exception.ApiException apiException) throw apiException;
            log.warn("业务导入文件解析失败: type={}", e.getClass().getSimpleName(), e);
            throw new RuntimeException("解析导入文件失败，请核对文件格式和模板内容", e);
        }
    }

    private List<List<String>> readTextRows(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                if (line.length() > ImportFilePolicy.MAX_TEXT_LINE_LENGTH) {
                    Asserts.fail("导入文件存在超长文本行");
                }
                ImportFilePolicy.requireRowCount(rows.size() + 1, ImportFilePolicy.MAX_IMPORT_ROWS);
                String delimiter = line.contains("\t") ? "\t" : ",";
                List<String> values = Arrays.asList(line.split(delimiter, -1));
                ImportFilePolicy.requireColumnCount(values.size());
                rows.add(values.stream().map(ImportFilePolicy::requireCellLength).toList());
            }
        }
        return rows;
    }

    private <T> List<T> requireImportList(List<T> rows) {
        if (rows == null || rows.isEmpty()) Asserts.fail("导入数据不能为空");
        ImportFilePolicy.requireRowCount(rows.size(), ImportFilePolicy.MAX_IMPORT_ROWS);
        return rows;
    }

    private <T> void validateRow(T row) {
        if (row == null) Asserts.fail("导入行不能为空");
        Set<ConstraintViolation<T>> violations = validator.validate(row);
        if (violations.isEmpty()) return;
        String message = violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .findFirst().orElse("导入数据格式不正确");
        Asserts.fail(message);
    }

    private boolean isBlankRow(List<String> row) {
        return row == null || row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private String get(List<String> row, int index) {
        if (row == null || row.size() <= index) {
            return null;
        }
        String value = row.get(index);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private Integer parseInteger(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private BigDecimal parseDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.parse(value);
    }

    private String resolveBatchNo(String requestedBatchNo) {
        if (requestedBatchNo == null || requestedBatchNo.isBlank()) {
            return generateBatchNo();
        }
        String normalized = requestedBatchNo.trim().toUpperCase();
        if (!normalized.matches("[A-Z0-9]{12,40}")) {
            Asserts.fail("导入批次编号格式不正确");
        }
        if (batchDao.selectByBatchNo(normalized) != null) {
            Asserts.fail("导入批次编号已存在，请重新发起导入");
        }
        return normalized;
    }

    private ImportResultVO buildResult(DmsImportBatch batch) {
        ImportResultVO result = new ImportResultVO();
        result.setBatchNo(batch.getBatchNo());
        result.setBatchName(batch.getBatchName());
        result.setImportType(batch.getImportType());
        ImportTypeEnum importType = ImportTypeEnum.getByValue(batch.getImportType());
        result.setImportTypeName(importType == null ? "未知" : importType.getName());
        int successCount = batch.getSuccessCount() == null ? 0 : batch.getSuccessCount();
        int failCount = batch.getFailCount() == null ? 0 : batch.getFailCount();
        int totalCount = batch.getTotalCount() == null ? 0 : batch.getTotalCount();
        int processedCount = successCount + failCount;
        result.setTotalCount(totalCount);
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setProcessedCount(processedCount);
        result.setProgressPercent(totalCount == 0 ? 100 : Math.min(100, processedCount * 100 / totalCount));
        result.setStatus(batch.getStatus());
        result.setStatusName(getStatusName(batch.getStatus()));
        result.setErrorFileUrl(batch.getErrorFileUrl());
        result.setOperatorName(batch.getOperatorName());
        result.setCreateTime(batch.getCreateTime());
        return result;
    }

    /**
     * 生成批次编号
     */
    private String generateBatchNo() {
        return "BATCH" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "待处理";
            case 1:
                return "处理中";
            case 2:
                return "处理完成";
            case 3:
                return "处理失败";
            default:
                return "未知";
        }
    }
}
