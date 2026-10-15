// UI-only surrogate backend. The production component still uses the original axios adapter.
interface Options { url: string; data?: FormData; signal?: AbortSignal; [key: string]: unknown }
const call = async <T>(method: string, options: Options): Promise<T> => {
  const response = await fetch(options.url, { method, body: options.data, signal: options.signal })
  return (await response.json()).data as T
}
export default {
  get: <T>(options: Options) => call<T>('GET', options),
  post: <T>(options: Options) => call<T>('POST', options)
}
