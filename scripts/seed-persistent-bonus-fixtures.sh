#!/usr/bin/env bash
set -euo pipefail

# 在已经清成零业务数据的测试商城基座上，创建一套长期保留、可在后台逐笔核对的奖金测试数据。
# 脚本只删除自己创建的临时会话和临时管理员；bonus_ 会员、订单、奖金、退款及资金流水全部保留。

if [[ "${CONFIRM_PERSISTENT_BONUS_FIXTURES:-}" != "YES" ]]; then
  echo "拒绝执行：请显式设置 CONFIRM_PERSISTENT_BONUS_FIXTURES=YES"
  exit 2
fi

db_name="${MALL_DB_NAME:-mall_distribution}"
api_base="${MALL_API_BASE:-http://127.0.0.1:8086}"
mysql_cmd=(mysql --protocol=socket -uroot "$db_name" -N -B)
test_prefix="bonus_"
admin_username="bonus_fixture_admin"
member_password="${BONUS_FIXTURE_PASSWORD:-}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
payload_encrypt_helper="${PAYLOAD_ENCRYPT_HELPER:-${script_dir}/payload-encrypt.py}"
phone_counter=1

declare -A MEMBER_ID USER_ID PHONE TOKEN ADDRESS_ID ORDER_ID ORDER_ITEM_ID

sql_value() {
  "${mysql_cmd[@]}" -e "$1"
}

fail() {
  echo "测试数据搭建失败：$*" >&2
  exit 1
}

assert_equal() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  [[ "$actual" == "$expected" ]] || fail "${label}，期望 ${expected}，实际 ${actual}"
  echo "通过：${label}"
}

assert_success() {
  local response="$1"
  local label="$2"
  if ! jq -e '.code == 200' >/dev/null <<<"$response"; then
    echo "接口失败：${label}" >&2
    jq -c '{code: .code, message: (.message // .msg // "未知错误")}' <<<"$response" >&2 || true
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
    local envelope challenge_id encrypted_key
    envelope="$(printf '%s' "$body" | "$payload_encrypt_helper" "$api_base")"
    body="$(jq -c '.body' <<<"$envelope")"
    challenge_id="$(jq -r '.challengeId' <<<"$envelope")"
    encrypted_key="$(jq -r '.encryptedKey' <<<"$envelope")"
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
  [[ -z "$idempotency_key" ]] || args+=(-H "X-Idempotency-Key: ${idempotency_key}")
  if [[ -n "$body" ]]; then
    local envelope challenge_id encrypted_key
    envelope="$(printf '%s' "$body" | "$payload_encrypt_helper" "$api_base")"
    body="$(jq -c '.body' <<<"$envelope")"
    challenge_id="$(jq -r '.challengeId' <<<"$envelope")"
    encrypted_key="$(jq -r '.encryptedKey' <<<"$envelope")"
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
}
trap cleanup_credentials EXIT

if [[ ! "$member_password" =~ ^[0-9]{6}$ ]]; then
  fail "必须通过 BONUS_FIXTURE_PASSWORD 提供6位测试密码"
fi
[[ -x "$payload_encrypt_helper" ]] || fail "找不到请求加密工具：${payload_encrypt_helper}"

foundation_state="$(sql_value "
  SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account = 0), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    FORMAT((SELECT COALESCE(SUM(balance), 0) FROM dms_member_asset_account), 2)
  );
")"
resume_mode=false
if [[ "$foundation_state" == "0:0:0:0:0.00" ]]; then
  assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_shop_member WHERE login_account LIKE '${test_prefix}%'")" "不存在同名前缀测试会员"
  echo "通过：造数前为零客户业务基座"
elif [[ "${RESUME_PERSISTENT_BONUS_FIXTURES:-}" == "YES" \
    && "$(sql_value "SELECT COUNT(*) FROM dms_shop_member WHERE login_account LIKE '${test_prefix}%'")" == "44" \
    && "$(sql_value "SELECT COUNT(*) FROM dms_shop_order WHERE remark LIKE '奖金测试[%' ")" == "1" \
    && "$(sql_value "SELECT COUNT(*) FROM dms_shop_order o JOIN dms_commission_record r ON r.order_id=o.id WHERE o.remark LIKE '奖金测试[L1]%' AND o.status=1 AND r.bonus_type='DIRECT_REWARD' AND r.commission_amount=25.00")" == "1" ]]; then
  resume_mode=true
  echo "通过：识别到首次执行停在L1之后，从L2安全续跑"
else
  fail "当前不是零业务基座，也不是脚本可识别的L1断点；拒绝重复造数（当前 ${foundation_state}）"
