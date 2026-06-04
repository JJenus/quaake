<script setup lang="ts">
import { computed } from 'vue'

interface Axis { label: string; value: number } // value 0..100
const props = defineProps<{ axes: Axis[]; size?: number }>()
const size = props.size ?? 260
const cx = size / 2, cy = size / 2, rMax = (size / 2) - 34

const points = computed(() => props.axes.map((a, i) => {
  const ang = (Math.PI * 2 * i) / props.axes.length - Math.PI / 2
  const rr = rMax * Math.max(0, Math.min(100, a.value)) / 100
  return { x: cx + rr * Math.cos(ang), y: cy + rr * Math.sin(ang), ang, a }
}))
const polygon = computed(() => points.value.map(p => `${p.x},${p.y}`).join(' '))
const rings = [0.25, 0.5, 0.75, 1]
function axisEnd(i: number) {
  const ang = (Math.PI * 2 * i) / props.axes.length - Math.PI / 2
  return { x: cx + rMax * Math.cos(ang), y: cy + rMax * Math.sin(ang) }
}
function labelPos(i: number) {
  const ang = (Math.PI * 2 * i) / props.axes.length - Math.PI / 2
  return { x: cx + (rMax + 18) * Math.cos(ang), y: cy + (rMax + 18) * Math.sin(ang) }
}
</script>

<template>
  <svg :width="size" :height="size" class="radar">
    <circle v-for="(f, i) in rings" :key="i" :cx="cx" :cy="cy" :r="rMax * f"
            fill="none" stroke="var(--line)" />
    <line v-for="(a, i) in axes" :key="'l'+i" :x1="cx" :y1="cy"
          :x2="axisEnd(i).x" :y2="axisEnd(i).y" stroke="var(--line)" />
    <polygon :points="polygon" fill="rgba(94,130,104,0.22)" stroke="var(--sage)" stroke-width="2" />
    <circle v-for="(p, i) in points" :key="'p'+i" :cx="p.x" :cy="p.y" r="3.5" fill="var(--forest)" />
    <text v-for="(a, i) in axes" :key="'t'+i" :x="labelPos(i).x" :y="labelPos(i).y"
          text-anchor="middle" dominant-baseline="middle" class="axis-label">{{ a.label }}</text>
  </svg>
</template>

<style scoped>
.radar { display: block; margin: 0 auto; }
.axis-label { font-size: .64rem; fill: var(--ink-muted); font-family: var(--font-ui); }
</style>
