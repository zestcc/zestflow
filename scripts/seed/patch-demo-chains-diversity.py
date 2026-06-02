#!/usr/bin/env python3
"""Diversify demo chain node types and remove duplicate topologies within the matrix."""
import json
from pathlib import Path

path = Path(__file__).parent / 'demo-chains.json'
data = json.loads(path.read_text(encoding='utf-8'))

chains = {c['code']: c for c in data['chains']}

# --- tier 1-3: each chain unique component path + mixed visual types ---
chains['CHN_DEMO_ORDER_CREATE']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '创建订单', 'type': 'NORMAL', 'component': 'createOrder'},
    {'id': 'n3', 'label': '解析结果', 'type': 'NORMAL', 'component': 'parseOrderResult'},
]
chains['CHN_DEMO_ORDER_CREATE']['edges'] = [
    {'source': 'n1', 'target': 'n2'}, {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_ORDER_CREATE']['desc'] = '加载器→创建订单→解析器'

chains['CHN_DEMO_ORDER_PAY']['nodes'] = [
    {'id': 'n1', 'label': '金额风控', 'type': 'CONDITION', 'component': 'riskCheckAmount'},
    {'id': 'n2', 'label': '创建支付单', 'type': 'NORMAL', 'component': 'createPayment'},
    {'id': 'n3', 'label': '执行支付', 'type': 'NORMAL', 'component': 'processPayment'},
]
chains['CHN_DEMO_ORDER_PAY']['edges'] = [
    {'source': 'n1', 'target': 'n2', 'label': '通过'},
    {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_ORDER_PAY']['desc'] = '金额风控(条件)→创建支付→执行支付'

chains['CHN_DEMO_ORDER_REFUND']['nodes'] = [
    {'id': 'n1', 'label': '查询支付', 'type': 'NORMAL', 'component': 'queryPayment'},
    {'id': 'n2', 'label': '解析结果', 'type': 'NORMAL', 'component': 'parseOrderResult'},
    {'id': 'n3', 'label': '归还库存', 'type': 'NORMAL', 'component': 'restoreStock'},
]
chains['CHN_DEMO_ORDER_REFUND']['desc'] = '查询支付→解析器→归还库存'

chains['CHN_DEMO_ORDER_CANCEL']['nodes'] = [
    {'id': 'n1', 'label': '取消订单', 'type': 'NORMAL', 'component': 'cancelOrder'},
    {'id': 'n2', 'label': 'IP风控', 'type': 'CONDITION', 'component': 'riskCheckIp'},
    {'id': 'n3', 'label': '释放库存', 'type': 'NORMAL', 'component': 'unlockStock'},
]
chains['CHN_DEMO_ORDER_CANCEL']['edges'] = [
    {'source': 'n1', 'target': 'n2'},
    {'source': 'n2', 'target': 'n3', 'label': 'IP正常'},
]
chains['CHN_DEMO_ORDER_CANCEL']['desc'] = '取消订单→IP风控→释放库存'

chains['CHN_DEMO_ORDER_REVIEW']['nodes'] = [
    {'id': 'n1', 'label': '提交审核', 'type': 'NORMAL', 'component': 'submitAudit'},
    {'id': 'n2', 'label': '人工审核', 'type': 'NORMAL', 'component': 'manualAudit'},
    {'id': 'n3', 'label': '自动审批', 'type': 'NORMAL', 'component': 'autoApprove'},
]
chains['CHN_DEMO_ORDER_REVIEW']['desc'] = '提交审核→人工审核→自动审批'

chains['CHN_DEMO_MEMBER_REGISTER']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '创建订单', 'type': 'NORMAL', 'component': 'createOrder'},
    {'id': 'n3', 'label': '发送通知', 'type': 'NORMAL', 'component': 'sendNotify'},
]
chains['CHN_DEMO_MEMBER_REGISTER']['desc'] = '加载器→创建订单→通知'

chains['CHN_DEMO_MEMBER_UPGRADE']['nodes'] = [
    {'id': 'n1', 'label': '用户风控', 'type': 'CONDITION', 'component': 'riskCheckUser'},
    {'id': 'n2', 'label': '用户标签', 'type': 'NORMAL', 'component': 'checkUserTag'},
    {'id': 'n3', 'label': '计算返现', 'type': 'NORMAL', 'component': 'calcCashback'},
]
chains['CHN_DEMO_MEMBER_UPGRADE']['edges'] = [
    {'source': 'n1', 'target': 'n2', 'label': '用户正常'},
    {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_MEMBER_UPGRADE']['desc'] = '用户风控→标签→返现'

chains['CHN_DEMO_LEVEL_CALC']['nodes'] = [
    {'id': 'n1', 'label': '频率风控', 'type': 'CONDITION', 'component': 'riskCheckFrequency'},
    {'id': 'n2', 'label': '积分兑换', 'type': 'NORMAL', 'component': 'redeemPoints'},
    {'id': 'n3', 'label': '发放优惠券', 'type': 'NORMAL', 'component': 'issueCoupon'},
]
chains['CHN_DEMO_LEVEL_CALC']['edges'] = [
    {'source': 'n1', 'target': 'n2', 'label': '频率正常'},
    {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_LEVEL_CALC']['desc'] = '频率风控→积分兑换→发券'

chains['CHN_DEMO_POINTS_EARN']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '计算返现', 'type': 'NORMAL', 'component': 'calcCashback'},
    {'id': 'n3', 'label': '积分入账', 'type': 'NORMAL', 'component': 'redeemPoints'},
]
chains['CHN_DEMO_POINTS_EARN']['desc'] = '加载器→返现→积分入账'

chains['CHN_DEMO_POINTS_REDEEM']['nodes'] = [
    {'id': 'n1', 'label': '促销路由', 'type': 'CONDITION', 'component': 'routePromotion'},
    {'id': 'n2', 'label': '积分兑换', 'type': 'NORMAL', 'component': 'redeemPoints'},
    {'id': 'n3', 'label': '发放优惠券', 'type': 'NORMAL', 'component': 'issueCoupon'},
]
chains['CHN_DEMO_POINTS_REDEEM']['edges'] = [
    {'source': 'n1', 'target': 'n2', 'label': '积分'},
    {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_POINTS_REDEEM']['desc'] = '促销选择器→积分兑换→发券'

chains['CHN_DEMO_INVOICE']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '开具发票', 'type': 'NORMAL', 'component': 'createInvoice'},
    {'id': 'n3', 'label': '发票核验', 'type': 'NORMAL', 'component': 'verifyInvoice'},
]
chains['CHN_DEMO_INVOICE']['desc'] = '加载器→开票→核验'

chains['CHN_DEMO_COUPON_ISSUE']['nodes'] = [
    {'id': 'n1', 'label': '设备风控', 'type': 'CONDITION', 'component': 'riskCheckDevice'},
    {'id': 'n2', 'label': '发放优惠券', 'type': 'NORMAL', 'component': 'issueCoupon'},
    {'id': 'n3', 'label': '发送短信', 'type': 'NORMAL', 'component': 'sendSms'},
]
chains['CHN_DEMO_COUPON_ISSUE']['edges'] = [
    {'source': 'n1', 'target': 'n2', 'label': '设备可信'},
    {'source': 'n2', 'target': 'n3'},
]
chains['CHN_DEMO_COUPON_ISSUE']['desc'] = '设备风控→发券→短信'

chains['CHN_DEMO_FLASH_SALE']['nodes'] = [
    {'id': 'n1', 'label': '秒杀校验', 'type': 'NORMAL', 'component': 'seckillValidate'},
    {'id': 'n2', 'label': '金额风控', 'type': 'CONDITION', 'component': 'riskCheckAmount'},
    {'id': 'n3', 'label': '锁定库存', 'type': 'NORMAL', 'component': 'lockStock'},
]
chains['CHN_DEMO_FLASH_SALE']['edges'] = [
    {'source': 'n1', 'target': 'n2'},
    {'source': 'n2', 'target': 'n3', 'label': '通过'},
]
chains['CHN_DEMO_FLASH_SALE']['desc'] = '秒杀→金额风控→锁库存'

chains['CHN_DEMO_SMS_SEND']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '发送短信', 'type': 'NORMAL', 'component': 'sendSms'},
    {'id': 'n3', 'label': '查询消息', 'type': 'NORMAL', 'component': 'queryMsgStatus'},
]
chains['CHN_DEMO_SMS_SEND']['desc'] = '加载器→短信→查状态'

chains['CHN_DEMO_EMAIL_SEND']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '发送邮件', 'type': 'NORMAL', 'component': 'sendEmail'},
    {'id': 'n3', 'label': '查询消息', 'type': 'NORMAL', 'component': 'queryMsgStatus'},
]
chains['CHN_DEMO_EMAIL_SEND']['desc'] = '加载器→邮件→查状态'

