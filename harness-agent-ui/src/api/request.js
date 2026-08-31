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
      headers: options.body instanceof FormData
        ? { Accept: DEFAULT_HEADERS.Accept, ...options.headers }
        : { ...DEFAULT_HEADERS, ...options.headers },
      signal: controller.signal
    })

    clearTimeout(timeoutId)

    if (!response.ok) {
      let message = `HTTP ${response.status}: ${response.statusText}`
      try {
        const errorData = await response.json()
        message = errorData?.error || errorData?.message || message
      } catch {
        // 非 JSON 错误响应时保留 HTTP 状态信息
      }
      throw new Error(message)
    }

    // DELETE 等接口成功时可能返回空响应体，不再强制解析 JSON
    const contentType = response.headers.get('content-type') || ''
    if (response.status === 204 || !contentType.includes('application/json')) {
      return null
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

export async function put(endpoint, body) {
  return request(endpoint, {
    method: 'PUT',
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

/**
 * DELETE 请求封装
 * @param {string} endpoint - API 路径
 * @returns {Promise<object>}
 */
export async function del(endpoint) {
  return request(endpoint, {
    method: 'DELETE'
  })
}