fi

curl --http1.1 -fsS --max-time 10 "${api_base}/actuator/health" | jq -e '.status == "UP"' >/dev/null \
  || fail "商城后端健康检查未通过"

admin_password="$(openssl rand -hex 18)"
admin_salt="$(openssl rand -hex 16)"
admin_token="$(openssl rand -hex 32)"

"${mysql_cmd[@]}" -e "
  DELETE FROM dms_admin_session WHERE username = '${admin_username}';
  DELETE FROM dms_admin_user WHERE username = '${admin_username}';
  INSERT INTO dms_admin_user
    (username, password_hash, salt, nickname, role_code, permissions, status,
     failed_login_count, lock_time, create_time, update_time)
  VALUES
    ('${admin_username}', SHA2(CONCAT('${admin_password}', ':', '${admin_salt}'), 256),
     '${admin_salt}', '奖金全景测试临时管理员', 'SUPER_ADMIN', '*', 1, 0, NULL, NOW(), NOW());
  INSERT INTO dms_admin_session
    (admin_id, username, token, status, expire_time, create_time, update_time)
  SELECT id, username, SHA2('${admin_token}', 256), 1, DATE_ADD(NOW(), INTERVAL 4 HOUR), NOW(), NOW()
    FROM dms_admin_user WHERE username = '${admin_username}';
"
admin_id="$(sql_value "SELECT id FROM dms_admin_user WHERE username = '${admin_username}'")"

add_member() {
  local username="$1"
  local nickname="$2"
  local inviter_username="${3:-}"
  local activate="${4:-false}"
  local level="${5:-1}"
  local phone inviter_user_id body response
  phone="$(printf '139100%05d' "$phone_counter")"
  phone_counter=$((phone_counter + 1))
  inviter_user_id=""
  [[ -z "$inviter_username" ]] || inviter_user_id="${USER_ID[$inviter_username]}"
  if [[ -n "$inviter_user_id" ]]; then
    body="$(jq -n --arg phone "$phone" --arg username "$username" --arg password "$member_password" \
      --arg nickname "$nickname" --argjson inviterUserId "$inviter_user_id" \
      --argjson activateDistribution "$activate" --argjson initialLevel "$level" \
      '{phone:$phone,username:$username,password:$password,nickname:$nickname,inviterUserId:$inviterUserId,activateDistribution:$activateDistribution,initialLevel:$initialLevel,reason:"奖金全景长期测试数据"}')"
  else
    body="$(jq -n --arg phone "$phone" --arg username "$username" --arg password "$member_password" \
      --arg nickname "$nickname" --argjson activateDistribution "$activate" --argjson initialLevel "$level" \
      '{phone:$phone,username:$username,password:$password,nickname:$nickname,activateDistribution:$activateDistribution,initialLevel:$initialLevel,reason:"奖金全景长期测试数据"}')"
  fi
  response="$(admin_request POST /shop/admin/members "$body")"
  assert_success "$response" "创建 ${username}"
  MEMBER_ID[$username]="$(sql_value "SELECT id FROM dms_shop_member WHERE login_account='${username}'")"
  USER_ID[$username]="$(sql_value "SELECT user_id FROM dms_shop_member WHERE login_account='${username}'")"
  PHONE[$username]="$phone"
}

member_token() {
  local username="$1"
  local token="$(openssl rand -hex 32)"
  "${mysql_cmd[@]}" -e "
    UPDATE dms_shop_member SET pay_password_hash = password_hash,
      pay_password_failed_count = 0, pay_password_lock_time = NULL
     WHERE id = ${MEMBER_ID[$username]};
    INSERT INTO dms_shop_member_session
      (member_id, user_id, token, status, expire_time, create_time, update_time)
    VALUES
      (${MEMBER_ID[$username]}, ${USER_ID[$username]}, SHA2('${token}', 256), 1,
       DATE_ADD(NOW(), INTERVAL 4 HOUR), NOW(), NOW());
  "
  printf '%s' "$token"
}

ensure_member_access() {
  local username="$1"
  if [[ -z "${TOKEN[$username]:-}" ]]; then
    TOKEN[$username]="$(member_token "$username")"
  fi
  if [[ -z "${ADDRESS_ID[$username]:-}" ]]; then
    local response
    response="$(member_request "${TOKEN[$username]}" POST /shop/addresses \
      "$(jq -n --arg name "$username" --arg phone "${PHONE[$username]}" \
        '{receiverName:$name,receiverPhone:$phone,province:"广东省",city:"深圳市",district:"南山区",detailAddress:"奖金全景测试专用地址",isDefault:1}')")"
    assert_success "$response" "${username} 保存测试地址"
    ADDRESS_ID[$username]="$(sql_value "SELECT id FROM dms_shop_address WHERE member_id=${MEMBER_ID[$username]} AND status=1 ORDER BY id DESC LIMIT 1")"
  fi
}

