import { describe, expect, it } from "vitest"

import { canManageOrganization, isOrganizationRole, isPlatformAdmin } from "./access"

describe("access policy", () => {
  it("keeps member work separate from organization administration", () => {
    expect(canManageOrganization("member")).toBe(false)
    expect(canManageOrganization("admin")).toBe(true)
    expect(canManageOrganization("owner")).toBe(true)
  })

  it("rejects unknown organization roles", () => {
    expect(isOrganizationRole("member")).toBe(true)
    expect(isOrganizationRole("platform_admin")).toBe(false)
    expect(isOrganizationRole(undefined)).toBe(false)
  })

  it("keeps platform administration as an explicit global role", () => {
    expect(isPlatformAdmin("platform_admin")).toBe(true)
    expect(isPlatformAdmin(undefined)).toBe(false)
  })
})
