<template>
  <div v-if="!loading">
    <div>404 Not Found</div>
    <div>path: {{ $route.path }}</div>
  </div>
  <div v-else class="loading flex items-center justify-center">
    <ReadingLoader />
  </div>
</template>
<script setup>
  import ReadingLoader from '@/components/loaders/ReadingLoader.vue';
  const props = defineProps({
    loading: Boolean,
  });
</script>
<style scoped>
  .loading {
    min-height: calc(100vh - theme(height.header) - 182px);
  }
</style>
