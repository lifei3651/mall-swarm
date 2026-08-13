#!/usr/bin/env bash
set -euo pipefail

# 在已经清成“零客户业务数据”的测试商城基座上，使用真实 HTTP 接口验证：
# 后台创建会员、后台加余额、余额下单、重复/并发支付、分次退款、奖金冲减和全额退款退会。
# 本脚本不负责最终清库；验收完成后必须再次执行 20260813_reset_commerce_foundation.sql。

if [[ "${CONFIRM_BONUS_SANDBOX:-}" != "YES" ]]; then
  echo "拒绝执行：请显式设置 CONFIRM_BONUS_SANDBOX=YES"
  exit 2
fi

db_name="${MALL_DB_NAME:-mall_distribution}"
api_base="${MALL_API_BASE:-http://127.0.0.1:8086}"
mysql_cmd=(mysql --protocol=socket -uroot "$db_name" -N -B)
test_prefix="codex_bonus_"
admin_username="codex_bonus_admin"
temp_dir="$(mktemp -d /tmp/lingqimall-bonus-smoke.XXXXXX)"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
payload_encrypt_helper="${PAYLOAD_ENCRYPT_HELPER:-${script_dir}/payload-encrypt.py}"

sql_value() {
  "${mysql_cmd[@]}" -e "$1"
}

assert_equal() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "验收失败：${label}，期望 ${expected}，实际 ${actual}"
    exit 1
  fi
  echo "通过：${label}"
}

assert_success() {
  local response="$1"
  local label="$2"
  if ! jq -e '.code == 200' >/dev/null <<<"$response"; then
    echo "接口失败：${label}"
    jq -c '{code: .code, message: (.message // .msg // "未知错误")}' <<<"$response" || true
    exit 1
  fi
}

admin_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local args=(--http1.1 -sS --max-time 30 -X "$method"
    -H "Authorization: Bearer ${admin_token}"
    -H 'Content-Type: application/json')
  if [[ -n "$body" ]]; then
    local encrypted_envelope challenge_id encrypted_key
    encrypted_envelope="$(printf '%s' "$body" | "$payload_encrypt_helper" "$api_base")"
    body="$(jq -c '.body' <<<"$encrypted_envelope")"
    challenge_id="$(jq -r '.challengeId' <<<"$encrypted_envelope")"
    encrypted_key="$(jq -r '.encryptedKey' <<<"$encrypted_envelope")"
    if [[ -n "$challenge_id" ]]; then
      args+=(-H "X-Payload-Encryption-Id: ${challenge_id}" -H "X-Payload-Encryption-Key: ${encrypted_key}")
    fi
    args+=(-d "$body")
  fi
  curl "${args[@]}" "${api_base}${path}"
}

member_request() {
  local token="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local idempotency_key="${5:-}"
  local args=(--http1.1 -sS --max-time 30 -X "$method"
    -H "Authorization: Bearer ${token}"
    -H 'Content-Type: application/json')
  if [[ -n "$idempotency_key" ]]; then
    args+=(-H "X-Idempotency-Key: ${idempotency_key}")
  fi
  if [[ -n "$body" ]]; then
    local encrypted_envelope challenge_id encrypted_key
    encrypted_envelope="$(printf '%s' "$body" | "$payload_encrypt_helper" "$api_base")"
    body="$(jq -c '.body' <<<"$encrypted_envelope")"
    challenge_id="$(jq -r '.challengeId' <<<"$encrypted_envelope")"
    encrypted_key="$(jq -r '.encryptedKey' <<<"$encrypted_envelope")"
    if [[ -n "$challenge_id" ]]; then
      args+=(-H "X-Payload-Encryption-Id: ${challenge_id}" -H "X-Payload-Encryption-Key: ${encrypted_key}")
    fi
    args+=(-d "$body")
  fi
  curl "${args[@]}" "${api_base}${path}"
}

cleanup_credentials() {
  set +e
  "${mysql_cmd[@]}" -e "
    DELETE FROM dms_shop_member_session
     WHERE member_id IN (SELECT id FROM dms_shop_member WHERE login_account LIKE '${test_prefix}%');
    DELETE FROM dms_admin_session WHERE username = '${admin_username}';
    DELETE FROM dms_admin_user WHERE username = '${admin_username}';
  " >/dev/null 2>&1
  find "$temp_dir" -type f -delete >/dev/null 2>&1
  rmdir "$temp_dir" >/dev/null 2>&1
}
trap cleanup_credentials EXIT

