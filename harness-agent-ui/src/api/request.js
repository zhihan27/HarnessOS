import { BASE_URL, API_ROUTES, DEFAULT_HEADERS, REQUEST_TIMEOUT } from '@/api/config'

/**
 * 统一请求封装
 * @param {string} endpoint - API 路径
 * @param {object} options - fetch 配置项
 * @returns {Promise<object>} - 响应数据
 */
export async function request(endpoint, options = {}) {
  const url = BASE_URL + endpoint
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)

  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        ...DEFAULT_HEADERS,
        ...options.headers
      },
      signal: controller.signal
    })

    clearTimeout(timeoutId)

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const data = await response.json()
    return data
  } catch (error) {
    clearTimeout(timeoutId)
    if (error.name === 'AbortError') {
      throw new Error('请求超时')
    }
    throw error
  }
}

/**
 * POST 请求封装
 * @param {string} endpoint - API 路径
 * @param {object} body - 请求体
 * @returns {Promise<object>}
 */
export async function post(endpoint, body) {
  return request(endpoint, {
    method: 'POST',
    body: JSON.stringify(body)
  })
}

/**
 * GET 请求封装
 * @param {string} endpoint - API 路径
 * @returns {Promise<object>}
 */
export async function get(endpoint) {
  return request(endpoint, {
    method: 'GET'
  })
}