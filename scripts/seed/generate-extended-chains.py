#!/usr/bin/env python3
"""Append extended component-combination demo chains (target: 100+ total)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JSON_PATH = ROOT / "demo-chains.json"

NODE_TEMPLATES = [
    {"type": "NORMAL", "component": "validateUser", "label": "用户校验"},
    {"type": "SCRIPT", "label": "脚本网关", "script": "amount > 0"},
    {"type": "SUB_CHAIN", "label": "子链调用", "subChainCode": "CHN_DEMO_ORDER_CREATE"},
    {"type": "CONDITION", "component": "riskCheckAmount", "label": "金额风控"},
    {"type": "SELECTOR", "component": "routePromotion", "label": "促销路由"},
    {"type": "ITERATOR", "label": "批量通知", "config": {"dataSource": "notifyItems", "itemName": "item", "subNodes": [{"component": "sendNotify"}]}},
    {"type": "FORK", "component": "validateUser", "label": "并行分叉"},
    {"type": "JOIN", "component": "sendNotify", "label": "并行汇聚"},
    {"type": "TRY_CATCH", "component": "validateUser", "label": "异常捕获"},
    {"type": "WHILE", "component": "validateUser", "label": "条件循环"},
    {"type": "DELAY", "component": "validateUser", "label": "延迟等待"},
    {"type": "LOGGER", "component": "validateUser", "label": "日志记录"},
    {"type": "TRANSFORMER", "component": "transformOrderStatus", "label": "数据转换"},
    {"type": "FILTER", "component": "filterValidUsers", "label": "数据过滤"},
    {"type": "AGGREGATOR", "component": "aggregateOrderAmounts", "label": "数据聚合"},
    {"type": "SPLITTER", "component": "splitOrdersByStatus", "label": "数据拆分"},
    {"type": "HTTP_CLIENT", "component": "httpGet", "label": "HTTP调用"},
    {"type": "MQ_PRODUCER", "component": "sendOrderCreatedMsg", "label": "消息生产"},
    {"type": "MQ_CONSUMER", "component": "consumeOrderCreatedMsg", "label": "消息消费"},
    {"type": "CACHE_READER", "component": "getUserCache", "label": "缓存读取"},
    {"type": "CACHE_WRITER", "component": "setUserCache", "label": "缓存写入"},
    {"type": "APPROVAL", "component": "validateUser", "label": "审批节点"},
    {"type": "NOTIFICATION", "component": "sendNotify", "label": "通知节点"},
]

COMPONENTS_ROTATE = [
    "validateUser", "sendNotify", "createOrder", "checkStock", "processPayment",
    "transformOrder", "filterInvalid", "aggregateStats", "splitOrder",
    "sendOrderCreatedMsg", "consumeOrderCreatedMsg", "readUserCache", "writeUserCache",
]


def build_chain(idx: int, tpl: dict, round_no: int):
    node_type = tpl["type"]
    label = f"{tpl['label']}-{round_no}"
    code = f"CHN_DEMO_EXT_{idx:03d}"
    n1 = {"id": "n1", "label": label, "type": node_type}
    if tpl.get("component"):
        n1["component"] = tpl["component"]
    if tpl.get("script"):
        n1["script"] = tpl["script"]
    if tpl.get("subChainCode"):
        n1["subChainCode"] = tpl["subChainCode"]
    if tpl.get("config"):
        n1["config"] = tpl["config"]
    n2 = {"id": "n2", "label": "发送通知", "type": "NORMAL", "component": "sendNotify"}
    chain = {
        "code": code,
        "name": f"扩展链-{node_type}-{idx:03d}",
        "desc": f"元件类型 {node_type} 组合示例 #{idx}",
        "tier": "1-3",
        "nodes": [n1, n2],
        "edges": [{"source": "n1", "target": "n2"}],
    }
    if node_type == "WHILE":
        n1["config"] = {"condition": "step < 0"}
    if node_type == "DELAY":
        n1["config"] = {"delayMs": 10}
    if node_type == "ITERATOR":
        chain["nodes"] = [
            {"id": "n0", "label": "种子通知列表", "type": "NORMAL", "component": "seedNotifyItems"},
            n1, n2,
        ]
        chain["edges"] = [{"source": "n0", "target": "n1"}, {"source": "n1", "target": "n2"}]
    if node_type == "CONDITION":
        chain["nodes"].insert(0, {"id": "n0", "label": "前置校验", "type": "NORMAL", "component": "validateUser"})
        chain["edges"] = [{"source": "n0", "target": "n1"}, {"source": "n1", "target": "n2", "label": "通过"}]
    if node_type == "SELECTOR":
        chain["edges"] = [{"source": "n1", "target": "n2", "label": "VIP"}]
    if node_type == "TRY_CATCH":
        chain["edges"] = [
            {"source": "n1", "target": "n2", "label": "Try"},
        ]
    scene = {
        "scene": f"SCN_EXT_{idx:06d}",
        "name": f"扩展场景-{node_type}-{idx:03d}",
        "chain": code,
        "body": '{"userId":"U10086","amount":128.5,"orderId":"ORD-MATRIX-001","step":0}',
    }
    return chain, scene


def main():
    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    existing = {c["code"] for c in data.get("chains", [])}
    start_idx = max([int(c["code"].split("_")[-1]) for c in data["chains"] if c["code"].startswith("CHN_DEMO_EXT_")] + [0]) + 1

    new_chains = []
    new_scenes = []
    idx = start_idx
    # 每类元件 5 个变体
    for round_no in range(1, 6):
        for tpl in NODE_TEMPLATES:
            comp = tpl.get("component") or COMPONENTS_ROTATE[idx % len(COMPONENTS_ROTATE)]
            tpl_copy = dict(tpl)
            if "component" not in tpl_copy or not tpl_copy["component"]:
                tpl_copy["component"] = comp
            chain, scene = build_chain(idx, tpl_copy, round_no)
            if chain["code"] not in existing:
                new_chains.append(chain)
                new_scenes.append(scene)
                existing.add(chain["code"])
            idx += 1

    data.setdefault("chains", []).extend(new_chains)
    data.setdefault("scenes", []).extend(new_scenes)
    JSON_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Added {len(new_chains)} chains, total {len(data['chains'])}, scenes {len(data['scenes'])}")


if __name__ == "__main__":
    main()
