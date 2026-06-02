// ============================================================
// Enums — matching backend entity enums exactly
// ============================================================

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export enum MemberSkillType {
  BACKEND_DEVELOPER = 'BACKEND_DEVELOPER',
  BUSINESS_ANALYST = 'BUSINESS_ANALYST',
  DEVOPS_ENGINEER = 'DEVOPS_ENGINEER',
  FLUTTER_DEVELOPER = 'FLUTTER_DEVELOPER',
  FRONTEND_DEVELOPER = 'FRONTEND_DEVELOPER',
  PROJECT_MANAGER = 'PROJECT_MANAGER',
  QA_ENGINEER = 'QA_ENGINEER',
  QUALITY_CONTROL = 'QUALITY_CONTROL',
  TEAM_LEAD = 'TEAM_LEAD',
  TECH_LEAD = 'TECH_LEAD',
  UI_UX_DESIGNER = 'UI_UX_DESIGNER',
  XAMARIN_DEVELOPER = 'XAMARIN_DEVELOPER',
}

export enum ProjectRole {
  BACKEND_DEVELOPER = 'BACKEND_DEVELOPER',
  BUSINESS_ANALYST = 'BUSINESS_ANALYST',
  DEVOPS_ENGINEER = 'DEVOPS_ENGINEER',
  FLUTTER_DEVELOPER = 'FLUTTER_DEVELOPER',
  FRONTEND_DEVELOPER = 'FRONTEND_DEVELOPER',
  PROJECT_MANAGER = 'PROJECT_MANAGER',
  QA_ENGINEER = 'QA_ENGINEER',
  QUALITY_CONTROL = 'QUALITY_CONTROL',
  TEAM_LEAD = 'TEAM_LEAD',
  TECH_LEAD = 'TECH_LEAD',
  UI_UX_DESIGNER = 'UI_UX_DESIGNER',
  XAMARIN_DEVELOPER = 'XAMARIN_DEVELOPER',
}

export enum ProjectStatus {
  PLANNED = 'PLANNED',
  ACTIVE = 'ACTIVE',
  ON_HOLD = 'ON_HOLD',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
}

export enum TaskStatus {
  TODO = 'TODO',
  IN_PROGRESS = 'IN_PROGRESS',
  BLOCKED = 'BLOCKED',
  DONE = 'DONE',
  CANCELLED = 'CANCELLED',
}

export enum RsvpStatus {
  YES = 'YES',
  NO = 'NO',
  MAYBE = 'MAYBE',
}

