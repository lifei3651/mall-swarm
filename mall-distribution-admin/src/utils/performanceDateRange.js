const toLocalDate = (date = new Date()) => {
  const value = new Date(date)
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

const addDays = (date, days) => {
  const value = toLocalDate(date)
  value.setDate(value.getDate() + days)
  return value
}

export const createRecentDateRange = (days = 30, referenceDate = new Date()) => {
  const end = toLocalDate(referenceDate)
  return [addDays(end, -(days - 1)), end]
}

const createTodayRange = () => {
  const today = toLocalDate()
  return [today, today]
}

const createYesterdayRange = () => {
  const yesterday = addDays(new Date(), -1)
  return [yesterday, yesterday]
}

const createCurrentMonthRange = () => {
  const today = toLocalDate()
  return [new Date(today.getFullYear(), today.getMonth(), 1), today]
}

const createPreviousMonthRange = () => {
  const today = toLocalDate()
  return [
    new Date(today.getFullYear(), today.getMonth() - 1, 1),
    new Date(today.getFullYear(), today.getMonth(), 0),
  ]
}

export const performanceDateShortcuts = [
  { text: '今天', value: createTodayRange },
  { text: '昨天', value: createYesterdayRange },
  { text: '近7天（含今天）', value: () => createRecentDateRange(7) },
  { text: '近30天（含今天）', value: () => createRecentDateRange(30) },
  { text: '本月', value: createCurrentMonthRange },
  { text: '上月', value: createPreviousMonthRange },
]