set_level() {
  local username="$1"
  local level="$2"
  local response
  response="$(admin_request PUT "/shop/admin/members/${MEMBER_ID[$username]}/level" \
    "$(jq -n --argjson level "$level" --arg reason "奖金全景测试固定展示级别：${level}级" '{level:$level,reason:$reason}')")"
  assert_success "$response" "${username} 调整为${level}级"
}

issue_balance() {
  local username="$1"
  local amount="$2"
  local scenario="$3"
  local request_id response
  request_id="$(cat /proc/sys/kernel/random/uuid)"
  response="$(admin_request POST /distribution/assets/issue \
    "$(jq -n --argjson userId "${USER_ID[$username]}" --argjson amount "$amount" \
      --arg requestId "$request_id" --arg adminPassword "$admin_password" --arg scenario "$scenario" \
      '{userId:$userId,amount:$amount,bizType:"BONUS_FIXTURE_ISSUE",bizId:$scenario,requestId:$requestId,remark:("奖金全景测试人工余额："+$scenario),adminPassword:$adminPassword}')")"
  assert_success "$response" "${username} 增加测试余额 ${amount}"
}

process_bonus() {
  local order_id="$1"
  local response status
  response="$(admin_request POST '/distribution/commission/calculation-tasks/process?limit=200')"
  assert_success "$response" "处理奖金任务"
  for _ in {1..20}; do
    status="$(sql_value "SELECT status FROM dms_bonus_calculation_task WHERE order_id=${order_id}")"
    [[ "$status" == "2" ]] && return 0
    [[ "$status" == "3" ]] && fail "订单 ${order_id} 奖金任务失败：$(sql_value "SELECT fail_reason FROM dms_bonus_calculation_task WHERE order_id=${order_id}")"
    # 当前1.0.50普通商城支付为同步计算，不创建异步任务；财务凭证落库即代表本单计算完成。
    if [[ -z "$status" && "$(sql_value "SELECT COUNT(*) FROM dms_order_finance WHERE order_id=${order_id}")" == "1" ]]; then
      return 0
    fi
    sleep 1
  done
  fail "订单 ${order_id} 奖金任务未在期限内完成"
}

place_order() {
  local scenario="$1"
  local username="$2"
  local quantity="$3"
  local issue_amount="$4"
  local description="$5"
  local body response order_id
  ensure_member_access "$username"
  issue_balance "$username" "$issue_amount" "$scenario"
  body="$(jq -n --argjson addressId "${ADDRESS_ID[$username]}" --argjson quantity "$quantity" \
    --arg remark "奖金测试[${scenario}] ${description}" \
    '{addressId:$addressId,payType:"BALANCE",businessType:"NORMAL",remark:$remark,items:[{productId:2,skuId:3,quantity:$quantity}]}')"
  response="$(member_request "${TOKEN[$username]}" POST /shop/orders "$body" "fixture-${scenario}-submit-20260813")"
  assert_success "$response" "${scenario} 提交订单"
  order_id="$(sql_value "SELECT id FROM dms_shop_order WHERE user_id=${USER_ID[$username]} AND remark LIKE '奖金测试[${scenario}]%' ORDER BY create_time DESC LIMIT 1")"
  [[ -n "$order_id" ]] || fail "找不到 ${scenario} 订单"
  ORDER_ID[$scenario]="$order_id"
  ORDER_ITEM_ID[$scenario]="$(sql_value "SELECT id FROM dms_shop_order_item WHERE order_id=${order_id} ORDER BY id LIMIT 1")"
  response="$(member_request "${TOKEN[$username]}" POST "/shop/wallet/orders/${order_id}/pay" \
    "$(jq -n --arg password "$member_password" '{paymentPassword:$password}')" "fixture-${scenario}-pay-20260813")"
  assert_success "$response" "${scenario} 余额支付"
  process_bonus "$order_id"
  response="$(admin_request PUT "/shop/admin/orders/${order_id}/service-remark" \
    "$(jq -n --arg remark "奖金测试[${scenario}] ${description}；请按测试清单核对，数据长期保留。" '{serviceRemark:$remark}')")"
  assert_success "$response" "${scenario} 保存客服备注"
}

