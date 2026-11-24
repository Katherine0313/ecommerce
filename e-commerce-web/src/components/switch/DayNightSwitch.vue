<script setup>
  import { watch } from 'vue';
  import useModelValue from '@/utils/useModelValue';
  import { configTheme, getCachedTheme } from '@/theme';
  // import { useSettingStore } from '@/store/setting';
  // import { storeToRefs } from 'pinia';
  const props = defineProps({
    value: { type: String },
  });
  const emit = defineEmits();
  const { value: _value } = useModelValue(
    () => props.value,
    (val) => emit('update:value', val),
    'day'
  );
  const valeToggle = {
    day: 'night',
    night: 'day',
  };
  function toggle() {
    _value.value = valeToggle[_value.value];
  }
  // const settingStore = useSettingStore();
  // const { theme } = storeToRefs(settingStore);
  // const { setTheme } = settingStore;
  // let cachedTheme = theme.value;
  // watch(theme, (val) => {
  //   if (val !== 'night') {
  //     cachedTheme = val;
  //     _value.value = 'day';
  //   }
  // });
  // 切到 night 用夜间配色，切回 day 时恢复上一次选中的主题
  watch(_value, (val) => {
    configTheme(val === 'night' ? 'night' : getCachedTheme());
  });
</script>
<template>
  <div
    @click="toggle"
    class="bg-fill-2 day-night-switch hover:border-border relative border-border-2 text-lg rounded-full border border-solid flex items-center"
  >
    <div
      :class="`spot transition-[left] duration-300 h-full absolute rounded-full bg-container ${_value}`"
    ></div>
    <IconFont
      :class="`day-night-switch-item ${_value === 'day' ? 'checked' : ''}`"
      name="icon-sun"
    />
    <IconFont
      :class="`day-night-switch-item ${_value === 'night' ? 'checked' : ''}`"
      name="icon-moono"
    />
  </div>
</template>
<style scoped lang="less">
  .day-night-switch {
    .spot {
      width: calc(50% - 1px);
      z-index: 1;
      left: 0;
      &.night {
        left: calc(50% + 1px);
        @apply bg-layout;
      }
    }

    &-item {
      @apply z-20 bg-transparent p-xxs rounded-full text-disabled ~"last:ml-[2px]";
      &.checked {
        @apply text-text;
      }
    }
  }
</style>
