package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.LiveRoomSaveDTO;
import com.macro.mall.distribution.vo.LiveRoomVO;

import java.util.List;

public interface LiveRoomService {

    List<LiveRoomVO> listPublic(int limit);

    List<LiveRoomVO> listPublic(Long tenantId, int limit);

    LiveRoomVO getPublic(Long id);

    List<LiveRoomVO> listAdmin(Integer status);

    LiveRoomVO save(Long id, LiveRoomSaveDTO dto);

    boolean updateStatus(Long id, Integer status);
}