assert_direct_order() {
  local scenario="$1"
  local inviter="$2"
  local direct_rate="$3"
  local direct_amount="$4"
  local director_rate="${5:-}"
  local director_amount="${6:-}"
  local order_id="${ORDER_ID[$scenario]}"
  local expected_count=1 expected_total="$direct_amount"
  [[ -z "$director_amount" ]] || { expected_count=2; expected_total="$(awk -v a="$direct_amount" -v b="$director_amount" 'BEGIN {printf "%.2f", a+b}')"; }
  assert_equal "$expected_count" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id=${order_id}")" "${scenario} 奖金条数"
  assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${order_id} AND m.login_account='${inviter}' AND r.bonus_type='DIRECT_REWARD' AND r.commission_rate=${direct_rate} AND r.commission_amount=${direct_amount}")" "${scenario} 直推奖比例和金额"
  if [[ -n "$director_amount" ]]; then
    assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${order_id} AND m.login_account='${inviter}' AND r.bonus_type='DIRECTOR_SHARE' AND r.commission_rate=${director_rate} AND r.commission_amount=${director_amount}")" "${scenario} 董事分红比例和金额"
  fi
  assert_equal "$expected_total" "$(sql_value "SELECT FORMAT(SUM(commission_amount),2) FROM dms_commission_record WHERE order_id=${order_id}")" "${scenario} 奖金合计"
}

apply_and_approve_refund() {
  local scenario="$1"
  local username="$2"
  local quantity="$3"
  local reason="$4"
  local order_id="${ORDER_ID[$scenario]}"
  local response after_sale_id
  response="$(member_request "${TOKEN[$username]}" POST /shop/after-sales \
    "$(jq -n --argjson orderId "$order_id" --argjson itemId "${ORDER_ITEM_ID[$scenario]}" \
      --argjson quantity "$quantity" --arg reason "$reason" \
      '{orderId:$orderId,applyType:1,items:[{orderItemId:$itemId,quantity:$quantity}],reason:$reason}')")"
  assert_success "$response" "${scenario} 会员申请退款"
  after_sale_id="$(sql_value "SELECT id FROM dms_shop_after_sale WHERE order_id=${order_id} ORDER BY id DESC LIMIT 1")"
  response="$(admin_request PUT "/shop/admin/after-sales/${after_sale_id}/audit" \
    "$(jq -n --argjson auditUserId "$admin_id" '{status:1,auditRemark:"奖金全景测试自动审核",auditUserId:$auditUserId,auditUserName:"奖金全景测试管理员"}')")"
  assert_success "$response" "${scenario} 后台审核退款"
}

manual_refund() {
  local scenario="$1"
  local reason="$2"
  local response
  response="$(admin_request POST "/shop/admin/orders/${ORDER_ID[$scenario]}/refund" \
    "$(jq -n --argjson itemId "${ORDER_ITEM_ID[$scenario]}" --arg reason "$reason" \
      --argjson operatorId "$admin_id" '{refundMode:"QUANTITY",items:[{orderItemId:$itemId,quantity:1}],reason:$reason,applyType:1,operatorId:$operatorId,operatorName:"奖金全景测试管理员"}')")"
  assert_success "$response" "${scenario} 后台超期退款"
}

ship_receive_and_backdate() {
  local scenario="$1"
  local username="$2"
  local response
  response="$(admin_request PUT "/shop/admin/orders/${ORDER_ID[$scenario]}/ship" \
    "$(jq -n --arg no "BONUS${scenario}20260813" '{deliveryCompany:"顺丰速运",deliveryNo:$no,shipmentQuantity:1}')")"
  assert_success "$response" "${scenario} 发货"
  response="$(member_request "${TOKEN[$username]}" PUT "/shop/orders/${ORDER_ID[$scenario]}/receive")"
  assert_success "$response" "${scenario} 确认收货"
  "${mysql_cmd[@]}" -e "UPDATE dms_shop_order SET receive_time=DATE_SUB(NOW(), INTERVAL 8 DAY), update_time=NOW() WHERE id=${ORDER_ID[$scenario]} AND status=3;"
}

transfer_bonus() {
  local username="$1"
  local amount="$2"
  local scenario="$3"
  local response
  [[ -n "${TOKEN[$username]:-}" ]] || TOKEN[$username]="$(member_token "$username")"
  response="$(member_request "${TOKEN[$username]}" POST /shop/wallet/transfers \
    "$(jq -n --arg phone "${PHONE[bonus_sink]}" --argjson amount "$amount" --arg password "$member_password" \
      --arg remark "奖金测试[${scenario}] 模拟奖金已消费" '{recipientPhone:$phone,amount:$amount,paymentPassword:$password,remark:$remark}')" \
    "fixture-${scenario}-transfer-20260813")"
  assert_success "$response" "${scenario} 将已结算奖金转出"
}

