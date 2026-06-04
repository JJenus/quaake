<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { usePriorities } from '~/stores/priorities'
import type { PropertyDetail, FitResponse } from '~/shared/types/api'

const route = useRoute()
const id = route.params.id as string
const api = useApi()
const fitScore = useFitScore()
const priorities = usePriorities()

// Universal property view (GET /properties/{id})
const { data: property, error } = await useAsyncData(`property:${id}`,
  () => api.get<PropertyDetail>(`/api/v1/properties/${id}`))

// Personalized fit (POST /properties/{id}/fit) — after mount so it can react to priorities
const fit = ref<FitResponse | null>(null)
const fitError = ref<string | null>(null)
async function loadFit() {
  try { fit.value = await fitScore(id, priorities.scoringContext) }
  catch (e: any) { fitError.value = e?.data?.detail ?? 'Could not compute Fit Score' }
}
onMounted(loadFit)

const axes = computed(() => (fit.value?.breakdown ?? []).map(c => ({
  label: c.dimension.replace('_', ' '), value: c.subScore,
})))
const money = (m?: { amount: number; currency: string }) =>
  m ? new Intl.NumberFormat('en-NG', { style: 'currency', currency: m.currency, maximumFractionDigits: 0 }).format(m.amount) : ''
</script>

<template>
  <main class="wrap">
    <p v-if="error" class="err">Property not found.</p>

    <template v-else-if="property">
      <header class="head">
        <div>
          <h1>{{ property.title }}</h1>
          <p class="area">{{ property.areaLabel }}</p>
          <p class="price">{{ money(property.price) }}</p>
        </div>
        <FitScore v-if="fit" :score="fit.score" :confidence="fit.confidence" />
        <p v-else-if="fitError" class="err small">{{ fitError }}</p>
        <p v-else class="muted small">Scoring…</p>
      </header>

      <section v-if="axes.length" class="card">
        <h2>How it fits your priorities</h2>
        <RadarChart :axes="axes" />
      </section>

      <section v-for="layer in property.layers" :key="layer.layer" class="card">
        <div class="layer-head">
          <h2>{{ layer.label }}</h2>
          <span v-if="layer.layerScore != null" class="chip">{{ layer.layerScore }}</span>
        </div>
        <ul>
          <li v-for="item in layer.items" :key="item.key">
            <span>{{ item.label }}</span>
            <span class="val">
              <template v-if="item.travelMinutes != null">{{ Math.round(item.travelMinutes) }} min {{ item.travelMode }}</template>
              <template v-else-if="item.subScore != null">{{ item.subScore }}</template>
              <template v-else>—</template>
            </span>
          </li>
        </ul>
      </section>
    </template>
  </main>
</template>

<style scoped>
.wrap { max-width: 720px; margin: 0 auto; padding: 28px 20px 64px; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 22px; }
h1 { font-family: var(--font-display); font-size: 1.9rem; color: var(--forest); font-weight: 600; }
.area { color: var(--ink-muted); margin-top: 2px; }
.price { color: var(--clay); font-weight: 600; margin-top: 8px; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg);
        padding: 20px 22px; margin-bottom: 16px; }
.layer-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
h2 { font-family: var(--font-display); font-size: 1.1rem; color: var(--ink); font-weight: 600; }
.chip { background: var(--sage); color: #fff; border-radius: 999px; padding: 2px 12px; font-size: .85rem; }
ul { list-style: none; }
li { display: flex; justify-content: space-between; padding: 9px 0; border-bottom: 1px solid var(--line); font-size: .95rem; }
li:last-child { border-bottom: none; }
.val { color: var(--ink-muted); }
.muted { color: var(--ink-faint); } .small { font-size: .85rem; } .err { color: #B4532F; }
</style>
