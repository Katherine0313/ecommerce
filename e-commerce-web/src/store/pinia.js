// 创建并导出全局唯一的 pinia 实例（供 cart / setting 等 store 使用）
import { createPinia } from 'pinia';

const pinia = createPinia();

export default pinia;