all_accounts=(
  bonus_l1_inv bonus_l1_buy bonus_l2_inv bonus_l2_buy bonus_l3_inv bonus_l3_buy bonus_l4_inv bonus_l4_buy
  bonus_l5_inv bonus_l5_buy bonus_l6_inv bonus_l6_buy bonus_l7_inv bonus_l7_buy bonus_l8_inv bonus_l8_buy
  bonus_chain_l8 bonus_chain_l7 bonus_chain_l6 bonus_chain_l5 bonus_chain_dir bonus_chain_buy
  bonus_same_far bonus_same_near bonus_same_dir bonus_same_buy
  bonus_deep_l5 bonus_deep_a bonus_deep_b bonus_deep_dir bonus_deep_buy
  bonus_no_parent bonus_partial_inv bonus_partial_buy bonus_full_inv bonus_full_buy bonus_settle_inv bonus_settle_buy
  bonus_sink bonus_debt_inv bonus_debt_buy bonus_offset_inv bonus_offset_buy1 bonus_offset_buy2
)

if [[ "$resume_mode" == "true" ]]; then
  for username in "${all_accounts[@]}"; do
    MEMBER_ID[$username]="$(sql_value "SELECT id FROM dms_shop_member WHERE login_account='${username}'")"
    USER_ID[$username]="$(sql_value "SELECT user_id FROM dms_shop_member WHERE login_account='${username}'")"
    PHONE[$username]="$(sql_value "SELECT phone FROM dms_shop_member WHERE login_account='${username}'")"
    [[ -n "${MEMBER_ID[$username]}" && -n "${USER_ID[$username]}" ]] || fail "断点会员 ${username} 不完整"
  done
else
  echo "开始创建1～8级直推测试会员"
  for level in {1..8}; do
    add_member "bonus_l${level}_inv" "奖金L${level}推荐人" "" true "$level"
    add_member "bonus_l${level}_buy" "奖金L${level}下单人" "bonus_l${level}_inv" false 1
  done

  echo "开始创建四档董事链、同级压缩和隔层无限代会员"
  add_member bonus_chain_l8 四档链合伙人 "" true 8
  add_member bonus_chain_l7 四档链三星董事 bonus_chain_l8 true 7
  add_member bonus_chain_l6 四档链二星董事 bonus_chain_l7 true 6
  add_member bonus_chain_l5 四档链一星董事 bonus_chain_l6 true 5
  add_member bonus_chain_dir 四档链直推会员 bonus_chain_l5 true 1
  add_member bonus_chain_buy 四档链下单人 bonus_chain_dir false 1

  add_member bonus_same_far 同级远端一星董事 "" true 5
  add_member bonus_same_near 同级近端一星董事 bonus_same_far true 5
  add_member bonus_same_dir 同级压缩直推人 bonus_same_near true 1
  add_member bonus_same_buy 同级压缩下单人 bonus_same_dir false 1

  add_member bonus_deep_l5 隔层一星董事 "" true 5
  add_member bonus_deep_a 隔层会员A bonus_deep_l5 true 1
  add_member bonus_deep_b 隔层会员B bonus_deep_a true 1
  add_member bonus_deep_dir 隔层直推会员 bonus_deep_b true 1
  add_member bonus_deep_buy 隔层下单人 bonus_deep_dir false 1

  echo "开始创建退款、结算、欠款及抵欠款会员"
  add_member bonus_no_parent 无推荐人下单会员 "" false 1
  add_member bonus_partial_inv 部分退款VIP推荐人 "" true 2
  add_member bonus_partial_buy 部分退款下单人 bonus_partial_inv false 1
  add_member bonus_full_inv 全额退款推荐人 "" true 1
  add_member bonus_full_buy 全额退款下单人 bonus_full_inv false 1
  add_member bonus_settle_inv 已结算推荐人 "" true 1
  add_member bonus_settle_buy 已结算下单人 bonus_settle_inv false 1
  add_member bonus_sink 奖金消费接收账户 "" true 1
  add_member bonus_debt_inv 奖金欠款推荐人 "" true 1
  add_member bonus_debt_buy 奖金欠款下单人 bonus_debt_inv false 1
  add_member bonus_offset_inv 未来奖金抵欠推荐人 "" true 1
  add_member bonus_offset_buy1 抵欠首单下单人 bonus_offset_inv false 1
  add_member bonus_offset_buy2 抵欠后续下单人 bonus_offset_inv false 1
