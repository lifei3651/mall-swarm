package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface ExternalTeamMigrationService {
    ImportResultVO migrate(MultipartFile file, Long anchorAgentId);
}
