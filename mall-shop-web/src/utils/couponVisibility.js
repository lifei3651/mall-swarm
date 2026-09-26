export const canClaimCouponOnSurface = (coupon, isPublicSurface) =>
  !isPublicSurface || (Array.isArray(coupon?.businessTypes) && coupon.businessTypes.includes('NORMAL'))
