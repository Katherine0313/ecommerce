import axios from './request.js'

/** 数据库表清单 */
export function getDbTables(params) {
  return axios.get('/api/admin/system/database/tables', { params })
}

/** 表字段列表 */
export function getTableColumns(table) {
  return axios.get(`/api/admin/system/database/tables/${encodeURIComponent(table)}/columns`)
}

/** 批量优化表（大表较慢，单独放宽超时） */
export function optimizeTables(tables) {
  return axios.post('/api/admin/system/database/tables/optimize', { tables }, { timeout: 60000 })
}

/** 发起备份（异步任务） */
export function createDbBackup(tables) {
  return axios.post('/api/admin/system/database/backups', { tables })
}

/** 备份任务进度 */
export function getDbBackupTask(taskId) {
  return axios.get(`/api/admin/system/database/tasks/${taskId}`)
}

/** 备份记录 */
export function getDbBackups() {
  return axios.get('/api/admin/system/database/backups')
}

/** 下载备份（blob） */
export function downloadDbBackup(fileName) {
  return axios.get(
    `/api/admin/system/database/backups/${encodeURIComponent(fileName)}/download`,
    { responseType: 'blob', timeout: 60000 }
  )
}

/** 删除备份 */
export function deleteDbBackup(fileName) {
  return axios.delete(`/api/admin/system/database/backups/${encodeURIComponent(fileName)}`)
}
