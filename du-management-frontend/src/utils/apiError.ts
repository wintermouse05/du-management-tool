import type { AxiosError } from 'axios'

interface ProblemFieldError {
  field?: string
  message?: string
}

interface ApiProblemDetail {
  status?: number
  type?: string
  title?: string
  detail?: string
  errorCode?: string
  traceId?: string
  errors?: ProblemFieldError[]
}

const DEFAULT_ERROR_MESSAGE = 'Something went wrong. Please try again.'
const GENERIC_SERVER_ERROR = 'An unexpected error occurred. Please try again later.'
const MAX_USER_MESSAGE_LENGTH = 280
const TECHNICAL_DETAIL_PATTERN =
  /\b(select\b.+\bfrom\b|insert\b.+\binto\b|update\b.+\bset\b|delete\b.+\bfrom\b|stack\s*trace|sql|sqlstate|jdbc|hibernate|preparedstatement|psqlexception|sqlexception|badsqlgrammar|data\s+integrity|duplicate\s+key|foreign\s+key|violates?\s+.*constraint|relation\s+"[^"]+"|column\s+"[^"]+"|table\s+"[^"]+"|could\s+not\s+(execute|extract|prepare)|syntax\s+error\s+at\s+or\s+near|entitymanager|transaction|security\s+context|required\s+role\b.+\bmissing|[a-zA-Z0-9_.$]+Exception|(?:java|javax|jakarta|org|com|net|io)\.[\w.$]+)\b/i

const ERROR_CODE_MESSAGE_MAP: Record<string, string> = {
  INVALID_CREDENTIALS: 'Invalid username or password',
  ACCOUNT_UNAVAILABLE: 'Something went wrong with this account. Please contact an administrator.',
  UNAUTHORIZED: 'Your session has expired. Please sign in again.',
  ACCESS_TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  ACCESS_TOKEN_INVALID: 'Your session is invalid. Please sign in again.',
  REFRESH_TOKEN_MISSING: 'Your session has expired. Please sign in again.',
  REFRESH_TOKEN_INVALID: 'Your session is invalid. Please sign in again.',
  REFRESH_TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  ACCESS_DENIED: 'You do not have permission to perform this action.',
  RESOURCE_NOT_FOUND: 'The requested resource was not found.',
  USER_NOT_FOUND: 'User not found.',
  EVENT_NOT_FOUND: 'Event not found.',
  SURVEY_NOT_FOUND: 'Survey not found.',
  ORDER_SESSION_NOT_FOUND: 'Order session not found.',
  ORDER_NOT_FOUND: 'Order not found.',
  MENU_ITEM_NOT_FOUND: 'Menu item not found.',
  RESTAURANT_NOT_FOUND: 'Restaurant not found.',
  GROUP_NOT_FOUND: 'Group not found.',
  ROLE_NOT_FOUND: 'Role not found.',
  SEMINAR_NOT_FOUND: 'Seminar not found.',
  SEMINAR_MATERIALS_NOT_FOUND: 'Seminar materials were not found.',
  SEMINAR_MATERIALS_UNAVAILABLE: 'Seminar materials are currently unavailable.',
  LUCKY_DRAW_SESSION_NOT_FOUND: 'Lucky draw session not found.',
  LUCKY_DRAW_PRIZE_NOT_FOUND: 'Lucky draw prize not found.',
  BOOKMARK_NOT_FOUND: 'Bookmark not found.',
  NOTIFICATION_TEMPLATE_NOT_FOUND: 'Notification template not found.',
  NOTIFICATION_JOB_NOT_FOUND: 'Notification job not found.',
  NOTIFICATION_CHANNEL_NOT_FOUND: 'Notification channel not found.',
  PAYLOAD_TOO_LARGE: 'The uploaded file exceeds the maximum allowed size.',
  UNSUPPORTED_MEDIA_TYPE: 'Unsupported file or content type.',
  AUTH_USERNAME_EXISTS: 'Username already exists',
  AUTH_EMAIL_EXISTS: 'Email already exists',
  PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED: 'Reset link is invalid or expired. Please request a new one.',
  PASSWORD_RESET_TOKEN_ALREADY_USED: 'This reset link has already been used.',
  SURVEY_DEADLINE_PASSED: 'Survey is already expired and cannot be updated.',
  EVENT_RSVP_CLOSED: 'This event has already happened. RSVP is closed.',
  ORDER_SESSION_NOT_OPEN: 'This order session is closed.',
  ORDER_SESSION_PAST_DEADLINE: 'Session is past deadline. Please choose a new deadline to reopen.',
  ORDER_SESSION_INVALID_DEADLINE: 'Please choose a deadline in the future.',
  ORDER_ITEM_NOT_IN_SESSION: 'Selected item does not belong to this order session.',
  INVALID_USER_IDS: 'One or more selected users are invalid.',
  USER_IDS_NOT_FOUND: 'Some selected users do not exist.',
  LUCKY_DRAW_PRIZE_FULL: 'All slots for this prize have already been assigned.',
  LUCKY_DRAW_USER_ALREADY_WON: 'This user has already won a prize in this session.',
  LUCKY_DRAW_NO_PARTICIPANTS: 'No participants configured for this lucky draw session.',
  LUCKY_DRAW_NO_ELIGIBLE_PARTICIPANTS: 'No eligible participants left for this prize.',
  FILE_REQUIRED: 'Please select a file before continuing.',
  FILE_FORMAT_UNSUPPORTED: 'Unsupported file format. Please use CSV or XLSX.',
  FILE_IMPORT_READ_FAILED: 'Unable to read import file. Please verify the format and try again.',
  CHATOPS_TOKEN_REQUIRED: 'Token is required.',
  CHATOPS_CHANNEL_URL_REQUIRED: 'Channel URL is required.',
  CHATOPS_CHANNEL_URL_INVALID: 'Channel URL format is invalid.',
  CHATOPS_CHANNEL_RESOLVE_FAILED: 'Unable to resolve channel information. Please verify URL and token.',
}