foundation_state="$(sql_value "
  SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    FORMAT((SELECT COALESCE(SUM(balance), 0) FROM dms_member_asset_account), 2)
  );
")"
assert_equal "0:0:0:0:0.00" "$foundation_state" "测试前商城处于零客户业务基座"

if ! curl --http1.1 -fsS --max-time 10 "${api_base}/actuator/health" | jq -e '.status == "UP"' >/dev/null; then
  echo "验收失败：商城后端健康检查未通过"
  exit 1
fi

# 生成只在本次进程内存在的测试凭据，数据库最终会删除测试管理员和所有会员会话。
admin_password="$(openssl rand -hex 18)"
admin_salt="$(openssl rand -hex 16)"
admin_token="$(openssl rand -hex 32)"
member_password="$(od -An -N4 -tu4 /dev/urandom | awk '{printf "%06d", $1 % 1000000}')"

"${mysql_cmd[@]}" -e "
  DELETE FROM dms_admin_session WHERE username = '${admin_username}';
  DELETE FROM dms_admin_user WHERE username = '${admin_username}';
  INSERT INTO dms_admin_user
    (username, password_hash, salt, nickname, role_code, permissions, status,
     failed_login_count, lock_time, create_time, update_time)
  VALUES
    ('${admin_username}', SHA2(CONCAT('${admin_password}', ':', '${admin_salt}'), 256),
     '${admin_salt}', '奖金沙盘临时管理员', 'SUPER_ADMIN', '*', 1, 0, NULL, NOW(), NOW());
  INSERT INTO dms_admin_session
    (admin_id, username, token, status, expire_time, create_time, update_time)
  SELECT id, username, SHA2('${admin_token}', 256), 1, DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), NOW()
    FROM dms_admin_user WHERE username = '${admin_username}';
"
admin_id="$(sql_value "SELECT id FROM dms_admin_user WHERE username = '${admin_username}'")"

create_member() {
  local phone="$1"
  local username="$2"
  local nickname="$3"
  local inviter_user_id="${4:-}"
  local activate="${5:-false}"
  local level="${6:-1}"
  local body
  if [[ -n "$inviter_user_id" ]]; then
    body="$(jq -n --arg phone "$phone" --arg username "$username" --arg password "$member_password" \
      --arg nickname "$nickname" --argjson inviterUserId "$inviter_user_id" \
      --argjson activateDistribution "$activate" --argjson initialLevel "$level" \
      '{phone:$phone,username:$username,password:$password,nickname:$nickname,inviterUserId:$inviterUserId,activateDistribution:$activateDistribution,initialLevel:$initialLevel,reason:"奖金沙盘验收"}')"
  else
    body="$(jq -n --arg phone "$phone" --arg username "$username" --arg password "$member_password" \
      --arg nickname "$nickname" --argjson activateDistribution "$activate" --argjson initialLevel "$level" \
      '{phone:$phone,username:$username,password:$password,nickname:$nickname,activateDistribution:$activateDistribution,initialLevel:$initialLevel,reason:"奖金沙盘验收"}')"
  fi
  local response
  response="$(admin_request POST /shop/admin/members "$body")"
  assert_success "$response" "创建会员 ${username}"
}

create_member 13900000001 codex_bonus_a 沙盘董事A "" true 5
a_user_id="$(sql_value "SELECT user_id FROM dms_shop_member WHERE login_account = 'codex_bonus_a'")"
create_member 13900000002 codex_bonus_b 沙盘VIP会员B "$a_user_id" true 2
b_user_id="$(sql_value "SELECT user_id FROM dms_shop_member WHERE login_account = 'codex_bonus_b'")"
create_member 13900000003 codex_bonus_c 沙盘购买人C "$b_user_id" false 1
c_member_id="$(sql_value "SELECT id FROM dms_shop_member WHERE login_account = 'codex_bonus_c'")"
c_user_id="$(sql_value "SELECT user_id FROM dms_shop_member WHERE id = ${c_member_id}")"
a_agent_id="$(sql_value "SELECT id FROM dms_agent WHERE user_id = ${a_user_id}")"
b_agent_id="$(sql_value "SELECT id FROM dms_agent WHERE user_id = ${b_user_id}")"

