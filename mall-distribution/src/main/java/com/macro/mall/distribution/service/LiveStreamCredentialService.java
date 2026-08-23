package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.vo.LiveStreamCredentialVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 云直播地址签发适配层。基座先支持腾讯云标准直播和外部固定视频源，后续可增加阿里云实现而不改业务层。
 */
@Service
public class LiveStreamCredentialService {

    private final String defaultProvider;
    private final String pushDomain;
    private final String playDomain;
    private final String appName;
    private final String pushAuthKey;
    private final int credentialSeconds;

    public LiveStreamCredentialService(
            @Value("${shop.live.provider:EXTERNAL}") String defaultProvider,
            @Value("${shop.live.tencent.push-domain:}") String pushDomain,
            @Value("${shop.live.tencent.play-domain:}") String playDomain,
            @Value("${shop.live.tencent.app-name:live}") String appName,
            @Value("${shop.live.tencent.push-auth-key:}") String pushAuthKey,
            @Value("${shop.live.tencent.credential-seconds:7200}") int credentialSeconds) {
        this.defaultProvider = normalizeProvider(defaultProvider);
        this.pushDomain = trim(pushDomain);
        this.playDomain = trim(playDomain);
        this.appName = trim(appName) == null ? "live" : trim(appName);
        this.pushAuthKey = trim(pushAuthKey);
        this.credentialSeconds = Math.max(600, Math.min(86400, credentialSeconds));
    }

    public LiveStreamCredentialVO issue(DmsLiveRoom room) {
        String provider = normalizeProvider(room.getProviderCode() == null ? defaultProvider : room.getProviderCode());
        String streamName = room.getStreamName();
        if (streamName == null || streamName.isBlank()) Asserts.fail("直播流标识尚未生成");
        if ("TENCENT".equals(provider)) return issueTencent(room, streamName);

        if (room.getWatchUrl() == null || room.getWatchUrl().isBlank()) {
            Asserts.fail("外部视频源尚未配置观看地址");
        }
        LiveStreamCredentialVO result = base(room, provider, streamName);
        result.setPlaybackUrl(room.getWatchUrl());
        result.setInstructions("当前直播间使用外部视频源，请在厂家摄像设备或已配置的直播工具中开播；商城负责观看、互动和统计。");
        return result;
    }

    public boolean isTencentReady() {
        return pushDomain != null && playDomain != null && pushAuthKey != null;
    }

    private LiveStreamCredentialVO issueTencent(DmsLiveRoom room, String streamName) {
        if (!isTencentReady()) {
            Asserts.fail("腾讯云直播尚未完成域名和推流鉴权配置，请联系平台管理员");
        }
        long expireEpoch = Instant.now().plusSeconds(credentialSeconds).getEpochSecond();
        String txTime = Long.toHexString(expireEpoch).toUpperCase(Locale.ROOT);
        String txSecret = md5(pushAuthKey + streamName + txTime);
        String pushUrl = "rtmp://" + pushDomain + "/" + appName + "/" + streamName
                + "?txSecret=" + txSecret + "&txTime=" + txTime;
        String playbackUrl = "https://" + playDomain + "/" + appName + "/" + streamName + ".m3u8";

        LiveStreamCredentialVO result = base(room, "TENCENT", streamName);
        result.setPushUrl(pushUrl);
        result.setPlaybackUrl(playbackUrl);
        result.setExpireTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(expireEpoch), ZoneId.systemDefault()));
        result.setInstructions("请在灵启主播端或已授权推流工具中使用本次短时推流地址；地址到期后需重新开始直播获取，禁止转发给他人。");
        return result;
    }

    private LiveStreamCredentialVO base(DmsLiveRoom room, String provider, String streamName) {
        LiveStreamCredentialVO result = new LiveStreamCredentialVO();
        result.setRoomId(room.getId());
        result.setProviderCode(provider);
        result.setStreamName(streamName);
        return result;
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("直播地址签发失败", exception);
        }
    }

    private static String normalizeProvider(String value) {
        String normalized = value == null ? "EXTERNAL" : value.trim().toUpperCase(Locale.ROOT);
        return "TENCENT".equals(normalized) ? "TENCENT" : "EXTERNAL";
    }

    private static String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
