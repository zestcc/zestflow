import type { Router } from 'vue-router'

/** 跳转执行链管理并打开链详情抽屉 */
export function goToChainDetail(
  router: Router,
  chainCode: string | null | undefined,
  appCode?: string | null,
) {
  if (!chainCode?.trim()) return
  router.push({
    name: 'Chains',
    query: {
      chainCode: chainCode.trim(),
      ...(appCode ? { appCode } : {}),
    },
  })
}

/** 跳转演示场景并打开场景详情抽屉 */
export function goToPlaygroundSceneDetail(
  router: Router,
  sceneCode: string | null | undefined,
  appCode?: string | null,
) {
  if (!sceneCode?.trim()) return
  router.push({
    name: 'PlaygroundScenes',
    query: {
      sceneCode: sceneCode.trim(),
      ...(appCode ? { appCode } : {}),
    },
  })
}

/** 跳转日志查询并打开执行 trace 详情 */
export function goToLogDetail(
  router: Router,
  executionId: string | null | undefined,
  appCode?: string | null,
) {
  if (!executionId?.trim()) return
  router.push({
    name: 'Logs',
    query: {
      executionId: executionId.trim(),
      ...(appCode ? { appCode } : {}),
    },
  })
}
