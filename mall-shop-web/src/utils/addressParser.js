import { pcaTextArr } from 'element-china-area-data'

const MUNICIPALITIES = new Set(['北京市', '天津市', '上海市', '重庆市'])
const SPECIAL_ALIASES = {
  内蒙古自治区: ['内蒙古'],
  广西壮族自治区: ['广西'],
  西藏自治区: ['西藏'],
  宁夏回族自治区: ['宁夏'],
  新疆维吾尔自治区: ['新疆'],
  香港特别行政区: ['香港'],
  澳门特别行政区: ['澳门'],
}

const unique = (values) => [...new Set(values.filter(Boolean))]
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const clean = (value) => String(value || '').replace(/[\u00a0\t]+/g, ' ').replace(/\s+/g, ' ').trim()

const aliases = (label, level) => {
  const result = [label, ...(SPECIAL_ALIASES[label] || [])]
  if (level === 'province' && /省$/.test(label)) result.push(label.slice(0, -1))
  if (level === 'city' && /(?:市|地区|盟|自治州)$/.test(label) && label !== '市辖区') {
    result.push(label.replace(/(?:市|地区|盟|自治州)$/, ''))
  }
  return unique(result).sort((a, b) => b.length - a.length)
}

const locate = (source, values) => {
  for (const value of values) {
    const index = source.indexOf(value)
    if (index >= 0) return { index, value }
  }
  return null
}

const resolveRegion = (source) => {
  const candidates = []
  for (const province of pcaTextArr) {
    const provinceMatch = locate(source, aliases(province.label, 'province'))
    for (const city of province.children || []) {
      const inferredCity = city.label === '市辖区' && MUNICIPALITIES.has(province.label)
      const cityMatch = inferredCity ? null : locate(source, aliases(city.label, 'city'))
      for (const district of city.children || []) {
        const districtMatch = locate(source, [district.label])
        if (!districtMatch && !cityMatch && !provinceMatch) continue
        if (!districtMatch && !(provinceMatch && cityMatch)) continue

        let score = 0
        if (provinceMatch) score += 20 + provinceMatch.value.length
        if (cityMatch) score += 30 + cityMatch.value.length
        if (districtMatch) score += 45 + districtMatch.value.length
        if (inferredCity && provinceMatch && districtMatch) score += 25

        const positions = [provinceMatch?.index, cityMatch?.index, districtMatch?.index].filter(Number.isInteger)
        if (positions.every((value, index) => index === 0 || positions[index - 1] <= value)) score += 12

        candidates.push({
          score,
          province: province.value,
          city: city.value,
          district: districtMatch ? district.value : '',
          matchedTerms: unique([provinceMatch?.value, cityMatch?.value, districtMatch?.value]),
        })
      }
    }
  }
  candidates.sort((a, b) => b.score - a.score)
  if (!candidates.length) return { province: '', city: '', district: '', matchedTerms: [] }
  if (candidates[1] && candidates[0].score === candidates[1].score
      && candidates[0].district !== candidates[1].district) {
    return { province: '', city: '', district: '', matchedTerms: [] }
  }
  return candidates[0]
}

const removeOnce = (source, value) => value
  ? source.replace(new RegExp(escapeRegExp(value)), ' ')
  : source

const findName = (source, phone, regionTerms) => {
  const labeled = source.match(/(?:收货人|收件人|联系人|姓名)\s*[：:]?\s*([\u4e00-\u9fa5A-Za-z·]{2,20})/)
  if (labeled) return labeled[1]

  const beforePhone = phone ? source.slice(0, source.indexOf(phone)) : source
  const terms = new Set(regionTerms)
  return beforePhone
    .split(/[\s,，;；|/]+/)
    .map((item) => item.replace(/^(?:收货人|收件人|联系人|姓名)[：:]?/, '').trim())
    .reverse()
    .find((item) => /^[\u4e00-\u9fa5A-Za-z·]{2,10}$/.test(item)
      && !terms.has(item)
      && !/[省市区县镇乡村路街道号楼室]/.test(item)) || ''
}

/**
 * 解析常见的中文收货信息文本。所有结果仍应由用户在提交订单前核对。
 */
export function parseChineseAddress(rawText) {
  const source = clean(rawText)
  if (!source) return { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '' }

  const phoneMatch = source.match(/(?:\+?86[-\s]?)?(1[3-9]\d{9})(?!\d)/)
  const receiverPhone = phoneMatch?.[1] || ''
  const region = resolveRegion(source)
  const receiverName = findName(source, receiverPhone, region.matchedTerms)

  const labeledDetail = source.match(/(?:详细地址|收货地址)\s*[：:]?\s*(.+)$/)?.[1]
  let detailAddress = labeledDetail || source
  detailAddress = detailAddress
    .replace(/(?:收货人|收件人|联系人|姓名|手机号码|手机号|联系电话|电话|所在地区|地区|详细地址|收货地址)\s*[：:]?/g, ' ')
    .replace(/(?:\+?86[-\s]?)?1[3-9]\d{9}/g, ' ')
  detailAddress = removeOnce(detailAddress, receiverName)
  for (const term of region.matchedTerms.sort((a, b) => b.length - a.length)) {
    detailAddress = removeOnce(detailAddress, term)
  }
  detailAddress = clean(detailAddress
    .replace(/[\s,，;；|/]+/g, ' ')
    .replace(/^[\s-]+|[\s-]+$/g, ''))

  return {
    receiverName,
    receiverPhone,
    province: region.province,
    city: region.city,
    district: region.district,
    detailAddress,
  }
}
