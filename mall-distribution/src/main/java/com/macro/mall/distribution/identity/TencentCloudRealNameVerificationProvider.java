package com.macro.mall.distribution.identity;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.RealNameVerificationProperties;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.faceid.v20180301.FaceidClient;
import com.tencentcloudapi.faceid.v20180301.models.IdCardVerificationRequest;
import com.tencentcloudapi.faceid.v20180301.models.IdCardVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TencentCloudRealNameVerificationProvider implements RealNameVerificationProvider {
    private final RealNameVerificationProperties properties;

    @Override
    public RealNameVerificationResult verify(String realName, String idCard) {
        if (!properties.isReady()) Asserts.fail("实名认证服务暂未开通，请联系客服");
        try {
            Credential credential = new Credential(properties.getSecretId().trim(), properties.getSecretKey().trim());
            HttpProfile http = new HttpProfile();
            http.setEndpoint(properties.getEndpoint().trim());
            http.setConnTimeout(Math.max(1, properties.getConnectTimeoutSeconds()));
            http.setReadTimeout(Math.max(1, properties.getReadTimeoutSeconds()));
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(http);
            FaceidClient client = new FaceidClient(credential, properties.getRegion(), clientProfile);
            IdCardVerificationRequest request = new IdCardVerificationRequest();
            request.setName(realName);
            request.setIdCard(idCard);
            IdCardVerificationResponse response = client.IdCardVerification(request);
            String code = response.getResult() == null ? "UNKNOWN" : response.getResult();
            return new RealNameVerificationResult("0".equals(code), code, response.getRequestId());
        } catch (TencentCloudSDKException exception) {
            // 不记录异常正文，避免上游 SDK 在错误信息中夹带请求字段；统一返回脱敏结果供审计计数。
            return new RealNameVerificationResult(false, "PROVIDER_ERROR", null);
        }
    }
}
