import type { NodeTypeMeta } from '@/config/nodeTypeRegistry'

type ShapeRegistrar = (name: string, options: Record<string, unknown>) => void

/** 批量注册 X6 节点形状（矩形类节点统一 markup） */
export function registerFlowShapes(
  reg: ShapeRegistrar,
  metas: NodeTypeMeta[],
  labelOf: (i18nKey: string, fallback: string) => string,
  terminalLabels: { start: string; end: string },
) {
  const handleGroup = {
    position: 'absolute',
    attrs: { circle: { r: 5, magnet: true, stroke: '#fff', strokeWidth: 2 } },
  }

  metas.forEach(meta => {
    if (meta.type === 'start' || meta.type === 'end') {
      const isStart = meta.type === 'start'
      reg(meta.shape, {
        inherit: 'rect',
        width: meta.size[0],
        height: meta.size[1],
        markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
        attrs: {
          body: { rx: 20, ry: 20, fill: meta.color, stroke: 'none' },
          label: {
            text: isStart ? terminalLabels.start : terminalLabels.end,
            fill: '#fff', fontSize: 13, fontWeight: 600,
            refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer',
          },
        },
      })
      return
    }

    const isDiamond = meta.type === 'CONDITION' || meta.type === 'SELECTOR' || meta.type === 'WHILE'
    if (isDiamond) {
      const refPoints = meta.type === 'SELECTOR'
        ? '85,0 120,40 85,80 35,80 0,40 35,0'
        : meta.type === 'WHILE'
          ? '50,5 95,40 50,75 5,40'
          : '50,0 100,40 50,80 0,40'
      reg(meta.shape, {
        inherit: 'polygon',
        width: meta.size[0],
        height: meta.size[1],
        markup: [{ tagName: 'polygon', selector: 'body' }, { tagName: 'text', selector: 'label' }],
        attrs: {
          body: { refPoints, fill: meta.color, stroke: 'none' },
          label: {
            text: labelOf(meta.i18nKey, meta.type),
            fill: '#fff', fontSize: 12, fontWeight: 600,
            refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer',
          },
        },
      })
      return
    }

    const rx = meta.type === 'SUB_CHAIN' ? 23 : meta.type === 'ITERATOR' ? 8 : 8
    const extraBody: Record<string, unknown> = meta.type === 'ITERATOR'
      ? { stroke: '#fff', strokeWidth: 2, strokeDasharray: '4,2' }
      : {}

    reg(meta.shape, {
      inherit: 'rect',
      width: meta.size[0],
      height: meta.size[1],
      markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
      attrs: {
        body: { rx, ry: meta.type === 'SUB_CHAIN' ? 23 : 8, fill: meta.color, stroke: 'none', ...extraBody },
        label: {
          text: labelOf(meta.i18nKey, meta.type),
          fill: '#ffffff', fontSize: 12, fontWeight: 600,
          refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer',
        },
      },
    })
  })

  return handleGroup
}
