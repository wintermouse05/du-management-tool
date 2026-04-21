// ============================================================
// Enums — matching backend entity enums exactly
// ============================================================

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export enum RsvpStatus {
  YES = 'YES',
  NO = 'NO',
  MAYBE = 'MAYBE',
}

export enum SeminarStatus {
  PROPOSED = 'PROPOSED',
  APPROVED = 'APPROVED',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
}

export enum OrderSessionStatus {
  OPEN = 'OPEN',
  CLOSED = 'CLOSED',
  CANCELLED = 'CANCELLED',
}

export enum VoteType {
  UPVOTE = 'UPVOTE',
  DOWNVOTE = 'DOWNVOTE',
}

// ============================================================
// Spring Page wrapper
// ============================================================

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  numberOfElements: number
  empty: boolean
}

export interface Pageable {
  page?: number
  size?: number
  sort?: string
}

// ============================================================
// Auth DTOs
// ============================================================

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  username: string
  role: string
  userId: number
}

export interface RegisterRequest {
  username: string
  email: string
  fullName: string
  password: string
}

// ============================================================
// Member DTOs
// ============================================================

export interface MemberRequest {
  roleId: number
  username: string
  email: string
  password?: string
  fullName: string
  dob?: string | null
  joinDate?: string | null
  status?: UserStatus
}

export interface MemberResponse {
  id: number
  roleId: number
  roleName: string
  username: string
  email: string
  fullName: string
  dob: string | null
  joinDate: string | null
  totalPoints: number
  status: UserStatus
}

// ============================================================
// Event DTOs
// ============================================================

export interface EventRequest {
  name: string
  eventDate: string
  location?: string
}

export interface EventResponse {
  id: number
  name: string
  eventDate: string
  location: string | null
}

export interface EventAttendanceRequest {
  userId: number
  rsvpStatus?: RsvpStatus
}

export interface EventAttendeeResponse {
  eventId: number
  userId: number
  fullName: string
  rsvpStatus: RsvpStatus
  checkedIn: boolean
}

// ============================================================
// Seminar DTOs
// ============================================================

export interface SeminarRequest {
  speakerId?: number | null
  title: string
  description?: string
  scheduledAt?: string | null
  status?: SeminarStatus
}

export interface SeminarResponse {
  id: number
  speakerId: number | null
  speakerName: string | null
  title: string
  description: string | null
  scheduledAt: string | null
  status: SeminarStatus
}

export interface SeminarVoteRequest {
  userId: number
  voteType: VoteType
}

export interface SeminarVoteResponse {
  seminarId: number
  userId: number
  voteType: VoteType
}

// ============================================================
// Order DTOs
// ============================================================

export interface MenuItemRequest {
  name: string
  price: number
}

export interface MenuItemResponse {
  id: number
  name: string
  price: number
}

export interface OrderSessionRequest {
  status?: OrderSessionStatus
  deadline: string
}

export interface OrderSessionResponse {
  id: number
  status: OrderSessionStatus
  deadline: string
}

export interface UserOrderRequest {
  sessionId: number
  userId: number
  itemId: number
  quantity: number
  note?: string
  paid?: boolean
}

export interface UserOrderResponse {
  id: number
  sessionId: number
  userId: number
  fullName: string
  itemId: number
  itemName: string
  quantity: number
  note: string | null
  paid: boolean
}

// ============================================================
// Survey DTOs
// ============================================================

export interface SurveyRequest {
  title: string
  link: string
  deadline: string
}

export interface SurveyResponse {
  id: number
  title: string
  link: string
  deadline: string
}

export interface SurveyCompletionRequest {
  userId: number
  completed: boolean
}

export interface SurveyProgressResponse {
  surveyId: number
  totalAssigned: number
  completedCount: number
}

// ============================================================
// Gamification DTOs
// ============================================================

export interface PointRuleRequest {
  actionCode: string
  pointValue: number
}

export interface PointRuleResponse {
  id: number
  actionCode: string
  pointValue: number
}

export interface ManualPointRequest {
  userId: number
  ruleId?: number | null
  pointsChanged?: number | null
  reason?: string
}

export interface PointHistoryResponse {
  id: number
  userId: number
  fullName: string
  ruleId: number | null
  actionCode: string | null
  pointsChanged: number
  reason: string
  createdAt: string
}

export interface LeaderboardEntryResponse {
  userId: number
  fullName: string
  totalPoints: number
}

// ============================================================
// Late Record DTOs
// ============================================================

export interface LateRecordRequest {
  userId: number
  recordDate: string
  minutesLate: number
  reason?: string
}

export interface LateRecordResponse {
  id: number
  userId: number
  fullName: string
  recordDate: string
  minutesLate: number
  reason: string | null
}

export interface LateSummaryResponse {
  userId: number
  fullName: string
  totalLateTimes: number
  totalMinutesLate: number
}

// ============================================================
// Lucky Draw DTOs
// ============================================================

export interface LuckyDrawSessionRequest {
  eventId: number
  name: string
}

export interface LuckyDrawSessionResponse {
  id: number
  eventId: number
  eventName: string
  name: string
}

export interface LuckyDrawPrizeRequest {
  sessionId: number
  prizeName: string
  quantity: number
}

export interface LuckyDrawPrizeResponse {
  id: number
  sessionId: number
  sessionName: string
  prizeName: string
  quantity: number
}

export interface LuckyDrawWinnerRequest {
  prizeId: number
  userId: number
}

export interface LuckyDrawWinnerResponse {
  id: number
  prizeId: number
  prizeName: string
  userId: number
  fullName: string
}

// ============================================================
// Role DTOs
// ============================================================

export interface RoleRequest {
  name: string
  description?: string
}

export interface RoleResponse {
  id: number
  name: string
  description: string | null
}

// ============================================================
// Notification DTOs
// ============================================================

export interface NotificationJobResponse {
  code: string
  schedule: string
  description: string
  enabled: boolean
  lastRunAt: string | null
}

export interface NotificationJobToggleRequest {
  enabled: boolean
}

export interface NotificationTemplateRequest {
  code: string
  name: string
  subjectTemplate: string
  bodyTemplate: string
  enabled: boolean
}

export interface NotificationTemplateResponse {
  id: number
  code: string
  name: string
  subjectTemplate: string
  bodyTemplate: string
  enabled: boolean
  updatedAt: string | null
}

export interface NotificationInboxResponse {
  id: number
  title: string
  message: string
  type: string
  read: boolean
  actionUrl: string | null
  createdAt: string
  readAt: string | null
}

export interface NotificationUnreadCountResponse {
  unreadCount: number
}
