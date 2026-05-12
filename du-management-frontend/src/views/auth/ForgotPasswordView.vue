<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'

const router = useRouter()

const email = ref('')
const loading = ref(false)
const error = ref('')
const submitted = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!email.value) {
    error.value = 'Please enter your email address'
    return
  }
  loading.value = true
  try {
    await authApi.forgotPassword({ email: email.value })
    submitted.value = true
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Something went wrong. Please try again.'
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
              <i class="pi pi-lock"></i>
            </div>
            <h1>Reset Password</h1>
          </div>
          <p v-if="!submitted" class="auth-subtitle">Enter your email to receive a reset link</p>
        </div>

        <form v-if="!submitted" @submit.prevent="handleSubmit" class="auth-form">
          <div class="form-field">
            <label for="forgot-email">Email</label>
            <InputText
              id="forgot-email"
              v-model="email"
              type="email"
              placeholder="Enter your email address"
              :class="{ 'p-invalid': error }"
              autocomplete="email"
              fluid
            />
          </div>

          <div v-if="error" class="auth-error">
            <i class="pi pi-exclamation-circle"></i>
            {{ error }}
          </div>

          <Button
            type="submit"
            label="Send Reset Link"
            icon="pi pi-envelope"
            :loading="loading"
            class="auth-submit"
            fluid
          />
        </form>

        <div v-else class="auth-success">
          <div class="success-icon">
            <i class="pi pi-check-circle"></i>
          </div>
          <p class="success-title">Check your email</p>
          <p class="success-text">
            If an account with that email exists, we've sent a password reset link.
            Please check your inbox and spam folder.
          </p>
          <Button
            label="Back to Login"
            icon="pi pi-arrow-left"
            class="p-button-outlined"
            @click="router.push('/login')"
            fluid
          />
        </div>

        <div class="auth-footer">
          <router-link to="/login" class="auth-link">Back to sign in</router-link>
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

.auth-success {
  text-align: center;
  padding: var(--space-4) 0;
}

.success-icon {
  font-size: 48px;
  color: var(--theme-green, #22c55e);
  margin-bottom: var(--space-4);
}

.success-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: var(--space-3);
}

.success-text {
  color: var(--theme-text-weak);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: var(--space-6);
}

.auth-footer {
  margin-top: var(--space-6);
  text-align: center;
  font-size: 14px;
  color: var(--theme-text-weak);
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