assert_equal "5:1" "$(sql_value "SELECT CONCAT(agent_level, ':', status) FROM dms_agent WHERE id = ${a_agent_id}")" "A为启用的一星董事"
assert_equal "2:1" "$(sql_value "SELECT CONCAT(agent_level, ':', status) FROM dms_agent WHERE id = ${b_agent_id}")" "B为启用的VIP会员"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_agent WHERE user_id = ${c_user_id}")" "C支付前尚未进入奖金体系"

issue_balance() {
  local user_id="$1"
  local amount="$2"
  local issue_label="$3"
  local request_id
  request_id="$(cat /proc/sys/kernel/random/uuid)"
  local body
  body="$(jq -n --argjson userId "$user_id" --argjson amount "$amount" --arg requestId "$request_id" \
    --arg adminPassword "$admin_password" --arg issueLabel "$issue_label" \
    '{userId:$userId,amount:$amount,bizType:"BONUS_SANDBOX_ISSUE",bizId:$issueLabel,requestId:$requestId,remark:("奖金沙盘测试加余额："+$issueLabel),adminPassword:$adminPassword}')"
  local response
  response="$(admin_request POST /distribution/assets/issue "$body")"
  assert_success "$response" "后台给 ${issue_label} 增加余额"
}

create_member_session() {
  local member_id="$1"
  local user_id="$2"
  local token
  token="$(openssl rand -hex 32)"
  "${mysql_cmd[@]}" -e "
    UPDATE dms_shop_member SET pay_password_hash = password_hash,
      pay_password_failed_count = 0, pay_password_lock_time = NULL
     WHERE id = ${member_id};
    INSERT INTO dms_shop_member_session
      (member_id, user_id, token, status, expire_time, create_time, update_time)
    VALUES
      (${member_id}, ${user_id}, SHA2('${token}', 256), 1, DATE_ADD(NOW(), INTERVAL 2 HOUR), NOW(), NOW());
  "
  printf '%s' "$token"
}

create_address() {
  local token="$1"
  local username="$2"
  local phone="$3"
  local response
  response="$(member_request "$token" POST /shop/addresses \
    "$(jq -n --arg receiverName "$username" --arg receiverPhone "$phone" \
      '{receiverName:$receiverName,receiverPhone:$receiverPhone,province:"广东省",city:"深圳市",district:"南山区",detailAddress:"奖金沙盘测试地址（测试完成即删除）",isDefault:1}')")"
  assert_success "$response" "${username} 保存收货地址"
}

submit_order() {
  local token="$1"
  local address_id="$2"
  local quantity="$3"
  local remark="$4"
  local idempotency_key="$5"
  local body
  body="$(jq -n --argjson addressId "$address_id" --argjson quantity "$quantity" --arg remark "$remark" \
    '{addressId:$addressId,payType:"BALANCE",businessType:"NORMAL",remark:$remark,items:[{productId:2,skuId:3,quantity:$quantity}]}')"
  local response
  response="$(member_request "$token" POST /shop/orders "$body" "$idempotency_key")"
  assert_success "$response" "提交订单 ${remark}"
}

apply_and_approve_refund() {
  local token="$1"
  local order_id="$2"
  local order_item_id="$3"
  local quantity="$4"
  local reason="$5"
  local body response after_sale_id audit_body
  body="$(jq -n --argjson orderId "$order_id" --argjson orderItemId "$order_item_id" \
    --argjson quantity "$quantity" --arg reason "$reason" \
    '{orderId:$orderId,applyType:1,items:[{orderItemId:$orderItemId,quantity:$quantity}],reason:$reason}')"
  response="$(member_request "$token" POST /shop/after-sales "$body")"
  assert_success "$response" "会员申请退款 ${reason}"
  after_sale_id="$(sql_value "SELECT id FROM dms_shop_after_sale WHERE order_id = ${order_id} AND reason = '${reason}' ORDER BY id DESC LIMIT 1")"
  audit_body="$(jq -n --argjson auditUserId "$admin_id" '{status:1,auditRemark:"奖金沙盘自动审核",auditUserId:$auditUserId,auditUserName:"奖金沙盘临时管理员"}')"
  response="$(admin_request PUT "/shop/admin/after-sales/${after_sale_id}/audit" "$audit_body")"
  assert_success "$response" "后台审核退款 ${reason}"
}