chains['CHN_DEMO_ORDER_PIPELINE']['nodes'][0] = {
    'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo',
}

chains['CHN_DEMO_STOCK_IN']['nodes'] = [
    {'id': 'n1', 'label': '库存盘点', 'type': 'NORMAL', 'component': 'inventoryCount'},
    {'id': 'n2', 'label': '归还库存', 'type': 'NORMAL', 'component': 'restoreStock'},
    {'id': 'n3', 'label': '库存调拨', 'type': 'NORMAL', 'component': 'transferStock'},
]
chains['CHN_DEMO_STOCK_IN']['desc'] = '盘点→归还→调拨'

chains['CHN_DEMO_STOCK_CHECK']['nodes'] = [
    {'id': 'n1', 'label': '库存检查', 'type': 'NORMAL', 'component': 'checkStock'},
    {'id': 'n2', 'label': '库存预警', 'type': 'NORMAL', 'component': 'stockWarning'},
    {'id': 'n3', 'label': '提交审核', 'type': 'NORMAL', 'component': 'submitAudit'},
]
chains['CHN_DEMO_STOCK_CHECK']['desc'] = '检查→预警→提交审核'

chains['CHN_DEMO_BILL_GEN']['nodes'] = [
    {'id': 'n1', 'label': '查询支付', 'type': 'NORMAL', 'component': 'queryPayment'},
    {'id': 'n2', 'label': '解析结果', 'type': 'NORMAL', 'component': 'parseOrderResult'},
    {'id': 'n3', 'label': '生成报表', 'type': 'NORMAL', 'component': 'generateReport'},
]
chains['CHN_DEMO_BILL_GEN']['desc'] = '查支付→解析→报表'