export enum SeminarStatus {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  DONE = 'DONE',
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

export enum ChatopsChannelPurpose {
  LATE_INPUT = 'LATE_INPUT',
  NOTIFICATION_OUTPUT = 'NOTIFICATION_OUTPUT',
}

export enum LateRecordStatus {
  FIRST_TIME = 'FIRST_TIME',
  UNPAID = 'UNPAID',
  PAID = 'PAID',
  IGNORE = 'IGNORE',
}

export enum SystemLogCategory {
  DATABASE = 'DATABASE',
  BACKEND_LOG = 'BACKEND_LOG',
  HTTP_REQUEST = 'HTTP_REQUEST',
  EXTERNAL_API = 'EXTERNAL_API',
  MESSAGE = 'MESSAGE',
  TASK = 'TASK',
}

export enum SystemLogSeverity {
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

export enum SystemLogStatus {
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED',
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
  sort?: string | string[]
}

export interface SystemLogSearchParams extends Pageable {
  q?: string
  category?: string
  severity?: SystemLogSeverity
  status?: SystemLogStatus
  source?: string
  actor?: string
  correlationId?: string
  from?: string
  to?: string
}

export interface SystemLogListResponse {
  id: number
  occurredAt: string
  category: SystemLogCategory
  severity: SystemLogSeverity
  status: SystemLogStatus
  action: string | null
  source: string | null
  actorUsername: string | null
  correlationId: string | null
  targetType: string | null
  targetId: string | null
  durationMs: number | null
  message: string | null
  exceptionClass: string | null
}

export interface SystemLogDetailResponse extends SystemLogListResponse {
  detailsJson: string | null
  stackTrace: string | null
}

export interface SystemLogSettingsResponse {
  retentionDays: number
  defaultRetentionDays: number
  minRetentionDays: number
  maxRetentionDays: number
}

export interface SystemLogSettingsUpdateRequest {
  retentionDays: number
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

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface AccountResponse {
  id: number
  username: string
  email: string
  fullName: string
  roleName: string
  dob: string | null
  joinDate: string | null
  tenureMonths: number | null
  totalPoints: number
  status: UserStatus
}

export interface AccountProfileUpdateRequest {
  fullName: string
  dob?: string | null
  joinDate?: string | null
}

export interface AccountPasswordChangeRequest {
  currentPassword: string
  newPassword: string
  confirmNewPassword: string
}

export interface GroupRequest {
  name: string
  description?: string
  allGroup: boolean
}

export interface GroupResponse {
  id: number
  name: string
  description: string | null
  allGroup: boolean
  memberCount: number
}

export interface GroupMemberResponse {
  id: number
  username: string
  fullName: string
  email: string
}

// ============================================================
// Project DTOs
// ============================================================

export interface ProjectRequest {
  name: string
  status: ProjectStatus
  startTime: string
  endTime: string
}

export interface ProjectResponse {
  id: number
  name: string
  status: ProjectStatus
  statusLabel: string
  startTime: string
  endTime: string
  memberCount: number
  taskCount: number
}

export interface ProjectAvailabilitySummaryResponse {
  openProjectCount: number
  availableMemberCount: number
  generatedAt: string
}

export interface ProjectMemberRequest {
  userId: number
  projectRole: ProjectRole
  participationStartTime: string
  expectedEndTime: string
}

export interface ProjectMemberResponse {
  projectId: number
  userId: number
  username: string
  fullName: string
  email: string
  projectRole: ProjectRole
  projectRoleLabel: string
  participationStartTime: string
  expectedEndTime: string
}

export interface ProjectTaskRequest {
  name: string
  status: TaskStatus
  assigneeId: number
  startTime: string
  deadline: string
}

export interface ProjectTaskResponse {
  id: number
  projectId: number
  projectName: string
  name: string
  status: TaskStatus
  statusLabel: string
  assigneeId: number
  assigneeUsername: string
  assigneeFullName: string
  startTime: string
  deadline: string
}

export interface RegisterRequest {
  username: string
  email: string
  fullName: string
  password: string
  dob?: string | null
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
  skills?: MemberSkillRequest[]
}

export interface MemberSkillRequest {
  skill: MemberSkillType
  level: number
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
  tenureMonths: number | null
  totalPoints: number
  status: UserStatus
  skills: MemberSkillResponse[]
}

export interface MemberSkillResponse {
  skill: MemberSkillType
  skillLabel: string
  level: number
}

// ============================================================
// Event DTOs
// ============================================================

export interface EventRequest {
  name: string
  eventDate: string
  location?: string
  description?: string
}

export interface EventResponse {
  id: number
  name: string
  eventDate: string
  location: string | null
  description: string | null
  creator: string
  creatorUsername: string | null
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
  scheduledAt: string
  status?: SeminarStatus
}

export interface SeminarResponse {
  id: number
  speakerId: number | null
  speakerName: string | null
  title: string
  description: string | null
  scheduledAt: string | null
  materialsUrl: string | null
  status: SeminarStatus
  currentUserVote: VoteType | null
}

export interface SeminarVoteRequest {
  userId: number
  voteType: VoteType
}

export interface SeminarVoteResponse {
  seminarId: number
  userId: number
  fullName: string
  voteType: VoteType
}

export interface SeminarVoteSummaryResponse {
  upvotes: number
  downvotes: number
}

// ============================================================
// Order DTOs
// ============================================================

export interface RestaurantRequest {
  name: string
  scrapeUrl: string
}

export interface RestaurantResponse {
  id: number
  name: string
  scrapeUrl: string
}

export interface MenuItemResponse {
  id: number
  name: string
  price: number
  description: string | null
  restaurantId: number
}

export interface MenuScrapeResponse {
  restaurantName: string | null
  items: MenuScrapeItemResponse[]
}

export interface OrderSessionRequest {
  name: string
  restaurantId: number
  status?: OrderSessionStatus
  deadline: string
}

export interface OrderSessionResponse {
  id: number
  name: string
  status: OrderSessionStatus
  deadline: string
  restaurantId: number | null
  restaurantName: string | null
  creatorName: string
  creatorUsername: string | null
  canManage: boolean
  createdAt: string | null
}

export interface UserOrderRequest {
  sessionId: number
  userId: number
  itemId: number
  quantity: number
  note?: string
  paid?: boolean
}

export interface UserOrderBulkRequest {
  sessionId: number
  userIds: number[]
  itemId: number
  quantity: number
  note?: string
  paid?: boolean
}

export interface UserOrderUpdateRequest {
  itemId: number
  quantity: number
  note?: string
}

export interface UserOrderResponse {
  id: number
  sessionId: number
  sessionName: string
  sessionStatus: OrderSessionStatus
  userId: number
  fullName: string
  orderedByFullName: string | null
  itemId: number
  itemName: string
  itemPrice: number
  quantity: number
  note: string | null
  paid: boolean
  canManage: boolean
}

export interface OrderItemSummaryResponse {
  itemId: number
  itemName: string
  unitPrice: number
  totalQuantity: number
  totalAmount: number
}

export interface OrderSessionSummaryResponse {
  sessionId: number
  totalOrderLines: number
  totalQuantity: number
  grandTotal: number
  items: OrderItemSummaryResponse[]
}

export interface MenuScrapeRequest {
  url: string
}

export interface MenuScrapeItemResponse {
  name: string
  price: string
  description: string
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

export interface SurveyAssignmentUpdateRequest {
  userIds: number[]
}

export interface SurveyAssignmentStatusResponse {
  userId: number
  fullName: string
  completed: boolean
}

export interface SurveyProgressResponse {
  surveyId: number
  totalAssigned: number
  completedCount: number
  assignments: SurveyAssignmentStatusResponse[]
}

// ============================================================
// Bookmark DTOs
// ============================================================

export interface BookmarkRequest {
  title: string
  url: string
  description?: string
  category?: string
  pinned?: boolean
}

export interface BookmarkResponse {
  id: number
  title: string
  url: string
  description: string | null
  category: string | null
  pinned: boolean
  createdBy: string | null
  updatedAt: string | null
  updatedBy: string | null
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
  status: LateRecordStatus
  fineAmount: number
  payable: boolean
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
  participantCount: number
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
  drawnCount: number
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

export interface LuckyDrawParticipantResponse {
  userId: number
  fullName: string
  email: string
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

export type NotificationChannelType = 'EMAIL' | 'WEBHOOK' | 'CHAT'

export interface NotificationChannelRequest {
  type: NotificationChannelType
  endpoint: string
  enabled: boolean
}

export interface NotificationChannelResponse {
  id: number
  type: NotificationChannelType
  endpoint: string
  enabled: boolean
}

export interface ChatopsChannelConfigUpsertRequest {
  token?: string
  channelUrl: string
}

export interface ChatopsChannelConfigResponse {
  id: number | null
  purpose: ChatopsChannelPurpose
  channelUrl: string | null
  channelId: string | null
  tokenConfigured: boolean
  tokenMasked: string | null
  updatedAt: string | null
}

export type ChatopsLeaveRequestType = 'WFH' | 'OFF'

export interface ChatopsLeaveRequestResponse {
  postId: string | null
  userId: string | null
  requesterName: string
  type: ChatopsLeaveRequestType
  requestedDate: string
  postedAt: string
  message: string
  matchedText: string
}

export interface ChatopsLeaveRequestSummaryResponse {
  date: string
  fetchedAt: string
  chatopsEnabled: boolean
  errorMessage: string | null
  total: number
  wfhCount: number
  offCount: number
  requests: ChatopsLeaveRequestResponse[]
}

// ============================================================
// Notification Schedule DTOs
// ============================================================

export type NotificationScheduleType = 'LATE' | 'EVENT' | 'BIRTHDAY' | 'ANNIVERSARY' | 'LEADERBOARD'

export interface NotificationScheduleResponse {
  id: number
  type: NotificationScheduleType
  sendTime: string
  channelId: string | null
  chatopsPostId: string | null
  enabled: boolean
}