# 场景一：1000元真实余额订单，冻结支付时关系和级别，产生30%直推奖+5%董事分红。
issue_balance "$c_user_id" 2000 C
c_token="$(create_member_session "$c_member_id" "$c_user_id")"
create_address "$c_token" codex_bonus_c 13900000003
c_address_id="$(sql_value "SELECT id FROM dms_shop_address WHERE member_id = ${c_member_id} AND status = 1 ORDER BY id DESC LIMIT 1")"
submit_order "$c_token" "$c_address_id" 10 BONUS-SANDBOX-PARTIAL order-c-submit-20260813
c_order_id="$(sql_value "SELECT id FROM dms_shop_order WHERE user_id = ${c_user_id} AND remark = 'BONUS-SANDBOX-PARTIAL' ORDER BY create_time DESC LIMIT 1")"
c_item_id="$(sql_value "SELECT id FROM dms_shop_order_item WHERE order_id = ${c_order_id} ORDER BY id LIMIT 1")"

pay_body="$(jq -n --arg paymentPassword "$member_password" '{paymentPassword:$paymentPassword}')"
c_pay_key="order-c-pay-20260813"
response="$(member_request "$c_token" POST "/shop/wallet/orders/${c_order_id}/pay" "$pay_body" "$c_pay_key")"
assert_success "$response" "C使用后台增加的余额支付1000元订单"

c_agent_id="$(sql_value "SELECT id FROM dms_agent WHERE user_id = ${c_user_id}")"
assert_equal "2:1" "$(sql_value "SELECT CONCAT(agent_level, ':', status) FROM dms_agent WHERE id = ${c_agent_id}")" "C首笔10件有效支付后激活并按件数晋升VIP"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 2 FROM dms_order_relation_snapshot WHERE order_id = ${c_order_id} AND relation_level >= 1")" "订单冻结B、A两层上级关系"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_commission_record WHERE order_id = ${c_order_id} AND agent_id = ${b_agent_id} AND bonus_type = 'DIRECT_REWARD' AND commission_rate = 0.30 AND commission_amount = 300.00 AND status = 0")" "B按支付前VIP级别获得30%直推奖"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_commission_record WHERE order_id = ${c_order_id} AND agent_id = ${a_agent_id} AND bonus_type = 'DIRECTOR_SHARE' AND commission_rate = 0.05 AND commission_amount = 50.00 AND status = 0")" "A获得5%董事团队分红"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id = ${c_order_id} AND agent_id = ${c_agent_id}")" "购买人不向自己发奖金"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_order_finance WHERE order_id = ${c_order_id} AND pay_amount = 1000.00 AND product_cost = 100.00 AND bonus_amount = 350.00 AND company_profit = 550.00 AND risk_status = 0")" "实付、成本、奖金、公司留存和风控对平"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 2 AND SUM(current_amount) = 650.00 FROM dms_order_balance_allocation WHERE order_id = ${c_order_id}")" "产品成本100元与剩余商品款550元归集凭证对平"
assert_equal "1000.00" "$(sql_value "SELECT balance FROM dms_member_asset_account WHERE user_id = ${c_user_id} AND asset_code = 'CASH_BONUS'")" "C余额只扣除一次1000元"
assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_member_asset_flow WHERE user_id = ${c_user_id} AND biz_type = 'ORDER_BALANCE_PAYMENT' AND biz_id = '${c_order_id}'")" "余额支付流水唯一"

# 同请求号由幂等层拦截；换请求号再次点击则由订单行锁/状态机返回原订单，不重复扣款或发奖。
duplicate_response="$(member_request "$c_token" POST "/shop/wallet/orders/${c_order_id}/pay" "$pay_body" "$c_pay_key")"
if jq -e '.code == 200' >/dev/null <<<"$duplicate_response"; then
  echo "验收失败：相同支付请求号没有被幂等层拦截"
  exit 1
fi
response="$(member_request "$c_token" POST "/shop/wallet/orders/${c_order_id}/pay" "$pay_body" order-c-pay-retry-20260813)"
assert_success "$response" "不同请求号重复点击由订单状态机安全返回"
assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_member_asset_flow WHERE user_id = ${c_user_id} AND biz_type = 'ORDER_BALANCE_PAYMENT' AND biz_id = '${c_order_id}'")" "重复支付后仍只有一笔扣款"
assert_equal "2" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id = ${c_order_id}")" "重复支付后仍只有两笔应发奖金"

