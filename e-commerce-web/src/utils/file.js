/**
 * 浏览器端文件工具。
 * 只保留 getBase64；原 readEnvFile/getEnv 是 Node 专用且已无人使用，
 * 去掉 fs/path 依赖，避免 Vite 打 production 包时出现 externalized 警告。
 */

/**
 * 获取文件 base64 编码内容
 * @param file
 * @returns
 */
export async function getBase64(file) {
  const promise = new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(reader.result));
    try {
      reader.readAsDataURL(file);
    } catch (e) {
      reject(e);
    }
  });
  return promise;
}
