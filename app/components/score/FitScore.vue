<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{ score: number; confidence?: number; size?: number }>()
const display = ref(0)
const size = props.size ?? 168
const stroke = 12
const r = (size - stroke) / 2
const circ = 2 * Math.PI * r

function colorFor(s: number) {
  if (s >= 80) return 'var(--sage)'
  if (s >= 60) return 'var(--sage-soft)'
  if (s >= 40) return 'var(--clay)'
  return '#C0705A'
}

function animate(to: number) {
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce) { display.value = to; return }
  const start = performance.now(); const from = display.value; const dur = 900
  const step = (t: number) => {
    const p = Math.min(1, (t - start) / dur)
    const eased = 1 - Math.pow(1 - p, 3)
    display.value = Math.round(from + (to - from) * eased)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}
onMounted(() => animate(props.score))
watch(() => props.score, v => animate(v))
</script>

<template>
  <div class="fit" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :width="size" :height="size">
      <circle :cx="size/2" :cy="size/2" :r="r" :stroke-width="stroke" fill="none" stroke="var(--line)" />
      <circle
        :cx="size/2" :cy="size/2" :r="r" :stroke-width="stroke" fill="none"
        :stroke="colorFor(display)" stroke-linecap="round"
        :stroke-dasharray="circ"
        :stroke-dashoffset="circ * (1 - display / 100)"
        :transform="`rotate(-90 ${size/2} ${size/2})`"
        style="transition: stroke-dashoffset .1s linear" />
    </svg>
    <div class="label">
      <span class="num">{{ display }}</span>
      <span class="cap">Fit Score</span>
      <span v-if="confidence != null" class="conf">{{ Math.round(confidence * 100) }}% confidence</span>
    </div>
  </div>
</template>

<style scoped>
.fit { position: relative; display: grid; place-items: center; }
.fit svg { position: absolute; inset: 0; }
.label { text-align: center; }
.num { font-family: var(--font-display); font-size: 2.6rem; font-weight: 600; color: var(--forest); display: block; }
.cap { font-size: .72rem; letter-spacing: .12em; text-transform: uppercase; color: var(--ink-muted); }
.conf { display: block; font-size: .68rem; color: var(--ink-faint); margin-top: 2px; }
</style>
