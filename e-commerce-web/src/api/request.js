import axios from 'axios'
import {message} from 'ant-design-vue'
import {getToken, removeToken} from '@/utils/token'

axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL
axios.defaults.timeout = 10000

// 登录页地址：token 失效后要回到这里重新登录
const LOGIN_PATH = import.meta.env.BASE_URL + 'front/login'

// 401 统一处理：token 失效后继续留在后台页面上只会一直报错，必须把用户送回登录页
function handleUnauthorized(error) {
  const msg = (error.response.data && error.response.data.message) || '登录已失效，请重新登录'
  removeToken()
  // 已经在登录页（例如密码输错）就地提示，不跳转
  if (window.location.pathname === LOGIN_PATH) {
    message.error(msg)
    return
  }
  // 整页跳转会丢掉内存里的提示，用 sessionStorage 把原因带给登录页展示
  sessionStorage.setItem('loginMessage', msg)
  window.location.href = LOGIN_PATH
}

// 请求拦截器
axios.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      switch (error.response.status) {
        case 400:
          // 参数/业务校验失败，后端 message 已可直接展示（如「该手机号未绑定后台账号」）
          message.error((error.response.data && error.response.data.message) || '请求参数错误，请检查填写内容')
          break
        case 401:
          handleUnauthorized(error)
          break
        case 403:
          // 后端会区分「仅管理员可执行」等具体原因，优先展示它
          message.error((error.response.data && error.response.data.message) || '无权限操作，请联系管理员')
          break
        case 404:
          message.error('请求资源不存在')
          break
        case 429:
          // 验证码发送频率限制等场景，后端 message 已可直接展示
          message.warning((error.response.data && error.response.data.message) || '操作过于频繁，请稍后再试')
          break
        case 500:
          message.error('服务器内部错误，请稍后重试')
          break
      }
    }
    return Promise.reject(error)
  }
)

export default axios