fi

echo "执行1～8级直推和对应董事分红"
direct_rates=(0 0.25 0.30 0.37 0.45 0.52 0.57 0.61 0.65)
direct_amounts=(0 25.00 30.00 37.00 45.00 52.00 57.00 61.00 65.00)
director_rates=(0 0 0 0 0 0.05 0.04 0.03 0.02)
director_amounts=(0 0 0 0 0 5.00 4.00 3.00 2.00)
start_level=1
if [[ "$resume_mode" == "true" ]]; then
  ORDER_ID[L1]="$(sql_value "SELECT id FROM dms_shop_order WHERE remark LIKE '奖金测试[L1]%' ORDER BY id LIMIT 1")"
  ORDER_ITEM_ID[L1]="$(sql_value "SELECT id FROM dms_shop_order_item WHERE order_id=${ORDER_ID[L1]} ORDER BY id LIMIT 1")"
  assert_direct_order L1 bonus_l1_inv 0.25 25.00
  start_level=2
fi
for level in $(seq "$start_level" 8); do
  set_level "bonus_l${level}_inv" "$level"
  if [[ $level -ge 5 ]]; then
    place_order "L${level}" "bonus_l${level}_buy" 1 200 "${level}级推荐人直推；预期直推${direct_amounts[$level]}，董事分红${director_amounts[$level]}"
    assert_direct_order "L${level}" "bonus_l${level}_inv" "${direct_rates[$level]}" "${direct_amounts[$level]}" "${director_rates[$level]}" "${director_amounts[$level]}"
  else
    place_order "L${level}" "bonus_l${level}_buy" 1 200 "${level}级推荐人直推；预期直推${direct_amounts[$level]}"
    assert_direct_order "L${level}" "bonus_l${level}_inv" "${direct_rates[$level]}" "${direct_amounts[$level]}"
  fi
done

echo "执行四档董事链"
set_level bonus_chain_l8 8; set_level bonus_chain_l7 7; set_level bonus_chain_l6 6; set_level bonus_chain_l5 5; set_level bonus_chain_dir 1
place_order CHAIN bonus_chain_buy 1 200 "直推25 + 一星5 + 二星4 + 三星3 + 合伙人2；预期合计39"
assert_equal "5" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id=${ORDER_ID[CHAIN]}")" "CHAIN 五笔奖金"
assert_equal "39.00" "$(sql_value "SELECT FORMAT(SUM(commission_amount),2) FROM dms_commission_record WHERE order_id=${ORDER_ID[CHAIN]}")" "CHAIN 奖金合计"
for item in 'bonus_chain_dir:DIRECT_REWARD:25.00' 'bonus_chain_l5:DIRECTOR_SHARE:5.00' 'bonus_chain_l6:DIRECTOR_SHARE:4.00' 'bonus_chain_l7:DIRECTOR_SHARE:3.00' 'bonus_chain_l8:DIRECTOR_SHARE:2.00'; do
  IFS=: read -r account bonus amount <<<"$item"
  assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${ORDER_ID[CHAIN]} AND m.login_account='${account}' AND r.bonus_type='${bonus}' AND r.commission_amount=${amount}")" "CHAIN ${account} ${bonus}"
done

echo "执行同级只取最近一人"
set_level bonus_same_far 5; set_level bonus_same_near 5; set_level bonus_same_dir 1
place_order SAME bonus_same_buy 1 200 "同链两名一星董事；预期近端5、远端0、直推25"
assert_equal "30.00" "$(sql_value "SELECT FORMAT(SUM(commission_amount),2) FROM dms_commission_record WHERE order_id=${ORDER_ID[SAME]}")" "SAME 奖金合计"
assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${ORDER_ID[SAME]} AND m.login_account='bonus_same_near' AND r.bonus_type='DIRECTOR_SHARE' AND r.commission_amount=5.00")" "SAME 最近一星董事得5"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${ORDER_ID[SAME]} AND m.login_account='bonus_same_far'")" "SAME 远端同级不重复得奖"

echo "执行隔层无限代"
set_level bonus_deep_l5 5; set_level bonus_deep_a 1; set_level bonus_deep_b 1; set_level bonus_deep_dir 1
place_order DEEP bonus_deep_buy 1 200 "一星董事位于第4层；预期直推25、隔层董事5"
assert_equal "30.00" "$(sql_value "SELECT FORMAT(SUM(commission_amount),2) FROM dms_commission_record WHERE order_id=${ORDER_ID[DEEP]}")" "DEEP 奖金合计"
assert_equal "1" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_member m ON m.user_id=r.agent_user_id WHERE r.order_id=${ORDER_ID[DEEP]} AND m.login_account='bonus_deep_l5' AND r.bonus_type='DIRECTOR_SHARE' AND r.commission_level=4 AND r.commission_amount=5.00")" "DEEP 第4层董事仍得5"

