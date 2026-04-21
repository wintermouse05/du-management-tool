<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = 'Please enter both username and password'
    return
  }
  loading.value = true
  try {
    await auth.login({ username: username.value, password: password.value })
    toast.add({ severity: 'success', summary: 'Welcome back!', detail: `Logged in as ${auth.username}`, life: 3000 })
    router.push('/')
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Invalid credentials. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-card fade-in">
        <div class="auth-header">
          <div class="auth-logo">
            <div class="auth-logo-icon">
              <i class="pi pi-th-large"></i>
            </div>
            <h1>DU Manager</h1>
          </div>
          <p class="auth-subtitle">Sign in to your account</p>
        </div>

        <form @submit.prevent="handleLogin" class="auth-form">
          <div class="form-field">
            <label for="login-username">Username</label>
            <InputText
              id="login-username"
              v-model="username"
              placeholder="Enter your username"
              :class="{ 'p-invalid': error }"
              autocomplete="username"
              fluid
            />
          </div>

          <div class="form-field">
            <label for="login-password">Password</label>
            <Password
              id="login-password"
              v-model="password"
              placeholder="Enter your password"
              :feedback="false"
              :class="{ 'p-invalid': error }"
              toggleMask
              autocomplete="current-password"
              fluid
            />
          </div>

          <div v-if="error" class="auth-error">
            <i class="pi pi-exclamation-circle"></i>
            {{ error }}
          </div>

          <Button
            type="submit"
            label="Sign In"
            icon="pi pi-sign-in"
            :loading="loading"
            class="auth-submit"
            fluid
          />
        </form>

        <div class="auth-footer">
          <span>Don't have an account?</span>
          <router-link to="/register" class="auth-link">Create account</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--theme-surface-light);
  padding: var(--space-6);
}

.auth-container {
  width: 100%;
  max-width: 420px;
}

.auth-card {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: var(--radius-section);
  box-shadow: var(--theme-shadow-card);
  padding: 40px;
}

.auth-header {
  text-align: center;
  margin-bottom: var(--space-8);
}

.auth-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-3);
}

.auth-logo-icon {
  width: 56px;
  height: 56px;
  background: var(--theme-blue);
  border-radius: var(--radius-btn);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  box-shadow: var(--theme-shadow-card);
}

.auth-logo h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--theme-text-primary);
  letter-spacing: -0.5px;
}

.auth-subtitle {
  color: var(--theme-text-weak);
  font-size: 15px;
  margin-top: var(--space-2);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.auth-error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 12px 16px;
  background: var(--theme-danger-bg);
  color: var(--theme-danger);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
}

.auth-submit {
  margin-top: var(--space-2);
  padding: 12px 24px !important;
  font-size: 15px !important;
}

.auth-footer {
  margin-top: var(--space-6);
  text-align: center;
  font-size: 14px;
  color: var(--theme-text-weak);
  display: flex;
  gap: var(--space-2);
  justify-content: center;
}

.auth-link {
  color: var(--theme-blue);
  font-weight: 600;
}

.auth-link:hover {
  text-decoration: underline;
}

@media (max-width: 480px) {
  .auth-page { padding: var(--space-4); }
  .auth-card { padding: 24px; border-radius: var(--radius-card); }
  .auth-logo h1 { font-size: 24px; }
  .auth-logo-icon { width: 48px; height: 48px; font-size: 20px; }
  .auth-header { margin-bottom: var(--space-6); }
}
</style>