function sanitizeUserMessage(message: unknown, fallback: string): string | null {
  if (typeof message !== 'string') {
    return null
  }

  const normalized = message.replace(/[\r\n\t]+/g, ' ').replace(/\s{2,}/g, ' ').trim()
  if (!normalized) {
    return null
  }
  if (normalized.length > MAX_USER_MESSAGE_LENGTH || TECHNICAL_DETAIL_PATTERN.test(normalized)) {
    return fallback
  }
  return normalized
}

function getFirstValidationError(errors: unknown): string | null {
  if (!Array.isArray(errors)) {
    return null
  }
  for (const item of errors) {
    if (item && typeof item === 'object') {
      const message = (item as ProblemFieldError).message
      const sanitized = sanitizeUserMessage(message, 'Invalid value')
      if (sanitized) {
        return sanitized
      }
    }
  }
  return null
}

function getAxiosLikeMessage(error: unknown): string | null {
  if (!error || typeof error !== 'object') {
    return null
  }
  const message = (error as { message?: unknown }).message
  if (typeof message !== 'string' || !message.trim()) {
    return null
  }
  if (message === 'Network Error') {
    return 'Unable to connect to server. Please check your connection and try again.'
  }
  if (/timeout/i.test(message)) {
    return 'The request timed out. Please try again.'
  }
  if (/^Request failed with status code \d+$/.test(message.trim())) {
    return null
  }
  return sanitizeUserMessage(message, DEFAULT_ERROR_MESSAGE)
}

function getMappedMessageByErrorCode(data: ApiProblemDetail | undefined): string | null {
  if (!data || typeof data !== 'object') {
    return null
  }

  const errorCode = typeof data.errorCode === 'string' ? data.errorCode.trim().toUpperCase() : ''
  if (!errorCode) {
    return null
  }

  if (errorCode === 'INTERNAL_SERVER_ERROR') {
    if (typeof data.traceId === 'string' && data.traceId.trim()) {
      return `${GENERIC_SERVER_ERROR} Reference ID: ${data.traceId.trim()}`
    }
    return GENERIC_SERVER_ERROR
  }

  return ERROR_CODE_MESSAGE_MAP[errorCode] ?? null
}

function getMappedMessageByStatus(status: number | undefined): string | null {
  if (typeof status !== 'number') {
    return null
  }
  if (status >= 500) {
    return GENERIC_SERVER_ERROR
  }
  if (status === 401) {
    return ERROR_CODE_MESSAGE_MAP.UNAUTHORIZED
  }
  if (status === 403) {
    return ERROR_CODE_MESSAGE_MAP.ACCESS_DENIED
  }
  if (status === 404) {
    return ERROR_CODE_MESSAGE_MAP.RESOURCE_NOT_FOUND
  }
  return null
}

export function getApiErrorDetail(error: unknown, fallback = DEFAULT_ERROR_MESSAGE): string {
  const axiosError = error as AxiosError<ApiProblemDetail>
  const data = axiosError?.response?.data
  if (data && typeof data === 'object') {
    const mappedMessage = getMappedMessageByErrorCode(data)
    if (mappedMessage) {
      return mappedMessage
    }

    const firstValidationError = getFirstValidationError(data.errors)
    if (firstValidationError) {
      return firstValidationError
    }

    const safeDetail = sanitizeUserMessage(data.detail, fallback)
    if (safeDetail) {
      return safeDetail
    }
    const safeTitle = sanitizeUserMessage(data.title, fallback)
    if (safeTitle) {
      return safeTitle
    }
  }

  const mappedStatusMessage = getMappedMessageByStatus(axiosError?.response?.status)
  if (mappedStatusMessage) {
    return mappedStatusMessage
  }

  const axiosLikeMessage = getAxiosLikeMessage(error)
  if (axiosLikeMessage) {
    return axiosLikeMessage
  }

  return fallback
}
