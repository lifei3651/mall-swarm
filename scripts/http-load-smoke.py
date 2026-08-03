#!/usr/bin/env python3
"""Small dependency-free HTTPS read load check for the 2C2G production host."""

import argparse
import json
import statistics
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def request_once(url: str, timeout: float):
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            body = response.read()
            payload = json.loads(body)
            ok = response.status == 200 and payload.get("code") == 200
            return ok, (time.perf_counter() - started) * 1000, response.status, ""
    except Exception as exc:  # load-test diagnostics
        return False, (time.perf_counter() - started) * 1000, 0, str(exc)


def request_scheduled(url: str, timeout: float, scheduled_at: float):
    delay = scheduled_at - time.perf_counter()
    if delay > 0:
        time.sleep(delay)
    return request_once(url, timeout)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="https://lingqimall.com")
    parser.add_argument("--requests", type=int, default=300)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=8)
    parser.add_argument("--rate", type=float, default=15, help="target requests per second")
    args = parser.parse_args()
    paths = ["/api/shop/home", "/api/shop/categories", "/api/shop/products?pageNum=1&pageSize=20"]
    urls = [args.base.rstrip("/") + paths[index % len(paths)] for index in range(args.requests)]
    results = []
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(request_scheduled, url, args.timeout, started + index / args.rate)
                   for index, url in enumerate(urls)]
        for future in as_completed(futures):
            results.append(future.result())
    elapsed = time.perf_counter() - started
    latencies = sorted(item[1] for item in results)
    failures = [item for item in results if not item[0]]
    p95 = latencies[min(len(latencies) - 1, int(len(latencies) * 0.95))]
    print(json.dumps({
        "requests": len(results), "concurrency": args.concurrency, "target_rate": args.rate,
        "success": len(results) - len(failures),
        "failures": len(failures), "requests_per_second": round(len(results) / elapsed, 2),
        "latency_ms_avg": round(statistics.mean(latencies), 2), "latency_ms_p95": round(p95, 2),
        "sample_error": failures[0][3] if failures else "",
    }, ensure_ascii=False, indent=2))
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
