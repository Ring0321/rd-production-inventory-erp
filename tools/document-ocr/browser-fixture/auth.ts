let revision = 1
const listeners = new Set<() => void>()
export const getAccessToken = () => 'synthetic-ui-fixture-only'
export const getAuthContextRevision = () => revision
export const onAuthContextChange = (callback: () => void) => { listeners.add(callback); return () => listeners.delete(callback) }
export const changeAuth = () => { revision++; listeners.forEach((listener) => listener()) }
