const enabled = (value) => value === true || value === 1 || value === '1' || value === 'true'

export const resolveDirectoryGuideLayout = (modules = {}) => {
  const primary = enabled(modules.primaryCategories)
  const subcategories = enabled(modules.subcategories)
  const hotProducts = enabled(modules.hotProducts)
  const hasRightContent = subcategories || hotProducts
  if (!primary && !hasRightContent) return 'empty'
  if (primary && !hasRightContent) return 'primary-only'
  if (primary) return 'split'
  return 'content-only'
}