chains['CHN_DEMO_RECONCILE']['nodes'] = [
    {'id': 'n1', 'label': '账户对账', 'type': 'NORMAL', 'component': 'reconcileAccount'},
    {'id': 'n2', 'label': '验签处理', 'type': 'NORMAL', 'component': 'verifySignature'},
    {'id': 'n3', 'label': '频率风控', 'type': 'CONDITION', 'component': 'riskCheckFrequency'},
]
chains['CHN_DEMO_RECONCILE']['edges'] = [
    {'source': 'n1', 'target': 'n2'},
    {'source': 'n2', 'target': 'n3', 'label': '验签通过'},
]
chains['CHN_DEMO_RECONCILE']['desc'] = '对账→验签→频率风控'

chains['CHN_DEMO_MEMBER_TOPUP']['nodes'] = [
    {'id': 'n1', 'label': '加载用户', 'type': 'NORMAL', 'component': 'loadUserInfo'},
    {'id': 'n2', 'label': '创建支付单', 'type': 'NORMAL', 'component': 'createPayment'},
    {'id': 'n3', 'label': '执行支付', 'type': 'NORMAL', 'component': 'processPayment'},
]
chains['CHN_DEMO_MEMBER_TOPUP']['desc'] = '加载器→创建支付→执行支付'

chains['CHN_DEMO_LOGISTICS_SHIP']['nodes'] = [
    {'id': 'n1', 'label': '创建发货单', 'type': 'NORMAL', 'component': 'createDelivery'},
    {'id': 'n2', 'label': '打印运单', 'type': 'NORMAL', 'component': 'printWaybill'},
    {'id': 'n3', 'label': '揽件处理', 'type': 'NORMAL', 'component': 'pickupPackage'},
]
chains['CHN_DEMO_LOGISTICS_SHIP']['desc'] = '发货单→运单→揽件'

data['chains'] = list(chains.values())
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('patched', len(chains), 'chains')
