import { describe, expect, it } from 'vitest'
import { formatDateTime, formatDateTimeCell } from '../src/utils/dateTime'

describe('dateTime display formatting', () => {
  it('replaces the ISO date-time separator with a space', () => {
    expect(formatDateTime('2026-08-08T15:09:55')).toBe('2026-08-08 15:09:55')
  })

  it('keeps already formatted date-time text stable', () => {
    expect(formatDateTime('2026-08-08 15:09:55')).toBe('2026-08-08 15:09:55')
  })

  it('returns a placeholder for empty values and supports table cells', () => {
    expect(formatDateTime()).toBe('-')
    expect(formatDateTimeCell({}, {}, '2026-08-08T15:09:55.123')).toBe('2026-08-08 15:09:55')
  })
})