echo "执行无推荐人不发奖"
place_order NONE bonus_no_parent 1 200 "无推荐人；预期奖金0"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id=${ORDER_ID[NONE]}")" "NONE 不产生奖金"

echo "执行部分退款"
set_level bonus_partial_inv 2
place_order PARTIAL bonus_partial_buy 10 2000 "1000元订单直推300；退3件后剩余奖金210、追回90"
assert_direct_order PARTIAL bonus_partial_inv 0.30 300.00
apply_and_approve_refund PARTIAL bonus_partial_buy 3 "奖金测试[PARTIAL] 退款3件，保留7件"
assert_equal "210.00" "$(sql_value "SELECT commission_amount FROM dms_commission_record WHERE order_id=${ORDER_ID[PARTIAL]} AND bonus_type='DIRECT_REWARD'")" "PARTIAL 剩余奖金210"
assert_equal "90.00:90.00:0.00:1" "$(sql_value "SELECT CONCAT(clawback_amount,':',deducted_amount,':',debt_amount,':',status) FROM dms_commission_clawback WHERE order_id=${ORDER_ID[PARTIAL]}")" "PARTIAL 待结算奖金追回90且无欠款"
assert_equal "300.00" "$(sql_value "SELECT refund_amount FROM dms_order_finance WHERE order_id=${ORDER_ID[PARTIAL]}")" "PARTIAL 实际退款300"

echo "执行全额退款退会"
set_level bonus_full_inv 1
place_order FULL bonus_full_buy 1 200 "100元订单直推25；全额退款后奖金归零、首单会员退会"
assert_direct_order FULL bonus_full_inv 0.25 25.00
apply_and_approve_refund FULL bonus_full_buy 1 "奖金测试[FULL] 全额退款"
assert_equal "0.00:3" "$(sql_value "SELECT CONCAT(commission_amount,':',status) FROM dms_commission_record WHERE order_id=${ORDER_ID[FULL]} AND bonus_type='DIRECT_REWARD'")" "FULL 奖金归零并标记已退款"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_agent WHERE user_id=${USER_ID[bonus_full_buy]} AND status=1")" "FULL 首单全退后下单人退出奖金体系"

echo "执行已结算、已消费后欠款、未来奖金抵欠款"
set_level bonus_settle_inv 1
place_order SETTLED bonus_settle_buy 1 200 "确认收货超过7天；预期直推25进入已结算"
set_level bonus_debt_inv 1
place_order DEBT bonus_debt_buy 1 200 "奖金结算后转出，再全额退款；预期形成25欠款"
set_level bonus_offset_inv 1
place_order OFFSET1 bonus_offset_buy1 1 200 "首单奖金结算后转出并退款；预期先形成25欠款"

ship_receive_and_backdate SETTLED bonus_settle_buy
ship_receive_and_backdate DEBT bonus_debt_buy
ship_receive_and_backdate OFFSET1 bonus_offset_buy1

echo "重启后端一次，触发真实T+7自动结算扫描"
systemctl restart lingqimall-distribution
for _ in {1..40}; do
  if curl --http1.1 -fsS --max-time 5 "${api_base}/actuator/health" | jq -e '.status == "UP"' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl --http1.1 -fsS --max-time 10 "${api_base}/actuator/health" | jq -e '.status == "UP"' >/dev/null \
  || fail "重启后商城后端未恢复健康"

settled_ready=0
for _ in {1..40}; do
  settled_ready="$(sql_value "SELECT COUNT(*) FROM dms_commission_record WHERE order_id IN (${ORDER_ID[SETTLED]},${ORDER_ID[DEBT]},${ORDER_ID[OFFSET1]}) AND status=1")"
  [[ "$settled_ready" == "3" ]] && break
  sleep 2
done
assert_equal "3" "$settled_ready" "三笔直推奖完成T+7自动结算"
assert_equal "6" "$(sql_value "SELECT COUNT(*) FROM dms_order_balance_allocation WHERE order_id IN (${ORDER_ID[SETTLED]},${ORDER_ID[DEBT]},${ORDER_ID[OFFSET1]}) AND status=1")" "三笔订单的成本及剩余商品款全部自动结算"
assert_equal "25.00" "$(sql_value "SELECT balance FROM dms_member_asset_account WHERE user_id=${USER_ID[bonus_settle_inv]} AND asset_code='CASH_BONUS'")" "SETTLED 推荐人余额收到25"

