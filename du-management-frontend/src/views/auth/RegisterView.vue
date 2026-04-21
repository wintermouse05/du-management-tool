<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const form = ref({ username: '', email: '', fullName: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  error.value = ''
  if (!form.value.username || !form.value.email || !form.value.fullName || !form.value.password) {
    error.value = 'Please fill in all required fields'; return
  }
  loading.value = true
  try {
    await auth.register(form.value)
    toast.add({ severity: 'success', summary: 'Account created!', life: 3000 })
    router.push('/')
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Registration failed.'
  } finally { loading.value = false }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-card fade-in">
        <div class="auth-header">
          <div class="auth-logo">
            <div class="auth-logo-icon"><i class="pi pi-th-large"></i></div>
            <h1>DU Manager</h1>
          </div>
          <p class="auth-subtitle">Create your account</p>
        </div>
        <form @submit.prevent="handleRegister" class="auth-form">
          <div class="form-field">
            <label for="reg-username">Username *</label>
            <InputText id="reg-username" v-model="form.username" placeholder="Choose a username" fluid />
          </div>
          <div class="form-field">
            <label for="reg-email">Email *</label>
            <InputText id="reg-email" v-model="form.email" type="email" placeholder="your@email.com" fluid />
          </div>
          <div class="form-field">
            <label for="reg-fullname">Full Name</label>
            <InputText id="reg-fullname" v-model="form.fullName" placeholder="Your full name" fluid />
          </div>
          <div class="form-field">
            <label for="reg-password">Password *</label>
            <Password id="reg-password" v-model="form.password" placeholder="Min 8 characters" toggleMask fluid />
          </div>
          <div v-if="error" class="auth-error"><i class="pi pi-exclamation-circle"></i> {{ error }}</div>
          <Button type="submit" label="Create Account" icon="pi pi-user-plus" :loading="loading" fluid />
        </form>
        <div class="auth-footer">
          <span>Already have an account?</span>
          <router-link to="/login" class="auth-link">Sign in</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--theme-surface-light); padding: var(--space-6); }
.auth-container { width: 100%; max-width: 420px; }
.auth-card { background: var(--theme-surface); border: 1px solid var(--theme-border); border-radius: var(--radius-section); box-shadow: var(--theme-shadow-card); padding: 40px; }
.auth-header { text-align: center; margin-bottom: var(--space-8); }
.auth-logo { display: flex; flex-direction: column; align-items: center; gap: var(--space-4); margin-bottom: var(--space-3); }
.auth-logo-icon { width: 56px; height: 56px; background: var(--theme-blue); border-radius: var(--radius-btn); display: flex; align-items: center; justify-content: center; color: white; font-size: 24px; }
.auth-logo h1 { font-size: 28px; font-weight: 700; color: var(--theme-text-primary); }
.auth-subtitle { color: var(--theme-text-weak); font-size: 15px; margin-top: var(--space-2); }
.auth-form { display: flex; flex-direction: column; gap: var(--space-5); }
.auth-error { display: flex; align-items: center; gap: var(--space-2); padding: 12px 16px; background: var(--theme-danger-bg); color: var(--theme-danger); border-radius: var(--radius-md); font-size: 13px; }
.auth-footer { margin-top: var(--space-6); text-align: center; font-size: 14px; color: var(--theme-text-weak); display: flex; gap: var(--space-2); justify-content: center; }
.auth-link { color: var(--theme-blue); font-weight: 600; }
</style>
