<template>
  <div class="about-us grid grid-cols-12 gap-4">
    <div
      class="card p-md col-span-12 lgx:col-span-7 flex justify-between items-stretch drop-shadow-sm"
    >
      <div class="flex flex-col justify-between">
        <div class="">
          <div class="text-subtext font-bold mb-1">当前店铺</div>
          <div class="text-title text-lg font-bold">
            {{ shopInfo?.name || '杰米小店' }}
          </div>
          <div class="mt-xs">
            <a-tag color="blue">自营店铺</a-tag>
          </div>
        </div>
        <router-link to="/goods/list">查看店铺商品 ></router-link>
      </div>
    </div>
    <div
      style="min-height: 232px"
      class="card p-md col-span-12 lgx:col-span-5 text-inverse drop-shadow-sm"
    >
      <div
        class="work-with-us text-text-inverse w-full h-full p-lg rounded-lg flex flex-col justify-between"
      >
        <div class="main">
          <h2 class="text-xl mb-4 font-bold">今日待办</h2>
          <div
            class="todo-item flex items-center justify-between cursor-pointer"
            v-for="item in todoList"
            :key="item.key"
            @click="router.push(item.to)"
          >
            <span>{{ item.label }}</span>
            <span class="font-bold">{{ item.count }} 笔</span>
          </div>
        </div>
        <router-link class="text-text-inverse" to="/orders/order-list"
          >去处理 ></router-link
        >
      </div>
    </div>
  </div>
</template>
<script setup>
  import { computed } from 'vue';
  import { useRouter } from 'vue-router';
  const props = defineProps({
    // { id, name, isTemplate }
    shopInfo: { type: Object, default: null },
    todo: {
      type: Object,
      default: () => ({ pendingShip: 0, afterSale: 0 }),
    },
  });
  const router = useRouter();
  const todoList = computed(() => [
    {
      key: 'ship',
      label: '待发货订单',
      count: props.todo?.pendingShip ?? 0,
      to: '/delivery',
    },
    {
      key: 'afterSale',
      label: '待处理售后',
      count: props.todo?.afterSale ?? 0,
      to: '/after-sales/after-sale-list',
    },
  ]);
</script>
<style lang="less" scoped>
  .work-with-us {
    background: linear-gradient(35deg, #844efd, #2533b2, #09b3ef);

    .todo-item {
      @apply py-1 border-b border-white border-opacity-20;
      &:last-child {
        @apply border-b-0;
      }
    }
  }
</style>
