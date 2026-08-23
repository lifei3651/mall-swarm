package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsLiveRoomDao;
import com.macro.mall.distribution.dto.TencentLiveCallbackDTO;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** 腾讯云推流/断流事件回调，只根据签名和服务器保存的 streamName 推进直播状态。 */
@Service
@RequiredArgsConstructor
public class LiveCallbackService {

    private final DmsLiveRoomDao liveRoomDao;
    private final ShopCatalogCacheService catalogCache;
    private final OperationLogService operationLogService;

    @Value("${shop.live.tencent.callback-auth-key:}")
    private String callbackAuthKey;

    @Transactional(rollbackFor = Exception.class)
    public boolean handleTencent(TencentLiveCallbackDTO dto) {
        if (callbackAuthKey == null || callbackAuthKey.length() < 16) Asserts.fail("腾讯云直播回调尚未启用");
        long now = Instant.now().getEpochSecond();
        if (dto.getT() < now || dto.getT() > now + 900) Asserts.fail("直播回调已过期或时间不正确");
        String expected = md5(callbackAuthKey + dto.getT());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                dto.getSign().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            Asserts.fail("直播回调签名校验失败");
        }
        if (dto.getEventType() != 0 && dto.getEventType() != 1) return true;
        DmsLiveRoom room = liveRoomDao.selectByStreamNameForUpdate(dto.getStreamId());
        if (room == null || !"TENCENT".equalsIgnoreCase(room.getProviderCode())) return true;
        int affected = dto.getEventType() == 1
                ? liveRoomDao.markStreamConnected(room.getTenantId(), room.getId())
                : liveRoomDao.markStreamDisconnected(room.getTenantId(), room.getId());
        if (affected > 0) {
            catalogCache.invalidateAfterCommit(room.getTenantId());
            operationLogService.log("LIVE_ROOM", dto.getEventType() == 1 ? "STREAM_CONNECTED" : "STREAM_DISCONNECTED",
                    "LIVE_ROOM", String.valueOf(room.getId()), null,
                    "stream=" + room.getStreamName(), dto.getEventType() == 1 ? "腾讯云确认推流" : "腾讯云确认断流");
        }
        return true;
    }

    private String md5(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("直播回调签名计算失败", exception);
        }
    }
}
