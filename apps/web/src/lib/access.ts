export const ORGANIZATION_ROLES = ["member", "admin", "owner"] as const

export type OrganizationRole = (typeof ORGANIZATION_ROLES)[number]
export type PlatformRole = "platform_admin"

export interface AccessContext {
  organizationRole: OrganizationRole
  platformRole?: PlatformRole
}

export function isOrganizationRole(value: unknown): value is OrganizationRole {
  return typeof value === "string" && ORGANIZATION_ROLES.includes(value as OrganizationRole)
}

export function canManageOrganization(role: OrganizationRole | undefined) {
  return role === "admin" || role === "owner"
}

export function isPlatformAdmin(role: PlatformRole | undefined) {
  return role === "platform_admin"
}
