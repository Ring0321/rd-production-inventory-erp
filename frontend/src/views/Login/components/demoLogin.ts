export interface PublicDemoLogin {
  username: 'admin'
  password: string
}

export const parsePublicDemoLogin = (value: unknown): PublicDemoLogin | null => {
  if (!value || typeof value !== 'object') return null
  const config = value as Record<string, unknown>
  if (
    config.enabled !== true ||
    config.syntheticDemoOnly !== true ||
    config.username !== 'admin' ||
    typeof config.password !== 'string' ||
    !/^[A-Za-z0-9_-]{12,16}$/.test(config.password)
  )
    return null
  return { username: 'admin', password: config.password }
}

// Optional, deliberately public deployment configuration, never a maintenance credential.
export const loadPublicDemoLogin = async (
  baseUrl = '/',
  fetcher: typeof fetch = fetch
): Promise<PublicDemoLogin | null> => {
  try {
    const response = await fetcher(`${baseUrl}demo-login.json`, {
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
      signal: AbortSignal.timeout(4000)
    })
    return response.ok ? parsePublicDemoLogin(await response.json()) : null
  } catch {
    return null
  }
}
