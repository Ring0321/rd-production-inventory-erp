export const canViewWarehouseSpace = (roles: readonly string[], permissions: ReadonlySet<string>) =>
  !roles.includes('rd_forklift_driver') &&
  (permissions.has('rd:putaway-task:query') || permissions.has('*:*:*'))
