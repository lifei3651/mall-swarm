package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ExternalTeamMemberDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.ImportTypeEnum;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.ExternalTeamMigrationService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.ImportResultVO;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExternalTeamMigrationServiceImpl implements ExternalTeamMigrationService {
    private final DmsShopMemberDao memberDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsMigrationBaselineDao baselineDao;
    private final DmsImportBatchDao batchDao;
    private final DmsImportDetailDao detailDao;
    private final ShopAuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO migrate(MultipartFile file, Long anchorAgentId) {
        List<ExternalTeamMemberDTO> rows = parse(file);
        if (rows.isEmpty()) Asserts.fail("平移文件没有数据");
        if (rows.size() > 1000) Asserts.fail("单次最多平移1000人");
        validate(rows);
        DmsAgent anchor = anchorAgentId == null ? null : agentDao.selectById(anchorAgentId);
        if (anchorAgentId != null && anchor == null) Asserts.fail("商城承接上级不存在");

        DmsAdminUser admin = AdminContext.get();
        String batchNo = "MIG" + System.currentTimeMillis();
        DmsImportBatch batch = new DmsImportBatch();
        batch.setBatchNo(batchNo); batch.setBatchName("外部团队平移-" + System.currentTimeMillis());
        batch.setImportType(ImportTypeEnum.AGENT.getValue()); batch.setTotalCount(rows.size());
        batch.setSuccessCount(0); batch.setFailCount(0); batch.setStatus(1);
        batch.setOperatorId(admin == null ? 0L : admin.getId()); batch.setOperatorName(admin == null ? "system" : admin.getUsername());
        batchDao.insert(batch);

        Map<String,DmsShopMember> members = new LinkedHashMap<>();
        for (ExternalTeamMemberDTO row : rows) {
            AdminMemberCreateDTO create = new AdminMemberCreateDTO();
            create.setPhone(row.getPhone());
            create.setUsername(importUsername(row.getExternalMemberCode()));
            create.setNickname(row.getNickname());
            create.setActivateDistribution(false);
            DmsShopMember member = authService.createAdminMember(create);
            members.put(row.getExternalMemberCode(), memberDao.selectById(member.getId()));
        }
        for (ExternalTeamMemberDTO row : rows) {
            DmsShopMember member = members.get(row.getExternalMemberCode());
            Long inviterUserId;
            if (row.getParentExternalCode() == null || row.getParentExternalCode().isBlank()) {
                inviterUserId = anchor == null ? null : anchor.getUserId();
            } else {
                inviterUserId = members.get(row.getParentExternalCode()).getUserId();
            }
            memberDao.updateInviterId(member.getId(), inviterUserId);
        }

        Set<String> completed = new HashSet<>();
        for (int round=0; round<rows.size()+1 && completed.size()<rows.size(); round++) {
            boolean progressed = false;
            for (ExternalTeamMemberDTO row : rows) {
                if (completed.contains(row.getExternalMemberCode())) continue;
                String parentCode = row.getParentExternalCode();
                if (parentCode != null && !parentCode.isBlank() && !completed.contains(parentCode)) continue;
                DmsShopMember member = members.get(row.getExternalMemberCode());
                AgentInfoVO agent = authService.activateMember(member.getUserId(), validLevel(row.getInitialLevel()), "外部团队平移，批次：" + batchNo);
                DmsMigrationBaseline baseline = new DmsMigrationBaseline();
                baseline.setBatchNo(batchNo); baseline.setAgentId(agent.getId()); baseline.setUserId(member.getUserId());
                baseline.setExternalMemberCode(row.getExternalMemberCode());
                baseline.setHistoricalOrderCount(nonNegative(row.getHistoricalOrderCount()));
                baseline.setHistoricalPersonalPerformance(money(row.getHistoricalPersonalPerformance()));
                baseline.setHistoricalTeamPerformance(money(row.getHistoricalTeamPerformance()));
                baseline.setInitialLevel(validLevel(row.getInitialLevel())); baseline.setCutoverTime(LocalDateTime.now());
                baselineDao.insert(baseline);
                // 账户累计件数与晋级器使用同一口径：期初累计有效件数 + 迁入后的新有效件数。
                DmsAgent activatedAgent = agentDao.selectById(agent.getId());
                int historicalUnits = nonNegative(row.getHistoricalOrderCount());
                if (historicalUnits > 0) {
                    DmsAgentAccount account = accountDao.selectByAgentId(activatedAgent.getId());
                    if (account != null) accountDao.addTotalOrders(activatedAgent.getId(), historicalUnits);
                }
                DmsImportDetail detail = new DmsImportDetail();
                detail.setBatchId(batch.getId()); detail.setBatchNo(batchNo); detail.setRowNum(rows.indexOf(row)+1);
                try { detail.setRawData(objectMapper.writeValueAsString(row)); } catch (Exception ignored) { detail.setRawData("{}"); }
                detail.setStatus(1); detail.setTargetId(agent.getId()); detailDao.insert(detail);
                completed.add(row.getExternalMemberCode()); progressed = true;
            }
            if (!progressed && completed.size()<rows.size()) Asserts.fail("外部团队关系存在循环，无法平移");
        }
        batch.setSuccessCount(rows.size()); batch.setFailCount(0); batch.setStatus(2); batchDao.update(batch);
        ImportResultVO result = new ImportResultVO();
        result.setBatchNo(batchNo); result.setTotalCount(rows.size()); result.setSuccessCount(rows.size());
        result.setFailCount(0); result.setStatus(2); result.setStatusName("平移完成"); result.setErrorMessages(List.of());
        return result;
    }

    private void validate(List<ExternalTeamMemberDTO> rows) {
        Set<String> codes = new HashSet<>(); Set<String> phones = new HashSet<>();
        for (ExternalTeamMemberDTO row : rows) {
            if (row.getExternalMemberCode()==null || row.getExternalMemberCode().isBlank()) Asserts.fail("外部会员编号不能为空");
            if (!codes.add(row.getExternalMemberCode())) Asserts.fail("外部会员编号重复："+row.getExternalMemberCode());
            row.setPhone(PhoneNumberUtils.normalize(row.getPhone()));
            if (!PhoneNumberUtils.isValidMainlandMobile(row.getPhone())) Asserts.fail("手机号不正确："+row.getPhone());
            if (!phones.add(row.getPhone()) || memberDao.selectByPhone(row.getPhone())!=null) Asserts.fail("手机号已存在或重复："+row.getPhone());
            if (baselineDao.selectByExternalCode(row.getExternalMemberCode())!=null) Asserts.fail("外部会员已平移："+row.getExternalMemberCode());
        }
        for (ExternalTeamMemberDTO row : rows) {
            if (row.getParentExternalCode()!=null && !row.getParentExternalCode().isBlank() && !codes.contains(row.getParentExternalCode()))
                Asserts.fail("找不到外部上级："+row.getParentExternalCode());
        }
    }

    private List<ExternalTeamMemberDTO> parse(MultipartFile file) {
        if (file==null || file.isEmpty()) Asserts.fail("请选择平移文件");
        try {
            List<List<String>> values = new ArrayList<>(); String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
            if (name.endsWith(".csv")) {
                try (BufferedReader br=new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; boolean header=true; while((line=br.readLine())!=null){ if(header){header=false;continue;} values.add(Arrays.stream(line.split(",",-1)).map(String::trim).toList()); }
                }
            } else {
                try(Workbook wb=WorkbookFactory.create(file.getInputStream())) { Sheet sheet=wb.getSheetAt(0); DataFormatter f=new DataFormatter();
                    for(int i=1;i<=sheet.getLastRowNum();i++){ Row r=sheet.getRow(i); if(r==null)continue; List<String> v=new ArrayList<>(); for(int j=0;j<9;j++)v.add(f.formatCellValue(r.getCell(j)).trim()); values.add(v); }
                }
            }
            List<ExternalTeamMemberDTO> result=new ArrayList<>();
            for(List<String> v:values){ if(v.stream().allMatch(String::isBlank))continue; ExternalTeamMemberDTO d=new ExternalTeamMemberDTO();
                d.setExternalMemberCode(get(v,0));d.setPhone(get(v,1));d.setNickname(get(v,2));d.setParentExternalCode(get(v,3));
                d.setInitialLevel(integer(get(v,4),1));d.setHistoricalOrderCount(integer(get(v,5),0));
                d.setHistoricalPersonalPerformance(decimal(get(v,6)));d.setHistoricalTeamPerformance(decimal(get(v,7)));d.setRemark(get(v,8));result.add(d); }
            return result;
        } catch(Exception e){ if(e instanceof com.macro.mall.common.exception.ApiException a) throw a; throw new IllegalArgumentException("解析平移文件失败："+e.getMessage(),e); }
    }
    private String get(List<String> v,int i){return i<v.size()?v.get(i).trim():"";}
    private String importUsername(String externalMemberCode) {
        String source = externalMemberCode == null ? "member" : externalMemberCode.trim();
        String normalized = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String suffix = Integer.toUnsignedString(source.hashCode(), 36);
        int maxBaseLength = Math.max(7, 20 - suffix.length() - 1);
        String base = "import_" + normalized;
        if (base.length() > maxBaseLength) base = base.substring(0, maxBaseLength);
        return base + "_" + suffix;
    }
    private Integer integer(String v,int d){try{return v==null||v.isBlank()?d:Integer.valueOf(v);}catch(Exception e){return d;}}
    private BigDecimal decimal(String v){try{return v==null||v.isBlank()?BigDecimal.ZERO:new BigDecimal(v);}catch(Exception e){return BigDecimal.ZERO;}}
    private int validLevel(Integer v){return v==null||v<1||v>8?1:v;}
    private int nonNegative(Integer v){return v==null?0:Math.max(v,0);}
    private BigDecimal money(BigDecimal v){return v==null||v.signum()<0?BigDecimal.ZERO:v;}
}
