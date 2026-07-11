import CryptoJS from 'crypto-js'

/**
 * API 加解密工具类
 * 仅保留 ERP 当前使用的 AES 加密能力。
 */

// 从环境变量获取配置
const API_ENCRYPT_ENABLE = import.meta.env.VITE_APP_API_ENCRYPT_ENABLE === 'true'
const API_ENCRYPT_HEADER = import.meta.env.VITE_APP_API_ENCRYPT_HEADER || 'X-Api-Encrypt'
const API_ENCRYPT_REQUEST_KEY = import.meta.env.VITE_APP_API_ENCRYPT_REQUEST_KEY || ''
const API_ENCRYPT_RESPONSE_KEY = import.meta.env.VITE_APP_API_ENCRYPT_RESPONSE_KEY || ''

/**
 * AES 加密工具类
 */
export class AES {
  /**
   * AES 加密
   * @param data 要加密的数据
   * @param key 加密密钥
   * @returns 加密后的字符串
   */
  static encrypt(data: string, key: string): string {
    try {
      if (!key) {
        throw new Error('AES 加密密钥不能为空')
      }
      if (key.length !== 32) {
        throw new Error(`AES 加密密钥长度必须为 32 位，当前长度: ${key.length}`)
      }

      const keyUtf8 = CryptoJS.enc.Utf8.parse(key)
      const encrypted = CryptoJS.AES.encrypt(data, keyUtf8, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
      })
      return encrypted.toString()
    } catch (error) {
      console.error('AES 加密失败:', error)
      throw error
    }
  }

  /**
   * AES 解密
   * @param encryptedData 加密的数据
   * @param key 解密密钥
   * @returns 解密后的字符串
   */
  static decrypt(encryptedData: string, key: string): string {
    try {
      if (!key) {
        throw new Error('AES 解密密钥不能为空')
      }
      if (key.length !== 32) {
        throw new Error(`AES 解密密钥长度必须为 32 位，当前长度: ${key.length}`)
      }
      if (!encryptedData) {
        throw new Error('AES 解密数据不能为空')
      }

      const keyUtf8 = CryptoJS.enc.Utf8.parse(key)
      const decrypted = CryptoJS.AES.decrypt(encryptedData, keyUtf8, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
      })
      const result = decrypted.toString(CryptoJS.enc.Utf8)
      if (!result) {
        throw new Error('AES 解密结果为空，可能是密钥错误或数据损坏')
      }
      return result
    } catch (error) {
      console.error('AES 解密失败:', error)
      throw error
    }
  }
}

/**
 * API 加解密主类
 */
export class ApiEncrypt {
  /**
   * 获取加密头名称
   */
  static getEncryptHeader(): string {
    return API_ENCRYPT_HEADER
  }

  /**
   * 加密请求数据
   * @param data 要加密的数据
   * @returns 加密后的数据
   */
  static encryptRequest(data: any): string {
    if (!API_ENCRYPT_ENABLE) {
      return data
    }

    try {
      const jsonData = typeof data === 'string' ? data : JSON.stringify(data)

      return AES.encrypt(jsonData, API_ENCRYPT_REQUEST_KEY)
    } catch (error) {
      console.error('请求数据加密失败:', error)
      throw error
    }
  }

  /**
   * 解密响应数据
   * @param encryptedData 加密的响应数据
   * @returns 解密后的数据
   */
  static decryptResponse(encryptedData: string): any {
    if (!API_ENCRYPT_ENABLE) {
      return encryptedData
    }

    try {
      const decryptedData = AES.decrypt(encryptedData, API_ENCRYPT_RESPONSE_KEY)

      if (!decryptedData) {
        throw new Error('解密结果为空')
      }

      // 尝试解析为 JSON，如果失败则返回原字符串
      try {
        return JSON.parse(decryptedData)
      } catch {
        return decryptedData
      }
    } catch (error) {
      console.error('响应数据解密失败:', error)
      throw error
    }
  }
}
