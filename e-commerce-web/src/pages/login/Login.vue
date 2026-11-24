<template>
  <div class="auth-shell">
    <!-- 背景装饰层：光斑 + 网格 + 噪点，全部纯 CSS，无外部图片依赖 -->
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="aurora aurora-3"></div>
    <div class="grain"></div>

    <main class="stage">
      <!-- 页面只保留系统名称，不再堆营销文案 -->
      <div class="hero">
        <div class="hero-mark">
          <!-- 兔耳挂件：画在头像下层，耳朵从圆上方探出 -->
          <svg class="hero-accessory" viewBox="0 -20 44 64" aria-hidden="true" focusable="false">
            <g transform="rotate(-12 12.2 4)">
              <ellipse cx="12.2" cy="-6" rx="6" ry="13" fill="#f8dbe2" />
              <ellipse cx="12.2" cy="-5" rx="2.8" ry="9.4" fill="#f3b6c6" />
            </g>
            <g transform="rotate(12 31.8 4)">
              <ellipse cx="31.8" cy="-6" rx="6" ry="13" fill="#f8dbe2" />
              <ellipse cx="31.8" cy="-5" rx="2.8" ry="9.4" fill="#f3b6c6" />
            </g>
          </svg>
          <div class="hero-photo">
            <img :src="avatar" alt="杰米多多" />
          </div>
        </div>
        <h1>杰米多多电商后台管理系统</h1>
      </div>

      <section class="panel">
        <form class="auth-form" @submit.prevent="submitAccount">
          <div class="field-group">
            <div class="field">
              <label for="login-username">账号</label>
              <a-input
                  id="login-username"
                  v-model:value="accountForm.username"
                  autocomplete="username"
                  placeholder="用户名 / 邮箱"
                  @focus="prefetchWorkplace"
              >
                <template #prefix>
                  <UserOutlined class="field-icon" />
                </template>
              </a-input>
            </div>
            <div class="field">
              <label for="login-password">密码</label>
              <a-input-password
                  id="login-password"
                  v-model:value="accountForm.password"
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  @focus="prefetchWorkplace"
              >
                <template #prefix>
                  <LockOutlined class="field-icon" />
                </template>
              </a-input-password>
            </div>
            <div class="field">
              <label for="login-captcha">图形验证码</label>
              <div class="captcha-row">
                <a-input
                    id="login-captcha"
                    v-model:value="captchaCode"
                    autocomplete="off"
                    :maxlength="4"
                    placeholder="请输入右侧验证码"
                    @pressEnter="submitAccount"
                >
                  <template #prefix>
                    <SafetyCertificateOutlined class="field-icon" />
                  </template>
                </a-input>
                <img
                    v-if="captchaImage"
                    :src="captchaImage"
                    class="captcha-img"
                    alt="图形验证码"
                    title="点击刷新"
                    @click="loadCaptcha"
                />
                <button v-else type="button" class="captcha-img captcha-refresh" @click="loadCaptcha">
                  {{ captchaLoading ? '加载中…' : '点击刷新' }}
                </button>
              </div>
            </div>
            <!-- 记住密码：只回填登录页，不参与鉴权 -->
            <div class="remember-row">
              <a-checkbox v-model:checked="remember">记住密码</a-checkbox>
              <span class="remember-tip">30 天内自动回填</span>
            </div>
            <button class="submit-btn" type="submit" :disabled="loading">
              <span v-if="loading" class="spinner"></span>
              {{ loading ? '登录中…' : '登 录' }}
            </button>
          </div>
        </form>
      </section>
    </main>

    <footer class="page-foot">
      © {{ year }} 杰米多多 · 登录即代表同意用户条款、数据使用协议与 Cookie 使用协议
    </footer>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import {
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import {getCaptcha, login} from '@/api/auth.js';
import {setToken} from '@/utils/token';
import {clearRemember, loadRemember, saveRemember} from '@/utils/remember';
import {connectOrderNotify} from '@/utils/orderNotify';
import router from '@/router';
import avatar from '@/assets/avatar/zongjie.webp';

const year = new Date().getFullYear();

const loading = ref(false);
const captchaLoading = ref(false);
const captchaId = ref('');
const captchaImage = ref('');
const captchaCode = ref('');
// 是否已开始预取工作台代码块（只触发一次）
let prefetchStarted = false;

// 固定预填演示账号（开发和生产都回填）
const accountForm = reactive({
  username: 'saler',
  password: 'saler123',
});

// 记住密码：有历史记录时优先回填（30 天过期）
const remembered = loadRemember();
const remember = ref(!!remembered);
if (remembered) {
  accountForm.username = remembered.username;
  accountForm.password = remembered.password;
}

// 拉取图形验证码：点击图片或登录失败后刷新
const loadCaptcha = () => {
  captchaLoading.value = true;
  getCaptcha()
      .then((res) => {
        const data = res.data.data || {};
        captchaId.value = data.captchaId || '';
        captchaImage.value = data.image || '';
        captchaCode.value = '';
      })
      .catch(() => {
        // 错误提示由 axios 拦截器统一处理
        captchaImage.value = '';
      })
      .finally(() => {
        captchaLoading.value = false;
      });
};

// 用户开始填表时才预取工作台代码块，登录后 router.push 直接命中缓存。
// 不在页面加载时定时预取：那样爬虫/扫描器访问登录页也会白拉 178kB 无关代码块。
const prefetchWorkplace = () => {
  if (prefetchStarted) return;
  prefetchStarted = true;
  import('@/pages/workplace/Workplace.vue').catch(() => {});
};

onMounted(() => {
  // 401 拦截器跳回本页时留下的原因（例如「登录已过期，请重新登录」）
  const tip = sessionStorage.getItem('loginMessage');
  if (tip) {
    message.warning(tip);
    sessionStorage.removeItem('loginMessage');
  }
  // 进入登录页先拉图形验证码
  loadCaptcha();
});

// 登录成功后的公共处理
const finishLogin = (res) => {
  if (res.data.success) {
    console.log(res.data)
    setToken(res.data.data.token);
    connectOrderNotify();
    message.success('登录成功');
    // router.push 返回 Promise：路由守卫拒绝或懒加载 chunk 失败时会 reject，
    // 不 catch 就什么提示都没有，用户只看到「登录成功」却停在原地。
    router.push('/workplace').catch((err) => {
      console.error('登录后跳转失败', err);
      const failedToLoad = err?.message?.includes('Failed to fetch')
          || err?.message?.includes('dynamically imported module');
      if (failedToLoad) {
        message.error('页面资源加载失败，请刷新页面后重试（如仍失败需重新部署前端）');
      } else {
        message.warning((err && err.message) || '跳转失败，请手动刷新页面');
      }
    });
  }
};

const submitAccount = () => {
  if (!accountForm.username.trim()) {
    message.warning('请输入账号');
    return;
  }
  if (!accountForm.password) {
    message.warning('请输入密码');
    return;
  }
  if (!captchaCode.value.trim()) {
    message.warning('请输入图形验证码');
    return;
  }
  if (!captchaId.value) {
    message.warning('验证码已失效，请刷新后重试');
    loadCaptcha();
    return;
  }
  loading.value = true;
  const username = accountForm.username.trim();
  login({
    username,
    password: accountForm.password,
    captchaId: captchaId.value,
    // 前端收集验证码，交给后端比对
    captchaCode: captchaCode.value.trim(),
  })
      .then((res) => {
        // 登录成功后才保存，避免把错误密码记下来
        if (res.data && res.data.success) {
          if (remember.value) {
            saveRemember(username, accountForm.password);
          } else {
            clearRemember();
          }
        }
        finishLogin(res);
      })
      .catch(() => {
        // 密码/验证码错误等由 axios 拦截器统一提示；验证码一次性使用，失败后刷新
        loadCaptcha();
      })
      .finally(() => {
        loading.value = false;
      });
};
</script>

<style scoped lang="less">
.auth-shell {
  --ink: #eef4ff;
  /* 卡片已改为浅色玻璃，这里用深灰蓝保证浅底上的可读性 */
  --muted: #5f7799;
  --gold: #ffca29;
  --line: rgba(16, 52, 100, 0.14);

  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 96px 24px 88px;
  overflow: hidden;
  isolation: isolate;
  color: var(--ink);
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, -apple-system, sans-serif;
  background:
      radial-gradient(120% 90% at 8% 0%, #5aa2f0 0%, transparent 60%),
      radial-gradient(100% 80% at 95% 6%, #3d8be4 0%, transparent 62%),
      linear-gradient(160deg, #2a78cc 0%, #3284dd 46%, #1a5aa8 100%);

  /* ============ 背景光斑 ============ */
  .aurora {
    position: absolute;
    z-index: -3;
    border-radius: 50%;
    filter: blur(90px);
    pointer-events: none;
  }

  .aurora-1 {
    width: 620px;
    height: 620px;
    top: -220px;
    left: -160px;
    background: radial-gradient(circle at 40% 40%, rgba(255, 202, 41, 0.28), rgba(255, 202, 41, 0) 65%);
  }

  .aurora-2 {
    width: 760px;
    height: 760px;
    right: -260px;
    bottom: -320px;
    background: radial-gradient(circle at 50% 50%, rgba(24, 150, 255, 0.5), rgba(24, 150, 255, 0) 66%);
  }

  .aurora-3 {
    width: 460px;
    height: 460px;
    left: 52%;
    top: 58%;
    background: radial-gradient(circle, rgba(0, 214, 255, 0.16), rgba(0, 214, 255, 0) 70%);
  }

  /* 噪点：抵消大面积渐变的塑料感 */
  .grain {
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    opacity: 0.05;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E");
  }

  /* ============ 系统名称 ============ */
  .hero {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 14px;

    .hero-mark {
      position: relative;
      width: 44px;
      height: 44px;
      flex: 0 0 44px;
      border: 2px solid rgba(255, 210, 80, 0.95);
      border-radius: 50%;
      background: #fff;
      box-shadow: 0 8px 20px -8px rgba(255, 202, 41, 0.8);

      /* 兔耳层在头像下方，超出的部分不会被裁掉 */
      .hero-accessory {
        position: absolute;
        top: -45.5%;
        left: 0;
        z-index: 1;
        width: 100%;
        height: auto;
        pointer-events: none;
      }

      .hero-photo {
        position: relative;
        z-index: 2;
        width: 100%;
        height: 100%;
        overflow: hidden;
        border-radius: 50%;
      }

      img {
        display: block;
        width: 100%;
        height: 100%;
        object-fit: cover;
        object-position: center 40%;
      }
    }

    h1 {
      margin: 0;
      color: #f7faff;
      font-size: 28px;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-shadow: 0 4px 24px rgba(0, 24, 60, 0.28);
      white-space: nowrap;
    }
  }

  /* ============ 主区域 ============ */
  .stage {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 30px;
    width: 100%;
    max-width: 460px;
  }

  /* ============ 毛玻璃登录卡片 ============ */
  .panel {
    position: relative;
    width: 100%;
    max-width: 424px;
    padding: 30px 34px 26px;
    border: 1px solid rgba(255, 255, 255, 0.75);
    border-radius: 26px;
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.94), rgba(236, 246, 255, 0.88));
    box-shadow:
        0 40px 90px -32px rgba(2, 20, 50, 0.55),
        0 1px 0 rgba(255, 255, 255, 0.9) inset;
    color: #10243f;

    /* 顶部金色高光细线 */
    &::before {
      content: '';
      position: absolute;
      inset: -1px 24% auto;
      height: 1px;
      background: linear-gradient(90deg, transparent, rgba(255, 202, 41, 0.9), transparent);
    }
  }

  /* ============ 表单 ============ */
  .auth-form {
    margin-top: 22px;
  }

  .field {
    margin-bottom: 16px;

    label {
      display: block;
      margin-bottom: 8px;
      color: var(--muted);
      font-size: 12.5px;
      letter-spacing: 0.08em;
    }
  }

  /* 覆盖 antd 默认输入框，换成浅色玻璃质感 */
  .field :deep(.ant-input-affix-wrapper),
  .field :deep(.ant-input) {
    height: 48px;
    padding: 0 14px;
    border: 1px solid var(--line) !important;
    border-radius: 12px !important;
    background: rgba(255, 255, 255, 0.72) !important;
    color: #10243f !important;
    box-shadow: none !important;
    transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
  }

  .field :deep(.ant-input-affix-wrapper:hover),
  .field :deep(.ant-input:hover) {
    border-color: rgba(255, 202, 41, 0.7) !important;
    background: rgba(255, 255, 255, 0.9) !important;
  }

  .field :deep(.ant-input-affix-wrapper-focused),
  .field :deep(.ant-input:focus) {
    border-color: rgba(240, 180, 10, 0.85) !important;
    background: #ffffff !important;
    box-shadow: 0 0 0 3px rgba(255, 202, 41, 0.2) !important;
  }

  .field :deep(.ant-input-affix-wrapper > input.ant-input) {
    height: 100%;
    padding: 0;
    border: none !important;
    background: transparent !important;
    color: #10243f !important;
    box-shadow: none !important;
  }

  .field :deep(.ant-input::placeholder) {
    color: rgba(48, 72, 110, 0.45);
  }

  .field :deep(.ant-input-affix-wrapper .anticon) {
    color: #7a90b2;
  }

  .field :deep(.ant-input-password-icon) {
    color: #7a90b2 !important;
  }

  /* Chrome 自动填充：
     1) 内层 input 上的 box-shadow: none !important 会把 autofill 常用的白色 inset 阴影覆盖掉，
        这里用更高优先级的选择器重新覆盖，避免露出浏览器默认的淡黄/淡蓝底色；
     2) affix 容器一起改白，避免只有文本区一块白、左右 padding 还是玻璃色。 */
  .field :deep(.ant-input-affix-wrapper:has(input:-webkit-autofill)) {
    background: #ffffff !important;
  }

  .field :deep(.ant-input-affix-wrapper > input.ant-input:-webkit-autofill),
  .field :deep(.ant-input-affix-wrapper > input.ant-input:-webkit-autofill:hover),
  .field :deep(.ant-input-affix-wrapper > input.ant-input:-webkit-autofill:focus) {
    -webkit-text-fill-color: #10243f !important;
    -webkit-box-shadow: 0 0 0 1000px #ffffff inset !important;
    caret-color: #10243f;
    border-radius: 12px;
  }

  .field-icon {
    margin-right: 8px;
    font-size: 15px;
  }

  /* 图形验证码：输入框 + 可点击刷新的图片 */
  .captcha-row {
    display: flex;
    align-items: center;
    gap: 12px;

    :deep(.ant-input-affix-wrapper) {
      flex: 1;
    }
  }

  .captcha-img {
    width: 120px;
    height: 40px;
    flex-shrink: 0;
    border-radius: 8px;
    cursor: pointer;
    object-fit: cover;
    background: rgba(255, 255, 255, 0.92);
  }

  .captcha-refresh {
    padding: 0;
    border: 1px solid var(--line);
    font-size: 12px;
    color: var(--muted);
  }

  /* 记住密码：勾选框 + 过期提示 */
  .remember-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 2px 0 18px;
    font-size: 13px;
    color: var(--muted);
  }

  .remember-tip {
    font-size: 12px;
    color: var(--muted);
    opacity: 0.75;
  }

  .submit-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    width: 100%;
    height: 48px;
    margin-top: 6px;
    border: none;
    border-radius: 12px;
    background: linear-gradient(135deg, #ffe38f 0%, #ffca29 52%, #f2b407 100%);
    box-shadow: 0 14px 30px -12px rgba(255, 202, 41, 0.7);
    color: #13223f;
    font-family: inherit;
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.14em;
    cursor: pointer;
    transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 18px 36px -12px rgba(255, 202, 41, 0.8);
      filter: brightness(1.03);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }

    &:disabled {
      opacity: 0.8;
      cursor: wait;
    }
  }

  .spinner {
    width: 14px;
    height: 14px;
    border: 2px solid rgba(19, 34, 63, 0.25);
    border-top-color: #13223f;
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
  }

  .page-foot {
    position: absolute;
    right: 0;
    bottom: 22px;
    left: 0;
    color: rgba(224, 235, 255, 0.58);
    font-size: 11.5px;
    letter-spacing: 0.03em;
    text-align: center;
  }

  /* 小屏：隐藏品牌介绍，卡片居中 */
  @media (max-width: 767px) {
    padding: 56px 16px 70px;

    .stage {
      gap: 22px;
    }

    .hero {
      gap: 10px;

      .hero-mark {
        width: 38px;
        height: 38px;
        flex: 0 0 38px;
      }

      h1 {
        font-size: 19px;
        letter-spacing: 0.04em;
      }
    }

    .panel {
      padding: 24px 20px 20px;
      border-radius: 22px;
    }

    .page-foot {
      bottom: 14px;
      padding: 0 16px;
      font-size: 11px;
      line-height: 1.6;
    }
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-shell {
    .submit-btn {
      transition: none !important;
    }
  }
}
</style>