# 三次退款30%、30%、40%，每次按累计退款比例只追回差额。
apply_and_approve_refund "$c_token" "$c_order_id" "$c_item_id" 3 BONUS-SANDBOX-REFUND-30A
assert_equal "210.00:35.00:105.00:1300.00" "$(sql_value "SELECT CONCAT((SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${b_agent_id}),':',(SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${a_agent_id}),':',(SELECT SUM(clawback_amount) FROM dms_commission_clawback WHERE order_id=${c_order_id}),':',(SELECT balance FROM dms_member_asset_account WHERE user_id=${c_user_id} AND asset_code='CASH_BONUS'))")" "退款30%后奖金和买家余额按比例变化"

apply_and_approve_refund "$c_token" "$c_order_id" "$c_item_id" 3 BONUS-SANDBOX-REFUND-30B
assert_equal "120.00:20.00:210.00:1600.00" "$(sql_value "SELECT CONCAT((SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${b_agent_id}),':',(SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${a_agent_id}),':',(SELECT SUM(clawback_amount) FROM dms_commission_clawback WHERE order_id=${c_order_id}),':',(SELECT balance FROM dms_member_asset_account WHERE user_id=${c_user_id} AND asset_code='CASH_BONUS'))")" "累计退款60%时不重复追回历史部分"

apply_and_approve_refund "$c_token" "$c_order_id" "$c_item_id" 4 BONUS-SANDBOX-REFUND-40
assert_equal "0.00:3:0.00:3:350.00:2000.00" "$(sql_value "SELECT CONCAT((SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${b_agent_id}),':',(SELECT status FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${b_agent_id}),':',(SELECT commission_amount FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${a_agent_id}),':',(SELECT status FROM dms_commission_record WHERE order_id=${c_order_id} AND agent_id=${a_agent_id}),':',(SELECT SUM(clawback_amount) FROM dms_commission_clawback WHERE order_id=${c_order_id}),':',(SELECT balance FROM dms_member_asset_account WHERE user_id=${c_user_id} AND asset_code='CASH_BONUS'))")" "全额退款后奖金归零、状态退款完成、余额原路退回"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_order_finance WHERE order_id = ${c_order_id} AND refund_amount = 1000.00 AND net_pay_amount = 0.00 AND bonus_amount = 0.00")" "全额退款后订单净实付和净奖金均为零"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_agent WHERE user_id = ${c_user_id} AND status = 1")" "首单全退且无其他有效订单后C退出奖金体系"
assert_equal "0.00" "$(sql_value "SELECT FORMAT(COALESCE(SUM(performance_amount),0),2) FROM dms_order_performance_detail WHERE order_id = ${c_order_id}")" "个人与团队业绩全部冲回"

# 场景二：待付款取消恢复库存；随后同一订单双请求并发支付，只允许一笔真实扣款。
create_member 13900000004 codex_bonus_d 沙盘并发购买人D "$b_user_id" false 1
d_member_id="$(sql_value "SELECT id FROM dms_shop_member WHERE login_account = 'codex_bonus_d'")"
d_user_id="$(sql_value "SELECT user_id FROM dms_shop_member WHERE id = ${d_member_id}")"
issue_balance "$d_user_id" 500 D
d_token="$(create_member_session "$d_member_id" "$d_user_id")"
create_address "$d_token" codex_bonus_d 13900000004
d_address_id="$(sql_value "SELECT id FROM dms_shop_address WHERE member_id = ${d_member_id} AND status = 1 ORDER BY id DESC LIMIT 1")"

stock_before_cancel="$(sql_value "SELECT CONCAT(stock, ':', sales_count) FROM dms_shop_product WHERE id = 2")"
submit_order "$d_token" "$d_address_id" 2 BONUS-SANDBOX-CANCEL order-d-cancel-20260813
d_cancel_order_id="$(sql_value "SELECT id FROM dms_shop_order WHERE user_id = ${d_user_id} AND remark = 'BONUS-SANDBOX-CANCEL' ORDER BY create_time DESC LIMIT 1")"
response="$(member_request "$d_token" PUT "/shop/orders/${d_cancel_order_id}/cancel")"
assert_success "$response" "D取消待付款订单"
assert_equal "$stock_before_cancel" "$(sql_value "SELECT CONCAT(stock, ':', sales_count) FROM dms_shop_product WHERE id = 2")" "取消待付款订单完整恢复库存和销量"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id = ${d_cancel_order_id}")" "未支付取消订单不产生奖金"

