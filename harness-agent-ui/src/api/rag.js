import { del, get, post, put, request } from '@/api/request'
import { API_ROUTES } from '@/api/config'

const base = API_ROUTES.RAG_KNOWLEDGE_BASES

/**
 * RAG 知识库 API。
 */
export const ragApi = {
  listKnowledgeBases: () => get(base),
  createKnowledgeBase: (data) => post(base, data),
  updateKnowledgeBase: (id, data) => put(`${base}/${id}`, data),
  deleteKnowledgeBase: (id) => del(`${base}/${id}`),
  listDocuments: (knowledgeBaseId) => get(`${base}/${knowledgeBaseId}/documents`),
  createDocument: (knowledgeBaseId, data) => post(`${base}/${knowledgeBaseId}/documents`, data),
  uploadDocument: (knowledgeBaseId, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request(`${base}/${knowledgeBaseId}/documents/upload`, {
      method: 'POST',
      body: formData,
      headers: {}
    })
  },
  deleteDocument: (knowledgeBaseId, documentId) => (
    del(`${base}/${knowledgeBaseId}/documents/${documentId}`)
  ),
  listChunks: (knowledgeBaseId, documentId) => (
    get(`${base}/${knowledgeBaseId}/documents/${documentId}/chunks`)
  ),
  search: (knowledgeBaseId, data) => post(`${base}/${knowledgeBaseId}/search`, data)
}
