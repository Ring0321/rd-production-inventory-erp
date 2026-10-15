import { ElMessageBox } from 'element-plus'

export const createEditorDialogs = () => {
  let active = false
  let disposed = false
  let cancel: ((error: Error) => void) | undefined
  const run = async <T>(show: () => Promise<T>): Promise<T> => {
    if (active || disposed) throw new Error('当前不可打开确认窗口')
    active = true
    const cancellation = new Promise<never>((_, reject) => {
      cancel = reject
    })
    try {
      return await Promise.race([show(), cancellation])
    } finally {
      active = false
      cancel = undefined
    }
  }
  return {
    confirm: (...args: Parameters<typeof ElMessageBox.confirm>) =>
      run(() => ElMessageBox.confirm(...args)),
    prompt: (...args: Parameters<typeof ElMessageBox.prompt>) =>
      run(() => ElMessageBox.prompt(...args)),
    close() {
      disposed = true
      if (active) {
        ElMessageBox.close()
        cancel?.(new Error('身份或页面已变化'))
      }
    }
  }
}
