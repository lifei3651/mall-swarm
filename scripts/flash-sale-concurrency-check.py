#!/usr/bin/env python3
"""Controlled write test for flash-sale concurrency and oversell boundaries.

The script never creates accounts or payments. It requires pre-created test members,
one storefront session cookie per line, and an explicit write-test confirmation.
Session values are never printed.
"""

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


def submit_once(url, payload, session, timeout, member_index, attempt_index, scheduled_at):
    delay = scheduled_at - time.perf_counter()
    if delay > 0:
        time.sleep(delay)
    request_id = uuid.uuid4().hex
    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Cookie": f"shop_session={session}",
            "X-Shop-Client": "storefront",
            "X-Idempotency-Key": request_id,
        },
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read())
            elapsed = (time.perf_counter() - started) * 1000
            success = response.status == 200 and body.get("code") == 200
            order = (body.get("data") or {}).get("order") or {}
            return member_index, attempt_index, success, response.status, body.get("message", ""), order.get("id"), elapsed
    except urllib.error.HTTPError as exc:
        elapsed = (time.perf_counter() - started) * 1000
        try:
            body = json.loads(exc.read())
            message = body.get("message", "")
        except Exception:
            message = f"HTTP {exc.code}"
        return member_index, attempt_index, False, exc.code, message, None, elapsed
    except Exception as exc:
        elapsed = (time.perf_counter() - started) * 1000
        return member_index, attempt_index, False, 0, f"transport:{type(exc).__name__}", None, elapsed


def main():
    parser = argparse.ArgumentParser(description="秒杀并发写入验收（仅限隔离测试环境）")
    parser.add_argument("--base", required=True, help="隔离测试环境地址，例如 https://test.example.com")
    parser.add_argument("--activity-id", required=True)
    parser.add_argument("--payload", required=True, help="ShopOrderSubmitDTO JSON文件")
    parser.add_argument("--sessions", required=True, help="测试会员shop_session，一行一个；文件不得提交Git")
    parser.add_argument("--repeat-per-member", type=int, default=2, help="每位会员同时提交次数，默认2次验证防重复")
    parser.add_argument("--concurrency", type=int, default=40)
    parser.add_argument("--timeout", type=float, default=15)
    parser.add_argument("--expected-stock", type=int, help="活动开始前可售库存；提供后自动验证不超卖")
    parser.add_argument("--confirm-write-test", required=True, choices=["YES"], help="必须明确传 YES 才会创建待付款测试订单")
    args = parser.parse_args()

    if not args.base.lower().startswith("https://"):
        raise SystemExit("仅允许对HTTPS隔离测试环境执行")
    sessions = [line.strip() for line in Path(args.sessions).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not sessions:
        raise SystemExit("sessions文件没有可用测试会话")
    payload_object = json.loads(Path(args.payload).read_text(encoding="utf-8"))
    payload = json.dumps(payload_object, ensure_ascii=False).encode("utf-8")
    repeat = max(1, args.repeat_per_member)
    jobs = [(member_index, attempt) for member_index in range(len(sessions)) for attempt in range(repeat)]
    url = f"{args.base.rstrip('/')}/api/shop/flash-sales/{args.activity_id}/orders"

    started = time.perf_counter()
    results = []
    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        futures = [pool.submit(submit_once, url, payload, sessions[member_index], args.timeout,
                               member_index, attempt, started + 0.8)
                   for member_index, attempt in jobs]
        for future in as_completed(futures):
            results.append(future.result())

    successes = [result for result in results if result[2]]
    failures = [result for result in results if not result[2]]
    successes_by_member = Counter(result[0] for result in successes)
    duplicate_member_success = [index for index, count in successes_by_member.items() if count > 1]
    order_ids = [str(result[5]) for result in successes if result[5] is not None]
    duplicate_order_ids = [order_id for order_id, count in Counter(order_ids).items() if count > 1]
    transport_failures = [result for result in failures if result[3] == 0]
    message_counts = Counter(result[4] or f"HTTP {result[3]}" for result in failures)
    latencies = sorted(result[6] for result in results)
    p95 = latencies[min(len(latencies) - 1, int(len(latencies) * 0.95))]
    oversold = args.expected_stock is not None and len(successes) > args.expected_stock

    summary = {
        "members": len(sessions),
        "requests": len(results),
        "success_orders": len(successes),
        "business_rejections": len(failures) - len(transport_failures),
        "transport_failures": len(transport_failures),
        "duplicate_success_members": len(duplicate_member_success),
        "duplicate_order_ids": len(duplicate_order_ids),
        "expected_stock": args.expected_stock,
        "oversold": oversold,
        "latency_ms_avg": round(statistics.mean(latencies), 2),
        "latency_ms_p95": round(p95, 2),
        "failure_messages": dict(message_counts.most_common(8)),
        "passed": not transport_failures and not duplicate_member_success and not duplicate_order_ids and not oversold,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    raise SystemExit(0 if summary["passed"] else 1)


if __name__ == "__main__":
    main()
