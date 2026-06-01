<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { accountApi } from '@/api/account'
import { getApiErrorDetail } from '@/utils/apiError'
import type { AccountResponse } from '@/types'
import { UserStatus } from '@/types'
import { formatLocalDate, parseApiDate, toLocalDate } from '@/utils/datetime'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const auth = useAuthStore()
const PASSWORD_POLICY_MESSAGE = 'Password must be 8-128 characters and include uppercase, lowercase, number, and special character.'
const PASSWORD_POLICY_REGEX = /^(?=\S{8,128}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~]).*$/

const account = ref<AccountResponse | null>(null)
const loading = ref(false)
const savingProfile = ref(false)
const changingPassword = ref(false)
const fullName = ref('')
const dob = ref<Date | null>(null)
const joinDate = ref<Date | null>(null)
const profileError = ref('')
const passwordError = ref('')
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmNewPassword: '',
})

const statusSeverity = computed(() => (
  account.value?.status === UserStatus.ACTIVE ? 'success' : 'danger'
))

async function loadAccount() {
  loading.value = true
  try {
    const response = await accountApi.getAccount()
    account.value = response.data
    fullName.value = response.data.fullName
    auth.setFullName(response.data.fullName)
    dob.value = parseApiDate(response.data.dob)
    joinDate.value = parseApiDate(response.data.joinDate)
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Load failed', detail: getApiErrorDetail(error, 'Unable to load account'), life: 4000 })
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  profileError.value = ''
  const trimmedName = fullName.value.trim()

  if (!trimmedName) {
    profileError.value = 'Full name is required.'
    return
  }
  if (trimmedName.length > 255) {
    profileError.value = 'Full name must be at most 255 characters.'
    return
  }

  savingProfile.value = true
  try {
    const response = await accountApi.updateProfile({
      fullName: trimmedName,
      dob: dob.value ? toLocalDate(dob.value) : null,
      joinDate: joinDate.value ? toLocalDate(joinDate.value) : null,
    })
    account.value = response.data
    fullName.value = response.data.fullName
    auth.setFullName(response.data.fullName)
    dob.value = parseApiDate(response.data.dob)
    joinDate.value = parseApiDate(response.data.joinDate)
    toast.add({ severity: 'success', summary: 'Profile updated', life: 2500 })
  } catch (error: any) {
    profileError.value = getApiErrorDetail(error, 'Unable to update profile')
  } finally {
    savingProfile.value = false
  }
}

function validatePasswordForm() {
  const { currentPassword, newPassword, confirmNewPassword } = passwordForm.value

  if (!currentPassword || !newPassword || !confirmNewPassword) {
    return 'All password fields are required.'
  }
  if (!PASSWORD_POLICY_REGEX.test(newPassword)) {
    return PASSWORD_POLICY_MESSAGE
  }
  if (newPassword === currentPassword) {
    return 'New password must be different from the current password.'
  }
  if (newPassword !== confirmNewPassword) {
    return 'New password and confirmation do not match.'
  }

  return ''
}

async function changePassword() {
  passwordError.value = validatePasswordForm()
  if (passwordError.value) {
    return
  }

  changingPassword.value = true
  try {
    await accountApi.changePassword(passwordForm.value)
    passwordForm.value = {
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: '',
    }
    toast.add({ severity: 'success', summary: 'Password changed', life: 2500 })
  } catch (error: any) {
    passwordError.value = getApiErrorDetail(error, 'Unable to change password')
  } finally {
    changingPassword.value = false
  }
}

onMounted(loadAccount)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Account</h2>
        <p class="page-subtitle">Your profile and sign-in details</p>
      </div>
    </div>

    <div class="account-grid">
      <section class="content-card account-details">
        <h3>Information</h3>
        <div v-if="loading" class="account-muted">Loading account...</div>
        <dl v-else-if="account" class="account-info-list">
          <div>
            <dt>Username</dt>
            <dd>{{ account.username }}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{{ account.email }}</dd>
          </div>
          <div>
            <dt>Role</dt>
            <dd><Tag :value="account.roleName" severity="info" /></dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd><Tag :value="account.status" :severity="statusSeverity" /></dd>
          </div>
          <div>
            <dt>Date of Birth</dt>
            <dd>{{ formatLocalDate(account.dob) || '-' }}</dd>
          </div>
          <div>
            <dt>Join Date</dt>
            <dd>{{ formatLocalDate(account.joinDate) || '-' }}</dd>
          </div>
          <div>
            <dt>Tenure</dt>
            <dd>{{ account.tenureMonths == null ? '-' : `${account.tenureMonths} months` }}</dd>
          </div>
          <div>
            <dt>Points</dt>
            <dd>{{ account.totalPoints }}</dd>
          </div>
        </dl>
      </section>

      <section class="content-card account-form-card">
        <h3>Profile</h3>
        <form class="account-form" @submit.prevent="saveProfile">
          <div class="form-field">
            <label for="account-full-name" class="required">Full Name</label>
            <InputText
              id="account-full-name"
              v-model="fullName"
              :disabled="loading"
              :class="{ 'p-invalid': profileError }"
              autocomplete="name"
              fluid
            />
          </div>
          <div class="account-date-fields">
            <div class="form-field">
              <label for="account-dob">Date of Birth</label>
              <DatePicker
                id="account-dob"
                v-model="dob"
                :disabled="loading"
                placeholder="Select date"
                showIcon
                fluid
              />
            </div>
            <div class="form-field">
              <label for="account-join-date">Join Date</label>
              <DatePicker
                id="account-join-date"
                v-model="joinDate"
                :disabled="loading"
                placeholder="Select date"
                showIcon
                fluid
              />
            </div>
          </div>
          <div v-if="profileError" class="form-error">
            <i class="pi pi-exclamation-circle"></i>
            {{ profileError }}
          </div>
          <Button type="submit" label="Save Profile" icon="pi pi-save" :loading="savingProfile" />
        </form>
      </section>

      <section class="content-card account-form-card">
        <h3>Password</h3>
        <form class="account-form" @submit.prevent="changePassword">
          <div class="form-field">
            <label for="account-current-password" class="required">Current Password</label>
            <Password
              id="account-current-password"
              v-model="passwordForm.currentPassword"
              :feedback="false"
              :class="{ 'p-invalid': passwordError }"
              toggleMask
              autocomplete="current-password"
              fluid
            />
          </div>
          <div class="form-field">
            <label for="account-new-password" class="required">New Password</label>
            <Password
              id="account-new-password"
              v-model="passwordForm.newPassword"
              :class="{ 'p-invalid': passwordError }"
              toggleMask
              autocomplete="new-password"
              fluid
            />
          </div>
          <div class="form-field">
            <label for="account-confirm-password" class="required">Confirm New Password</label>
            <Password
              id="account-confirm-password"
              v-model="passwordForm.confirmNewPassword"
              :feedback="false"
              :class="{ 'p-invalid': passwordError }"
              toggleMask
              autocomplete="new-password"
              fluid
            />
          </div>
          <div v-if="passwordError" class="form-error">
            <i class="pi pi-exclamation-circle"></i>
            {{ passwordError }}
          </div>
          <Button type="submit" label="Change Password" icon="pi pi-lock" :loading="changingPassword" severity="secondary" />
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.account-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(320px, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.account-details {
  grid-row: span 2;
}

.account-details h3,
.account-form-card h3 {
  margin-bottom: var(--space-4);
  font-size: 20px;
}

.account-info-list {
  display: grid;
  gap: var(--space-4);
}

.account-info-list div {
  display: grid;
  gap: 4px;
}

.account-info-list dt {
  color: var(--theme-text-weak);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.account-info-list dd {
  color: var(--theme-text-primary);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.account-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  align-items: flex-start;
}

.account-form .form-field {
  width: 100%;
  margin: 0;
}

.account-date-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  width: 100%;
}

.form-error {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--theme-danger);
  font-size: 13px;
}

.account-muted {
  color: var(--theme-text-weak);
  font-size: 14px;
}

@media (max-width: 900px) {
  .account-grid {
    grid-template-columns: 1fr;
  }

  .account-details {
    grid-row: auto;
  }

  .account-date-fields {
    grid-template-columns: 1fr;
  }
}
</style>
