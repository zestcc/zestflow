#!/usr/bin/env python3
"""Patch demo-chains.json so all 151 chains are executable in matrix tests."""
import json
import re
from pathlib import Path

JSON_PATH = Path(__file__).resolve().parent / "demo-chains.json"
DEFAULT_BODY = {
    "userId": "U10086",
    "userName": "Matrix User",
    "amount": 128.5,
    "orderId": "ORD-MATRIX-001",
    "step": 0,
    "_http_url": "http://127.0.0.1:8081/test/http-echo",
}

EXT_NAME = re.compile(r"扩展链-([A-Z_]+)-\d+$")

EXT_N1 = {
    "NORMAL": {"type": "NORMAL", "component": "validateUser"},
    "SCRIPT": {"type": "SCRIPT", "script": "amount > 0"},
    "SUB_CHAIN": {"type": "SUB_CHAIN", "subChainCode": "CHN_DEMO_ORDER_CREATE"},
    "CONDITION": {"type": "CONDITION", "component": "riskCheckAmount"},
    "SELECTOR": {"type": "SELECTOR", "component": "routePromotion"},
    "ITERATOR": {
        "type": "ITERATOR",
        "config": {"dataSource": "notifyItems", "itemName": "item", "subNodes": [{"component": "sendNotify"}]},
    },
    "FORK": {"type": "FORK", "component": "validateUser"},
    "JOIN": {"type": "JOIN", "component": "sendNotify"},
    "TRY_CATCH": {"type": "TRY_CATCH", "component": "validateUser"},
    "WHILE": {"type": "WHILE", "component": "validateUser", "config": {"condition": "step < 0"}},
    "DELAY": {"type": "DELAY", "component": "validateUser", "config": {"delayMs": 10}},
    "LOGGER": {"type": "LOGGER", "component": "validateUser"},
    "TRANSFORMER": {"type": "TRANSFORMER", "component": "transformOrderStatus"},
    "FILTER": {"type": "FILTER", "component": "filterValidUsers"},
    "AGGREGATOR": {"type": "AGGREGATOR", "component": "aggregateOrderAmounts"},
    "SPLITTER": {"type": "SPLITTER", "component": "splitOrdersByStatus"},
    "HTTP_CLIENT": {"type": "HTTP_CLIENT", "component": "httpGet"},
    "MQ_PRODUCER": {"type": "MQ_PRODUCER", "component": "sendOrderCreatedMsg"},
    "MQ_CONSUMER": {"type": "MQ_CONSUMER", "component": "consumeOrderCreatedMsg"},
    "CACHE_READER": {"type": "CACHE_READER", "component": "getUserCache"},
    "CACHE_WRITER": {"type": "CACHE_WRITER", "component": "setUserCache"},
    "APPROVAL": {"type": "APPROVAL", "component": "validateUser"},
    "NOTIFICATION": {"type": "NOTIFICATION", "component": "sendNotify"},
}


def patch_while(chain):
    for node in chain.get("nodes", []):
        if node.get("type") == "WHILE":
            cfg = node.setdefault("config", {})
            if not cfg.get("condition"):
                cfg["condition"] = "step < 0"


def patch_delay(chain):
    for node in chain.get("nodes", []):
        if node.get("type") == "DELAY":
            cfg = node.setdefault("config", {})
            cfg.setdefault("delayMs", 10)


def patch_iterator(chain):
    nodes = chain.get("nodes", [])
    if not any(n.get("type") == "ITERATOR" for n in nodes):
        return
    if any(n.get("component") == "seedNotifyItems" for n in nodes):
        return
    seed = {"id": "n0", "label": "种子通知列表", "type": "NORMAL", "component": "seedNotifyItems"}
    chain["nodes"] = [seed] + nodes
    edges = chain.get("edges", [])
    if edges and edges[0].get("source") == "n1":
        edges.insert(0, {"source": "n0", "target": "n1"})


def patch_script(chain):
    for node in chain.get("nodes", []):
        if node.get("type") == "SCRIPT" and node.get("script") == "amount > 0":
            node["script"] = "amount > 0"


def restore_ext_node_types(chain):
    """Restore EXT chain n1 from name pattern (undo historical downgrade to NORMAL)."""
    m = EXT_NAME.match(chain.get("name", ""))
    if not m:
        return
    node_type = m.group(1)
    spec = EXT_N1.get(node_type)
    if not spec:
        return
    for node in chain.get("nodes", []):
        if node.get("id") != "n1":
            continue
        node["type"] = spec["type"]
        for key in ("component", "script", "subChainCode", "config"):
            if key in spec:
                node[key] = spec[key]
            elif key in node and key not in spec:
                if key != "config":
                    node.pop(key, None)


def patch_http_config(chain):
    for node in chain.get("nodes", []):
        if node.get("type") == "HTTP_CLIENT":
            cfg = node.setdefault("config", {})
            cfg.setdefault("httpMethod", "GET")
            cfg.setdefault("httpUrl", "http://127.0.0.1:8081/test/http-echo")


def main():
    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    scene_by_chain = {s["chain"]: s for s in data.get("scenes", [])}
    patched = 0
    for chain in data.get("chains", []):
        code = chain.get("code", "")
        before = json.dumps(chain, ensure_ascii=False)
        restore_ext_node_types(chain)
        patch_while(chain)
        patch_delay(chain)
        patch_iterator(chain)
        patch_script(chain)
        patch_http_config(chain)
        if json.dumps(chain, ensure_ascii=False) != before:
            patched += 1
        scene = scene_by_chain.get(code)
        if scene:
            body = DEFAULT_BODY.copy()
            try:
                raw = json.loads(scene.get("body") or "{}")
                if isinstance(raw, dict):
                    body.update(raw)
            except json.JSONDecodeError:
                pass
            scene["body"] = json.dumps(body, ensure_ascii=False)

    JSON_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Patched {patched} chains, scenes={len(data.get('scenes', []))}, chains={len(data.get('chains', []))}")


if __name__ == "__main__":
    main()
