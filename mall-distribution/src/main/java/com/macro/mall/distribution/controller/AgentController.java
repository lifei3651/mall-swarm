package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.LineChangeAuditDTO;
import com.macro.mall.distribution.entity.DmsLineChangeApplication;
import com.macro.mall.distribution.service.LineChangeApplicationService;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.dto.AgentLevelAdjustDTO;
import com.macro.mall.distribution.service.AgentRelationService;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 代理管理控制器
 */
@Tag(name = "AgentController", description = "会员关系管理")
@RestController
@RequestMapping("/distribution/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ShopAuthService shopAuthService;
    private final LineChangeApplicationService lineChangeApplicationService;
    private final AgentRelationService relationService;
    private final PerformanceService performanceService;

    @Operation(summary = "后台将已有商城账号设为会员")
    @PostMapping("/register")
    public CommonResult<AgentInfoVO> register(@RequestBody AgentRegisterDTO registerDTO) {
        if (registerDTO == null || registerDTO.getUserId() == null) {
            return CommonResult.failed("请选择已有商城账号");
        }
        AgentInfoVO agentInfo = shopAuthService.activateMember(
                registerDTO.getUserId(), 1, "后台将已有商城账号设为会员");
        return CommonResult.success(agentInfo);
    }

    @Operation(summary = "查询会员关系列表")
    @GetMapping("/list")
    public CommonResult<CommonPage<AgentInfoVO>> listAgents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer agentLevel,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<AgentInfoVO> agents = agentService.listAgents(keyword, status, agentLevel);
        return CommonResult.success(CommonPage.restPage(agents));
    }

    @Operation(summary = "导出筛选后的会员关系")
    @GetMapping("/export")
    public void exportAgents(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Integer status,
                             @RequestParam(required = false) Integer agentLevel,
                             HttpServletResponse response) throws IOException {
        List<AgentInfoVO> agents = agentService.listAgents(keyword, status, agentLevel);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=member-relations.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("会员关系");
            String[] headers = {"登录账号", "会员名称", "真实姓名", "手机号", "推广编号",
                    "级别", "直属上级", "组织深度", "邀请码", "状态", "来源", "注册时间"};
            Row headerRow = sheet.createRow(0);
            Font font = workbook.createFont();
            font.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowIndex = 1;
            for (AgentInfoVO agent : agents) {
                Row row = sheet.createRow(rowIndex++);
                setCell(row, 0, agent.getMemberAccount());
                setCell(row, 1, agent.getAgentName());
                setCell(row, 2, agent.getRealName());
                setCell(row, 3, agent.getPhone());
                setCell(row, 4, agent.getAgentCode());
                setCell(row, 5, agent.getAgentLevelName());
                setCell(row, 6, agent.getParentName() == null ? "无直属上级" : agent.getParentName());
                setCell(row, 7, agent.getLevelDepth());
                setCell(row, 8, agent.getInviteCode());
                setCell(row, 9, agent.getStatusName());
                setCell(row, 10, agent.getSourceTypeName());
                setCell(row, 11, agent.getCreateTime() == null ? "" : formatter.format(agent.getCreateTime()));
            }
            int[] widths = {16, 18, 16, 16, 20, 14, 18, 12, 16, 12, 14, 22};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            workbook.write(response.getOutputStream());
        }
    }

    @Operation(summary = "按登录账号或手机号查询会员关系身份")
    @GetMapping("/resolve/{memberKey}")
    public CommonResult<AgentInfoVO> resolveAgent(@PathVariable String memberKey) {
        return CommonResult.success(agentService.getAgentById(performanceService.resolveAgentId(memberKey)));
    }

    private void setCell(Row row, int index, Object value) {
        row.createCell(index).setCellValue(value == null ? "" : String.valueOf(value));
    }

    @Operation(summary = "查询会员关系树根节点")
    @GetMapping("/roots")
    public CommonResult<List<AgentInfoVO>> getRootAgents() {
        return CommonResult.success(agentService.getRootAgents());
    }

    @Operation(summary = "更新会员关系信息")
    @PutMapping("/{id}")
    public CommonResult<Boolean> updateAgent(@PathVariable Long id, @RequestBody AgentUpdateDTO updateDTO) {
        return CommonResult.success(agentService.updateAgentInfo(id, updateDTO));
    }

    @Operation(summary = "更新会员关系状态")
    @PutMapping("/{id}/status")
    public CommonResult<Boolean> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(agentService.updateStatus(id, status));
    }

    @Operation(summary = "管理员直接调整会员卡级")
    @PutMapping("/{id}/level")
    public CommonResult<AgentInfoVO> adjustLevel(@PathVariable Long id, @RequestBody AgentLevelAdjustDTO dto) {
        return CommonResult.success(agentService.adjustLevel(id, dto.getLevel(), dto.getReason()));
    }

    @Operation(summary = "根据关系ID查询会员")
    @GetMapping("/{id}")
    public CommonResult<AgentInfoVO> getAgentById(@PathVariable Long id) {
        AgentInfoVO agentInfo = agentService.getAgentById(id);
        if (agentInfo == null) {
            return CommonResult.failed("会员不存在");
        }
        return CommonResult.success(agentInfo);
    }

    @Operation(summary = "根据用户ID查询会员关系")
    @GetMapping("/user/{userId}")
    public CommonResult<AgentInfoVO> getAgentByUserId(@PathVariable Long userId) {
        AgentInfoVO agentInfo = agentService.getAgentByUserId(userId);
        if (agentInfo == null) {
            return CommonResult.failed("会员不存在");
        }
        return CommonResult.success(agentInfo);
    }

    @Operation(summary = "根据推广编号查询会员")
    @GetMapping("/code/{agentCode}")
    public CommonResult<AgentInfoVO> getAgentByAgentCode(@PathVariable String agentCode) {
        AgentInfoVO agentInfo = agentService.getAgentByAgentCode(agentCode);
        if (agentInfo == null) {
            return CommonResult.failed("会员不存在");
        }
        return CommonResult.success(agentInfo);
    }

    @Operation(summary = "根据邀请码查询会员")
    @GetMapping("/invite/{inviteCode}")
    public CommonResult<AgentInfoVO> getAgentByInviteCode(@PathVariable String inviteCode) {
        AgentInfoVO agentInfo = agentService.getAgentByInviteCode(inviteCode);
        if (agentInfo == null) {
            return CommonResult.failed("会员不存在");
        }
        return CommonResult.success(agentInfo);
    }

    @Operation(summary = "查询直属下级会员列表")
    @GetMapping("/children/{parentId}")
    public CommonResult<CommonPage<AgentInfoVO>> getChildrenAgents(@PathVariable Long parentId) {
        List<AgentInfoVO> agents = agentService.getChildrenAgents(parentId);
        return CommonResult.success(CommonPage.restPage(agents));
    }

    @Operation(summary = "查询所有下级会员（包括多级）")
    @GetMapping("/descendants/{agentId}")
    public CommonResult<CommonPage<AgentInfoVO>> getAllDescendants(@PathVariable Long agentId) {
        List<AgentInfoVO> agents = agentService.getAllDescendants(agentId);
        return CommonResult.success(CommonPage.restPage(agents));
    }

    @Operation(summary = "会员移线（变更直属上级关系）")
    @PostMapping("/switch-line")
    public CommonResult<DmsLineChangeApplication> switchLine(@RequestBody AgentSwitchLineDTO switchLineDTO) {
        return CommonResult.success(lineChangeApplicationService.submit(switchLineDTO), "移线已执行并记录操作日志");
    }

    @Operation(summary = "查询全部移线记录（限拥有移线管理权限的管理员）")
    @GetMapping("/line-change-applications")
    public CommonResult<List<DmsLineChangeApplication>> listLineChanges(@RequestParam(required = false) Integer status) {
        return CommonResult.success(lineChangeApplicationService.list(status));
    }

    @Operation(summary = "处理旧版待审批移线记录")
    @PostMapping("/line-change-applications/{id}/audit")
    public CommonResult<DmsLineChangeApplication> auditLineChange(@PathVariable Long id, @RequestBody LineChangeAuditDTO dto) {
        return CommonResult.success(lineChangeApplicationService.audit(id, dto));
    }

    @Operation(summary = "生成推广二维码")
    @GetMapping("/qrcode/{agentId}")
    public CommonResult<String> generateQrCode(@PathVariable Long agentId) {
        String qrCodeUrl = agentService.generateQrCodeUrl(agentId);
        return CommonResult.success(qrCodeUrl);
    }

    @Operation(summary = "查询团队成员数")
    @GetMapping("/team-count/{agentId}")
    public CommonResult<Integer> getTeamMemberCount(@PathVariable Long agentId) {
        int count = relationService.getTeamMemberCount(agentId);
        return CommonResult.success(count);
    }

    @Operation(summary = "查询各层级团队成员数")
    @GetMapping("/level-counts/{agentId}")
    public CommonResult<int[]> getLevelMemberCounts(@PathVariable Long agentId) {
        int[] counts = relationService.getLevelMemberCounts(agentId);
        return CommonResult.success(counts);
    }
}
