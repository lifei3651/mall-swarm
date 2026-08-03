import request from '@/utils/request'

export function getShopBrand() {
  return request({
    url: '/shop/home',
    method: 'get',
  })
}
