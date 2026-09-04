package com.macro.mall.distribution.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OfficialWeChatMiniProgramGateway implements WeChatMiniProgramGateway {

    private static final String API_ORIGIN = "https://api.weixin.qq.com";
    private static final int MAX_RESPONSE_CHARS = 65_536;

    private final WeChatMiniProgramProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile AccessToken cachedAccessToken;

    public OfficialWeChatMiniProgramGateway(WeChatMiniProgramProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(bounded(properties.getConnectTimeoutMs(), 5000)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public LoginIdentity exchangeLoginCode(String code) {
        requireLoginReady();
        String requestUrl = API_ORIGIN + "/sns/jscode2session?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret())
                + "&js_code=" + encode(code) + "&grant_type=authorization_code";
        JsonNode response = getJson(requestUrl, "微信登录凭证校验失败");
        failOnWeChatError(response, "微信登录暂时不可用，请稍后重试");
        String openId = text(response, "openid");
        if (openId == null || openId.isBlank()) {
            throw new ApiException("微信登录暂时不可用，请稍后重试");
        }
        return new LoginIdentity(openId, text(response, "unionid"));
    }

    @Override
    public PhoneNumber exchangePhoneCode(String code) {
        if (!properties.phoneAuthorizationReady()) {
            throw new ApiException("当前客户尚未开通微信手机号快捷验证");
        }
        return exchangePhoneCode(code, false);
    }

    @Override
    public SubscribeMessageResult sendSubscribeMessage(SubscribeMessageCommand command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("touser", command.openId());
        body.put("template_id", command.templateId());
        if (command.page() != null && !command.page().isBlank()) body.put("page", command.page());
        body.put("miniprogram_state", command.miniProgramState());
        body.put("lang", "zh_CN");
        ObjectNode data = body.putObject("data");
        for (Map.Entry<String, String> entry : command.data().entrySet()) {
            data.putObject(entry.getKey()).put("value", entry.getValue());
        }
        JsonNode response = postWithAccessToken("/cgi-bin/message/subscribe/send", body, false,
                "微信订阅消息发送失败");
        return new SubscribeMessageResult(response.path("errcode").asInt(0));
    }

    @Override
    public ShippingInfoResult uploadShippingInfo(ShippingInfoCommand command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode orderKey = body.putObject("order_key");
        orderKey.put("order_number_type", 1);
        orderKey.put("mchid", command.merchantId());
        orderKey.put("out_trade_no", command.paymentOrderNo());
        body.put("logistics_type", 1);
        body.put("delivery_mode", 2);
        body.put("is_all_delivered", command.allDelivered());
        var shippingList = body.putArray("shipping_list");
        for (ShippingItem item : command.shipments()) {
            ObjectNode row = shippingList.addObject();
            row.put("tracking_no", item.trackingNo());
            row.put("express_company", item.expressCompany());
            row.put("item_desc", item.itemDescription());
            if (item.receiverContact() != null && !item.receiverContact().isBlank()) {
                row.putObject("contact").put("receiver_contact", item.receiverContact());
            }
        }
        body.put("upload_time", OffsetDateTime.now(ZoneOffset.ofHours(8))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        body.putObject("payer").put("openid", command.openId());
        JsonNode response = postWithAccessToken("/wxa/sec/order/upload_shipping_info", body, false,
                "微信发货信息同步失败");
        return new ShippingInfoResult(response.path("errcode").asInt(0));
    }

    @Override
    public List<DeliveryCompany> deliveryCompanies() {
        requireLoginReady();
        JsonNode response = postWithAccessToken("/cgi-bin/express/delivery/open_msg/get_delivery_list",
                objectMapper.createObjectNode(), false, "微信物流公司列表获取失败");
        failOnWeChatError(response, "微信物流公司列表获取失败");
        List<DeliveryCompany> rows = new ArrayList<>();
        JsonNode data = response.path("data");
        if (data.isArray()) {
            for (JsonNode row : data) {
                String id = text(row, "delivery_id");
                String name = text(row, "delivery_name");
                if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
                    rows.add(new DeliveryCompany(id, name));
                }
            }
        }
        return List.copyOf(rows);
    }

    private PhoneNumber exchangePhoneCode(String code, boolean retried) {
        String token = accessToken();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", code);
        JsonNode response = postJson(API_ORIGIN + "/wxa/business/getuserphonenumber?access_token="
                + encode(token), body, "微信手机号验证失败");
        int errorCode = response.path("errcode").asInt(0);
        if (!retried && (errorCode == 40014 || errorCode == 42001)) {
            cachedAccessToken = null;
            return exchangePhoneCode(code, true);
        }
        failOnWeChatError(response, "微信手机号验证暂时不可用，请稍后重试");
        JsonNode phoneInfo = response.path("phone_info");
        String phone = text(phoneInfo, "purePhoneNumber");
        String countryCode = text(phoneInfo, "countryCode");
        if (phone == null || phone.isBlank()) {
            throw new ApiException("未能取得微信绑定手机号，请重新授权");
        }
        return new PhoneNumber(phone, countryCode);
    }

    private String accessToken() {
        AccessToken current = cachedAccessToken;
        if (current != null && current.validAt(Instant.now())) return current.value();
        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && current.validAt(Instant.now())) return current.value();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("grant_type", "client_credential");
            body.put("appid", properties.getAppId());
            body.put("secret", properties.getAppSecret());
            body.put("force_refresh", false);
            JsonNode response = postJson(API_ORIGIN + "/cgi-bin/stable_token", body,
                    "微信接口调用凭证获取失败");
            failOnWeChatError(response, "微信手机号验证暂时不可用，请稍后重试");
            String value = text(response, "access_token");
            long expiresIn = Math.max(300, response.path("expires_in").asLong(7200));
            if (value == null || value.isBlank()) {
                throw new ApiException("微信手机号验证暂时不可用，请稍后重试");
            }
            current = new AccessToken(value, Instant.now().plusSeconds(Math.max(60, expiresIn - 300)));
            cachedAccessToken = current;
            return current.value();
        }
    }

    private JsonNode postWithAccessToken(String path, JsonNode body, boolean retried, String failureMessage) {
        String separator = path.contains("?") ? "&" : "?";
        JsonNode response = postJson(API_ORIGIN + path + separator + "access_token=" + encode(accessToken()),
                body, failureMessage);
        int errorCode = response.path("errcode").asInt(0);
        if (!retried && (errorCode == 40001 || errorCode == 40014 || errorCode == 42001)) {
            cachedAccessToken = null;
            return postWithAccessToken(path, body, true, failureMessage);
        }
        return response;
    }

    private JsonNode getJson(String url, String failureMessage) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(bounded(properties.getReadTimeoutMs(), 10000)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, failureMessage);
    }

    private JsonNode postJson(String url, JsonNode body, String failureMessage) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(bounded(properties.getReadTimeoutMs(), 10000)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request, failureMessage);
    }

    private JsonNode send(HttpRequest request, String failureMessage) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().length() > MAX_RESPONSE_CHARS) {
                log.warn("微信小程序接口异常: status={}", response.statusCode());
                throw new ApiException(failureMessage);
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(failureMessage, exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException(failureMessage, exception);
        }
    }

    private void failOnWeChatError(JsonNode response, String publicMessage) {
        int errorCode = response.path("errcode").asInt(0);
        if (errorCode != 0) {
            log.warn("微信小程序接口返回失败: errcode={}", errorCode);
            throw new ApiException(publicMessage);
        }
    }

    private void requireLoginReady() {
        if (!properties.loginReady()) {
            throw new ApiException("当前客户尚未开通微信小程序登录");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private int bounded(int value, int fallback) {
        return value < 500 || value > 30_000 ? fallback : value;
    }

    private record AccessToken(String value, Instant usableUntil) {
        boolean validAt(Instant now) {
            return usableUntil != null && usableUntil.isAfter(now);
        }
    }
}
