type Point = Pick<PointerEvent, 'pointerId' | 'clientX' | 'clientY' | 'button'>

export const createSceneTap = () => {
  const active = new Set<number>()
  let candidate: Point | undefined
  return {
    start(point: Point) {
      candidate = !active.size && point.button === 0 ? point : undefined
      active.add(point.pointerId)
    },
    move(point: Point) {
      if (
        candidate?.pointerId === point.pointerId &&
        Math.hypot(point.clientX - candidate.clientX, point.clientY - candidate.clientY) > 6
      )
        candidate = undefined
    },
    end(point: Point) {
      const tap =
        active.size === 1 &&
        candidate?.pointerId === point.pointerId &&
        point.button === 0 &&
        Math.hypot(point.clientX - candidate.clientX, point.clientY - candidate.clientY) <= 6
      active.delete(point.pointerId)
      if (!active.size || candidate?.pointerId === point.pointerId) candidate = undefined
      return tap
    },
    cancel(point: Point) {
      active.delete(point.pointerId)
      candidate = undefined
    }
  }
}