submit_order "$d_token" "$d_address_id" 1 BONUS-SANDBOX-CONCURRENT order-d-concurrent-submit-20260813
d_order_id="$(sql_value "SELECT id FROM dms_shop_order WHERE user_id = ${d_user_id} AND remark = 'BONUS-SANDBOX-CONCURRENT' ORDER BY create_time DESC LIMIT 1")"
d_item_id="$(sql_value "SELECT id FROM dms_shop_order_item WHERE order_id = ${d_order_id} ORDER BY id LIMIT 1")"

member_request "$d_token" POST "/shop/wallet/orders/${d_order_id}/pay" "$pay_body" order-d-pay-concurrent-a >"${temp_dir}/pay-a.json" &
pay_pid_a=$!
member_request "$d_token" POST "/shop/wallet/orders/${d_order_id}/pay" "$pay_body" order-d-pay-concurrent-b >"${temp_dir}/pay-b.json" &
pay_pid_b=$!
wait "$pay_pid_a"
wait "$pay_pid_b"
assert_success "$(<"${temp_dir}/pay-a.json")" "并发支付请求A"
assert_success "$(<"${temp_dir}/pay-b.json")" "并发支付请求B"
assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_member_asset_flow WHERE user_id = ${d_user_id} AND biz_type = 'ORDER_BALANCE_PAYMENT' AND biz_id = '${d_order_id}'")" "并发支付只形成一笔扣款流水"
assert_equal "400.00" "$(sql_value "SELECT balance FROM dms_member_asset_account WHERE user_id = ${d_user_id} AND asset_code = 'CASH_BONUS'")" "并发支付只扣一次100元"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_commission_record WHERE order_id = ${d_order_id} AND agent_id = ${b_agent_id} AND bonus_type = 'DIRECT_REWARD' AND commission_amount = 25.00")" "并发支付只形成一笔按当前会员级别计算的直推奖"
assert_equal "1" "$(sql_value "SELECT COUNT(*) = 1 FROM dms_shop_order WHERE id = ${d_order_id} AND status = 1 AND pay_amount = 100.00")" "并发支付后订单状态和实付正确"

apply_and_approve_refund "$d_token" "$d_order_id" "$d_item_id" 1 BONUS-SANDBOX-CONCURRENT-FULL-REFUND
assert_equal "500.00" "$(sql_value "SELECT balance FROM dms_member_asset_account WHERE user_id = ${d_user_id} AND asset_code = 'CASH_BONUS'")" "并发订单全退后D余额原路恢复"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id = ${d_order_id} AND status <> 3")" "并发订单全退后本单奖金全部关闭"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_agent WHERE user_id = ${d_user_id} AND status = 1")" "并发购买人首单全退后退出奖金体系"

# 沙盘总资金只来自两次后台人工增加；所有已支付订单均已全额原路退回。
assert_equal "2500.00" "$(sql_value "SELECT COALESCE(SUM(balance),0.00) FROM dms_member_asset_account")" "沙盘最终余额等于人工注入总额，没有凭空增减"
assert_equal "0.00" "$(sql_value "SELECT COALESCE(SUM(commission_amount),0.00) FROM dms_commission_record WHERE status IN (0,1)")" "没有遗留待结算或已结算奖金"
assert_equal "0.00" "$(sql_value "SELECT COALESCE(SUM(debt_amount),0.00) FROM dms_commission_clawback WHERE status = 2")" "待结算奖金退款未形成错误欠款"

echo "LIVE_BONUS_SMOKE_PASS"
echo "ORDERS=$(sql_value "SELECT COUNT(*) FROM dms_shop_order")"
echo "REFUNDS=$(sql_value "SELECT COUNT(*) FROM dms_finance_refund")"
echo "COMMISSION_ROWS=$(sql_value "SELECT COUNT(*) FROM dms_commission_record")"
echo "CLAWBACK_ROWS=$(sql_value "SELECT COUNT(*) FROM dms_commission_clawback")"
echo "PAYMENT_FLOWS=$(sql_value "SELECT COUNT(*) FROM dms_member_asset_flow WHERE biz_type = 'ORDER_BALANCE_PAYMENT'")"
