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
    private static final int MAX_DELIVERY_LIST_RESPONSE_CHARS = 1_048_576;
    private static final int MAX_EXPRESS_PRINT_RESPONSE_CHARS = 4_194_304;

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
                objectMapper.createObjectNode(), false, "微信物流公司列表获取失败",
                MAX_DELIVERY_LIST_RESPONSE_CHARS);
        failOnWeChatError(response, "微信物流公司列表获取失败");
        return parseDeliveryCompanies(response);
    }

    static List<DeliveryCompany> parseDeliveryCompanies(JsonNode response) {
        List<DeliveryCompany> rows = new ArrayList<>();
        // 微信正式接口返回字段为 delivery_list；兼容早期联调响应中的 data，
        // 避免合法物流公司列表被误读为空后导致发货信息无法同步。
        JsonNode data = response.path("delivery_list");
        if (!data.isArray()) data = response.path("data");
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

    @Override
    public WaybillTrackingResult followWaybill(WaybillTrackingCommand command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("openid", command.openId());
        if (command.senderPhone() != null && !command.senderPhone().isBlank()) {
            body.put("sender_phone", command.senderPhone());
        }
        body.put("receiver_phone", command.receiverPhone());
        body.put("delivery_id", command.deliveryId());
        body.put("waybill_id", command.waybillId());
        body.put("trans_id", command.transactionId());
        body.put("order_detail_path", command.orderDetailPath());
        var details = body.putObject("goods_info").putArray("detail_list");
        for (WaybillGoods item : command.goods()) {
            ObjectNode detail = details.addObject();
            detail.put("goods_name", item.name());
            detail.put("goods_img_url", item.imageUrl());
            if (item.description() != null && !item.description().isBlank()) {
                detail.put("goods_desc", item.description());
            }
        }
        JsonNode response = postWithAccessToken("/cgi-bin/express/delivery/open_msg/follow_waybill",
                body, false, "微信物流查询暂时不可用");
        failOnWeChatError(response, "微信物流查询暂时不可用，请稍后重试");
        String token = text(response, "waybill_token");
        if (token == null || token.isBlank()) {
            throw new ApiException("微信物流查询暂时不可用，请稍后重试");
        }
        return new WaybillTrackingResult(token);
    }

    @Override
    public List<ExpressAccount> expressAccounts() {
        requireLoginReady();
        JsonNode response = getWithAccessToken("/cgi-bin/express/business/account/getall", false,
                "微信快递账号获取失败", MAX_DELIVERY_LIST_RESPONSE_CHARS);
        failOnWeChatError(response, "微信快递账号获取失败，请稍后重试");
        List<ExpressAccount> accounts = new ArrayList<>();
        JsonNode rows = response.path("list");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String bizId = text(row, "biz_id");
                String deliveryId = text(row, "delivery_id");
                if (bizId == null || bizId.isBlank() || deliveryId == null || deliveryId.isBlank()) continue;
                List<ExpressServiceType> services = new ArrayList<>();
                JsonNode serviceRows = row.path("service_type");
                if (serviceRows.isArray()) {
                    for (JsonNode service : serviceRows) {
                        String serviceName = text(service, "service_name");
                        if (serviceName != null && !serviceName.isBlank()) {
                            services.add(new ExpressServiceType(service.path("service_type").asInt(0), serviceName));
                        }
                    }
                }
                accounts.add(new ExpressAccount(bizId, deliveryId, text(row, "alias"),
                        row.path("status_code").asInt(-1), row.path("quota_num").asLong(0),
                        List.copyOf(services)));
            }
        }
        return List.copyOf(accounts);
    }

    @Override
    public List<ExpressDeliveryCompany> expressDeliveryCompanies() {
        requireLoginReady();
        JsonNode response = getWithAccessToken("/cgi-bin/express/business/delivery/getall", false,
                "微信快递公司列表获取失败", MAX_DELIVERY_LIST_RESPONSE_CHARS);
        failOnWeChatError(response, "微信快递公司列表获取失败，请稍后重试");
        List<ExpressDeliveryCompany> rows = new ArrayList<>();
        JsonNode data = response.path("data");
        if (data.isArray()) {
            for (JsonNode row : data) {
                String id = text(row, "delivery_id");
                String name = text(row, "delivery_name");
                if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
                    List<ExpressServiceType> services = new ArrayList<>();
                    JsonNode serviceRows = row.path("service_type");
                    if (serviceRows.isArray()) {
                        for (JsonNode service : serviceRows) {
                            String serviceName = text(service, "service_name");
                            if (serviceName != null && !serviceName.isBlank()) {
                                services.add(new ExpressServiceType(service.path("service_type").asInt(0), serviceName));
                            }
                        }
                    }
                    rows.add(new ExpressDeliveryCompany(id, name, row.path("can_use_cash").asInt(0) == 1,
                            text(row, "cash_biz_id"), List.copyOf(services)));
                }
            }
        }
        return List.copyOf(rows);
    }

    @Override
    public ExpressOrderResult createExpressOrder(ExpressOrderCommand command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("add_source", 0);
        body.put("order_id", command.orderId());
        body.put("openid", command.openId());
        body.put("delivery_id", command.deliveryId());
        body.put("biz_id", command.bizId());
        if (command.remark() != null && !command.remark().isBlank()) body.put("custom_remark", command.remark());
        writeExpressAddress(body.putObject("sender"), command.sender());
        writeExpressAddress(body.putObject("receiver"), command.receiver());
        ObjectNode cargo = body.putObject("cargo");
        cargo.put("count", command.packageCount());
        cargo.put("weight", command.weight());
        cargo.put("space_x", command.length());
        cargo.put("space_y", command.width());
        cargo.put("space_z", command.height());
        var cargoDetails = cargo.putArray("detail_list");
        for (ExpressCargoItem item : command.cargoItems()) {
            ObjectNode detail = cargoDetails.addObject();
            detail.put("name", item.name());
            detail.put("count", item.count());
        }
        ObjectNode shop = body.putObject("shop");
        shop.put("wxa_path", command.orderDetailPath());
        var shopDetails = shop.putArray("detail_list");
        for (ExpressShopItem item : command.shopItems()) {
            ObjectNode detail = shopDetails.addObject();
            detail.put("goods_name", item.name());
            detail.put("goods_img_url", item.imageUrl());
            detail.put("goods_desc", item.description());
        }
        body.putObject("insured").put("use_insured", 0).put("insured_value", 0);
        ObjectNode service = body.putObject("service");
        service.put("service_type", command.serviceType());
        service.put("service_name", command.serviceName());
        if (command.expectedPickupTime() != null) body.put("expect_time", command.expectedPickupTime());
        JsonNode response = postWithAccessToken("/cgi-bin/express/business/order/add", body, false,
                "微信快递下单失败");
        failOnWeChatError(response, expressFailureMessage(response, "微信快递下单失败，请检查绑定账号与电子面单余额"));
        String waybillId = text(response, "waybill_id");
        if (waybillId == null || waybillId.isBlank()) throw new ApiException("微信快递未返回运单号，请稍后重试");
        return new ExpressOrderResult(text(response, "order_id"), command.deliveryId(), waybillId, null, null);
    }

    @Override
    public ExpressOrderResult getExpressOrder(ExpressOrderLookup command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("order_id", command.orderId());
        if (command.openId() != null && !command.openId().isBlank()) body.put("openid", command.openId());
        body.put("delivery_id", command.deliveryId());
        if (command.waybillId() != null && !command.waybillId().isBlank()) body.put("waybill_id", command.waybillId());
        if (command.printType() != null) body.put("print_type", command.printType());
        JsonNode response = postWithAccessToken("/cgi-bin/express/business/order/get", body, false,
                "微信运单获取失败", MAX_EXPRESS_PRINT_RESPONSE_CHARS);
        failOnWeChatError(response, "微信运单获取失败，请稍后重试");
        return new ExpressOrderResult(text(response, "order_id"), text(response, "delivery_id"),
                text(response, "waybill_id"), response.has("order_status") ? response.path("order_status").asInt() : null,
                text(response, "print_html"));
    }

    @Override
    public long getExpressQuota(String deliveryId, String bizId) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("delivery_id", deliveryId);
        body.put("biz_id", bizId);
        JsonNode response = postWithAccessToken("/cgi-bin/express/business/quota/get", body, false,
                "微信电子面单余额获取失败");
        failOnWeChatError(response, "微信电子面单余额获取失败，请稍后重试");
        return response.path("quota_num").asLong(0);
    }

    @Override
    public void cancelExpressOrder(ExpressOrderLookup command) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("order_id", command.orderId());
        if (command.openId() != null && !command.openId().isBlank()) body.put("openid", command.openId());
        body.put("delivery_id", command.deliveryId());
        body.put("waybill_id", command.waybillId());
        JsonNode response = postWithAccessToken("/cgi-bin/express/business/order/cancel", body, false,
                "微信快递运单取消失败");
        failOnWeChatError(response, "微信快递运单取消失败，请稍后重试");
    }

    @Override
    public ExpressPathResult getExpressPath(String openId, String deliveryId, String waybillId) {
        requireLoginReady();
        ObjectNode body = objectMapper.createObjectNode();
        if (openId != null && !openId.isBlank()) body.put("openid", openId);
        body.put("delivery_id", deliveryId);
        body.put("waybill_id", waybillId);
        JsonNode response = postWithAccessToken("/cgi-bin/express/business/path/get", body, false,
                "微信物流轨迹获取失败");
        failOnWeChatError(response, "微信物流轨迹获取失败，请稍后重试");
        List<ExpressPathItem> items = new ArrayList<>();
        JsonNode rows = response.path("path_item_list");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                items.add(new ExpressPathItem(row.path("action_time").asLong(0),
                        row.path("action_type").asInt(0), text(row, "action_msg")));
            }
        }
        return new ExpressPathResult(List.copyOf(items));
    }

    private void writeExpressAddress(ObjectNode target, ExpressAddress address) {
        target.put("name", address.name());
        if (address.phone() != null && address.phone().contains("-")) target.put("tel", address.phone());
        else target.put("mobile", address.phone());
        if (address.company() != null && !address.company().isBlank()) target.put("company", address.company());
        target.put("country", "中国");
        target.put("province", address.province());
        target.put("city", address.city());
        target.put("area", address.area());
        target.put("address", address.address());
    }

    private String expressFailureMessage(JsonNode response, String fallback) {
        int code = response.path("errcode").asInt(0);
        if (code == 9300525 || code == 9300531) return "微信快递账号尚未正确绑定，请先到小程序后台完成快递账号配置";
        if (code == 9300510) return "所选快递服务类型不可用，请刷新后重试";
        if (code == 9300503) return "所选快递公司暂不可用，请刷新后重试";
        if (code == 9300535) return "订单商品信息不完整，暂时无法生成微信运单";
        if (code == 9300501 || code == 9300502) return "快递公司下单失败，请稍后重试";
        return fallback;
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
        return postWithAccessToken(path, body, retried, failureMessage, MAX_RESPONSE_CHARS);
    }

    private JsonNode postWithAccessToken(String path, JsonNode body, boolean retried, String failureMessage,
                                         int maxResponseChars) {
        String separator = path.contains("?") ? "&" : "?";
        JsonNode response = postJson(API_ORIGIN + path + separator + "access_token=" + encode(accessToken()),
                body, failureMessage, maxResponseChars);
        int errorCode = response.path("errcode").asInt(0);
        if (!retried && (errorCode == 40001 || errorCode == 40014 || errorCode == 42001)) {
            cachedAccessToken = null;
            return postWithAccessToken(path, body, true, failureMessage, maxResponseChars);
        }
        return response;
    }

    private JsonNode getWithAccessToken(String path, boolean retried, String failureMessage,
                                        int maxResponseChars) {
        String separator = path.contains("?") ? "&" : "?";
        JsonNode response = getJson(API_ORIGIN + path + separator + "access_token=" + encode(accessToken()),
                failureMessage, maxResponseChars);
        int errorCode = response.path("errcode").asInt(0);
        if (!retried && (errorCode == 40001 || errorCode == 40014 || errorCode == 42001)) {
            cachedAccessToken = null;
            return getWithAccessToken(path, true, failureMessage, maxResponseChars);
        }
        return response;
    }

    private JsonNode getJson(String url, String failureMessage) {
        return getJson(url, failureMessage, MAX_RESPONSE_CHARS);
    }

    private JsonNode getJson(String url, String failureMessage, int maxResponseChars) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(bounded(properties.getReadTimeoutMs(), 10000)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, failureMessage, maxResponseChars);
    }

    private JsonNode postJson(String url, JsonNode body, String failureMessage) {
        return postJson(url, body, failureMessage, MAX_RESPONSE_CHARS);
    }

    private JsonNode postJson(String url, JsonNode body, String failureMessage, int maxResponseChars) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(bounded(properties.getReadTimeoutMs(), 10000)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request, failureMessage, maxResponseChars);
    }

    private JsonNode send(HttpRequest request, String failureMessage, int maxResponseChars) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body() == null || response.body().length() > maxResponseChars) {
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

    private static String text(JsonNode node, String field) {
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
