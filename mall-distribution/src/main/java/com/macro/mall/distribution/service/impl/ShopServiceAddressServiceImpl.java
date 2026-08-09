package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.service.ShopServiceAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopServiceAddressServiceImpl implements ShopServiceAddressService {

    private final DmsShopServiceAddressDao addressDao;

    @Override
    public List<DmsShopServiceAddress> list(Long tenantId, Integer addressType, Integer status) {
        return addressDao.selectList(tenantId == null ? 1L : tenantId, addressType, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopServiceAddress save(DmsShopServiceAddress address) {
        validate(address);
        address.setTenantId(address.getTenantId() == null ? 1L : address.getTenantId());
        address.setAddressLabel(blankToNull(address.getAddressLabel()));
        address.setIsDefault(Integer.valueOf(1).equals(address.getIsDefault()) ? 1 : 0);
        address.setStatus(1);
        if (address.getIsDefault() == 1) addressDao.clearDefault(address.getTenantId(), address.getAddressType());
        if (address.getId() == null) {
            if (addressDao.selectList(address.getTenantId(), address.getAddressType(), 1).isEmpty()) address.setIsDefault(1);
            addressDao.insert(address);
        } else {
            DmsShopServiceAddress existing = addressDao.selectById(address.getId());
            if (existing == null || !address.getTenantId().equals(existing.getTenantId())) Asserts.fail("地址不存在");
            addressDao.update(address);
        }
        return addressDao.selectById(address.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Long tenantId, Integer status) {
        if (id == null) Asserts.fail("地址ID不能为空");
        DmsShopServiceAddress existing = addressDao.selectById(id);
        if (existing == null || !existing.getTenantId().equals(tenantId == null ? 1L : tenantId)) Asserts.fail("地址不存在");
        if (status != null && status == 1) {
            existing.setStatus(1);
            existing.setIsDefault(1);
            addressDao.clearDefault(existing.getTenantId(), existing.getAddressType());
            addressDao.update(existing);
            return true;
        }
        return addressDao.updateStatus(id, existing.getTenantId(), 0) > 0;
    }

    @Override
    public DmsShopServiceAddress getDefault(Long tenantId, Integer addressType) {
        return addressDao.selectDefault(tenantId == null ? 1L : tenantId, addressType);
    }

    private void validate(DmsShopServiceAddress address) {
        if (address == null) Asserts.fail("地址不能为空");
        if (address.getAddressType() == null || (address.getAddressType() != 1 && address.getAddressType() != 2)) Asserts.fail("地址类型不正确");
        if (isBlank(address.getContactName())) Asserts.fail("请填写联系人");
        if (isBlank(address.getContactPhone())) Asserts.fail("请填写联系电话");
        String contactPhone = address.getContactPhone().trim();
        if (!contactPhone.matches("^(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}|(?:400|800)-?\\d{3}-?\\d{4})$")) {
            Asserts.fail("请填写正确的手机号或座机号码");
        }
        address.setContactPhone(contactPhone);
        if (isBlank(address.getProvince()) || isBlank(address.getCity()) || isBlank(address.getDistrict())) Asserts.fail("请完整选择省、市、区/县");
        if (isBlank(address.getDetailAddress())) Asserts.fail("请填写详细地址");
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }
}
