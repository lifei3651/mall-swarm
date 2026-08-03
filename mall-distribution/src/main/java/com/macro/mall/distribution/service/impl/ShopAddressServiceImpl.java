package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopAddressDao;
import com.macro.mall.distribution.dto.ShopAddressDTO;
import com.macro.mall.distribution.entity.DmsShopAddress;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAddressService;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopAddressServiceImpl implements ShopAddressService {

    private final DmsShopAddressDao addressDao;

    @Override
    public List<DmsShopAddress> list(DmsShopMember member) {
        return addressDao.selectByMemberId(member.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopAddress save(DmsShopMember member, ShopAddressDTO dto) {
        validate(dto);
        DmsShopAddress address = new DmsShopAddress();
        address.setId(dto.getId());
        address.setMemberId(member.getId());
        address.setUserId(member.getUserId());
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(PhoneNumberUtils.normalize(dto.getReceiverPhone()));
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setIsDefault(Integer.valueOf(1).equals(dto.getIsDefault()) ? 1 : 0);
        address.setStatus(1);

        if (Integer.valueOf(1).equals(address.getIsDefault())) {
            addressDao.clearDefault(member.getId());
        }
        if (address.getId() == null) {
            if (addressDao.selectByMemberId(member.getId()).isEmpty()) {
                address.setIsDefault(1);
            }
            addressDao.insert(address);
        } else {
            DmsShopAddress existing = addressDao.selectById(address.getId());
            if (existing == null || !member.getId().equals(existing.getMemberId())) {
                Asserts.fail("地址不存在");
            }
            addressDao.update(address);
        }
        return addressDao.selectById(address.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(DmsShopMember member, Long id) {
        DmsShopAddress existing = addressDao.selectById(id);
        if (existing == null || !member.getId().equals(existing.getMemberId())) {
            Asserts.fail("地址不存在");
        }
        boolean deleted = addressDao.deleteById(id, member.getId()) > 0;
        if (deleted && Integer.valueOf(1).equals(existing.getIsDefault())) {
            List<DmsShopAddress> remaining = addressDao.selectByMemberId(member.getId());
            if (!remaining.isEmpty()) {
                DmsShopAddress nextDefault = remaining.get(0);
                nextDefault.setIsDefault(1);
                addressDao.update(nextDefault);
            }
        }
        return deleted;
    }

    private void validate(ShopAddressDTO dto) {
        if (dto == null) {
            Asserts.fail("地址不能为空");
        }
        if (dto.getReceiverName() == null || dto.getReceiverName().isBlank()) {
            Asserts.fail("收货人不能为空");
        }
        if (!PhoneNumberUtils.isValidMainlandMobile(dto.getReceiverPhone())) {
            Asserts.fail("请填写正确的11位手机号");
        }
        if (dto.getProvince() == null || dto.getProvince().isBlank()
                || dto.getCity() == null || dto.getCity().isBlank()
                || dto.getDistrict() == null || dto.getDistrict().isBlank()) {
            Asserts.fail("请完整选择省、市、区/县");
        }
        if (dto.getDetailAddress() == null || dto.getDetailAddress().isBlank()) {
            Asserts.fail("详细地址不能为空");
        }
    }
}
