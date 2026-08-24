import request from '@/utils/request'
export const listMessageTemplates=()=>request({url:'/shop/admin/message-operations/templates',method:'get'})
export const updateMessageTemplate=(id,data)=>request({url:`/shop/admin/message-operations/templates/${id}`,method:'put',data})
export const listMessageChannels=()=>request({url:'/shop/admin/message-operations/channels',method:'get'})
export const updateInAppChannel=(id,enabled)=>request({url:`/shop/admin/message-operations/channels/${id}/in-app`,method:'put',params:{enabled}})
export const listMessageDeliveries=(params)=>request({url:'/shop/admin/message-operations/deliveries',method:'get',params})
