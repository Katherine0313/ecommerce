<script setup></script>
<template>
  <div class="page-footer">
    <div class="copyright">
      Copyright
      <CopyrightOutlined class="icon-copyright" /> 2026 杰米多多
    </div>
    <!-- 工信部 ICP 备案号，管局要求可点击跳转到备案查询官网 -->
    <div class="beian">
      <a
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
      >
        粤ICP备2026115644号
      </a>
    </div>
  </div>
</template>

<style scoped lang="less">
  .page-footer {
    text-align: center;
    @apply text-gray-400;

    .copyright {
      margin-top: 8px;

      .icon-copyright {
        margin: 0;
      }
    }

    .beian {
      margin-top: 4px;
    }
  }
</style>
