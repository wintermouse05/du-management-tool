import { UserStatus, type MemberResponse } from '@/types'

export type MemberDisplayOption = MemberResponse & {
  displayName: string
  disabled: boolean
  inactive: boolean
}

const inactiveSuffix = ' (inactive)'

export function formatMemberName(member: Pick<MemberResponse, 'fullName' | 'status'> | null | undefined, fallback = 'Unknown member') {
  const name = member?.fullName?.trim() || fallback
  if (member?.status === UserStatus.INACTIVE && !name.endsWith(inactiveSuffix)) {
    return `${name}${inactiveSuffix}`
  }
  return name
}

export function toMemberDisplayOption(member: MemberResponse): MemberDisplayOption {
  const inactive = member.status === UserStatus.INACTIVE
  return {
    ...member,
    displayName: formatMemberName(member),
    disabled: inactive,
    inactive,
  }
}

export function findMemberDisplayName(
  members: Array<MemberDisplayOption | MemberResponse>,
  memberId: number | null | undefined,
  fallback = 'Selected member',
) {
  if (!memberId) {
    return fallback
  }
  const member = members.find(item => item.id === memberId)
  if (!member) {
    return fallback
  }
  return 'displayName' in member ? member.displayName : formatMemberName(member)
}