transfer_bonus bonus_debt_inv 25 DEBT
manual_refund DEBT "奖金测试[DEBT] 已结算奖金消费后全额退款"
assert_equal "25.00:0.00:25.00:3:2" "$(sql_value "SELECT CONCAT(clawback_amount,':',deducted_amount,':',debt_amount,':',clawback_type,':',status) FROM dms_commission_clawback WHERE order_id=${ORDER_ID[DEBT]} ORDER BY id DESC LIMIT 1")" "DEBT 形成25元待抵扣欠款"

transfer_bonus bonus_offset_inv 25 OFFSET1
manual_refund OFFSET1 "奖金测试[OFFSET1] 已结算奖金消费后全额退款"
assert_equal "25.00" "$(sql_value "SELECT SUM(debt_amount) FROM dms_commission_clawback c JOIN dms_shop_member m ON m.user_id=c.agent_user_id WHERE m.login_account='bonus_offset_inv' AND c.status=2")" "OFFSET1 先形成25元欠款"

set_level bonus_offset_inv 1
place_order OFFSET2 bonus_offset_buy2 1 200 "新直推奖25优先抵扣历史欠款；预期新奖金净额0、欠款清零"
assert_equal "0.00:3" "$(sql_value "SELECT CONCAT(commission_amount,':',status) FROM dms_commission_record WHERE order_id=${ORDER_ID[OFFSET2]} AND bonus_type='DIRECT_REWARD'")" "OFFSET2 新奖金全部抵欠"
assert_equal "0.00" "$(sql_value "SELECT COALESCE(SUM(debt_amount),0.00) FROM dms_commission_clawback c JOIN dms_shop_member m ON m.user_id=c.agent_user_id WHERE m.login_account='bonus_offset_inv' AND c.status=2")" "OFFSET2 历史欠款清零"
assert_equal "25.00" "$(sql_value "SELECT COALESCE(SUM(clawback_amount),0.00) FROM dms_commission_clawback c WHERE c.order_id=${ORDER_ID[OFFSET2]} AND c.clawback_type=4 AND c.status=1")" "OFFSET2 留下25元抵欠流水"

echo "恢复所有展示账号的明确级别，便于后台逐个核对"
for level in {1..8}; do set_level "bonus_l${level}_inv" "$level"; done
set_level bonus_chain_l8 8; set_level bonus_chain_l7 7; set_level bonus_chain_l6 6; set_level bonus_chain_l5 5; set_level bonus_chain_dir 1
set_level bonus_same_far 5; set_level bonus_same_near 5; set_level bonus_same_dir 1
set_level bonus_deep_l5 5; set_level bonus_deep_a 1; set_level bonus_deep_b 1; set_level bonus_deep_dir 1
set_level bonus_partial_inv 2; set_level bonus_full_inv 1; set_level bonus_settle_inv 1; set_level bonus_sink 1; set_level bonus_debt_inv 1; set_level bonus_offset_inv 1

assert_equal "44" "$(sql_value "SELECT COUNT(*) FROM dms_shop_member WHERE login_account LIKE '${test_prefix}%'")" "共保留44个测试会员"
assert_equal "18" "$(sql_value "SELECT COUNT(*) FROM dms_shop_order WHERE remark LIKE '奖金测试[%'")" "共保留18张测试订单"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status<>2")" "没有失败或处理中的奖金任务"
assert_equal "0" "$(sql_value "SELECT COUNT(*) FROM dms_order_finance f JOIN dms_shop_order o ON o.id=f.order_id WHERE o.remark LIKE '奖金测试[%' AND f.net_pay_amount <> f.product_cost + f.bonus_amount + f.company_profit")" "全部订单净资金拆分守恒"

echo "PERSISTENT_BONUS_FIXTURES_PASS"
echo "MEMBERS=$(sql_value "SELECT COUNT(*) FROM dms_shop_member WHERE login_account LIKE '${test_prefix}%'")"
echo "ORDERS=$(sql_value "SELECT COUNT(*) FROM dms_shop_order WHERE remark LIKE '奖金测试[%'")"
echo "COMMISSION_ROWS=$(sql_value "SELECT COUNT(*) FROM dms_commission_record")"
echo "CLAWBACK_ROWS=$(sql_value "SELECT COUNT(*) FROM dms_commission_clawback")"
echo "REFUNDS=$(sql_value "SELECT COUNT(*) FROM dms_finance_refund")"
