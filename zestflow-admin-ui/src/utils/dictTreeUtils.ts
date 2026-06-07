import type { DictDataTreeVO } from '@/api/dict'

export type ParentTreeNode = {
  id: number
  label: string
  children?: ParentTreeNode[]
}

/** 去掉虚拟分组节点，供 el-tree-select 选同类型父级 */
export function buildParentTreeOptions(
  nodes: DictDataTreeVO[],
  excludeId?: number,
): ParentTreeNode[] {
  const result: ParentTreeNode[] = []
  for (const node of nodes) {
    if (node.virtualNode) {
      if (node.children?.length) {
        result.push(...buildParentTreeOptions(node.children, excludeId))
      }
      continue
    }
    if (node.id === excludeId) continue
    const children = node.children?.length
      ? buildParentTreeOptions(node.children, excludeId)
      : undefined
    result.push({
      id: node.id,
      label: `${node.label} (${node.value})`,
      children: children?.length ? children : undefined,
    })
  }
  return result
}

export function collectDescendantIds(nodes: DictDataTreeVO[], rootId: number): Set<number> {
  const ids = new Set<number>()
  function walk(list: DictDataTreeVO[]) {
    for (const n of list) {
      if (n.virtualNode) {
        if (n.children) walk(n.children)
        continue
      }
      if (n.id === rootId) {
        collectFromNode(n)
      } else if (n.children?.length) {
        walk(n.children)
      }
    }
  }
  function collectFromNode(node: DictDataTreeVO) {
    for (const c of node.children || []) {
      if (!c.virtualNode && c.id) {
        ids.add(c.id)
        collectFromNode(c)
      } else if (c.children) {
        collectFromNode(c)
      }
    }
  }
  walk(nodes)
  return ids
}
