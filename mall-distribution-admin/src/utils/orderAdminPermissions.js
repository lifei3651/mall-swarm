export const ORDER_ADMIN_PERMISSIONS = Object.freeze({
  viewOrders: 'shop:order',
  handleAfterSale: 'shop:aftersale',
  readFinance: 'finance:read',
})

export function resolveOrderAdminAccess(hasPermission) {
  return {
    canViewOrders: Boolean(hasPermission?.(ORDER_ADMIN_PERMISSIONS.viewOrders)),
    canHandleAfterSale: Boolean(hasPermission?.(ORDER_ADMIN_PERMISSIONS.handleAfterSale)),
    canReadFinance: Boolean(hasPermission?.(ORDER_ADMIN_PERMISSIONS.readFinance)),
  }
}
