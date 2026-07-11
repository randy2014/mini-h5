package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.VipInvitationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VipInvitationCodeMapper extends BaseMapper<VipInvitationCode> {
    @Select("SELECT * FROM vip_invitation_code WHERE code = #{code} LIMIT 1 FOR UPDATE")
    VipInvitationCode selectByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM vip_invitation_code WHERE owner_user_id = #{ownerUserId} AND is_current = 1 LIMIT 1")
    VipInvitationCode selectCurrentByOwner(@Param("ownerUserId") Long ownerUserId);

    @Select("SELECT * FROM vip_invitation_code WHERE owner_user_id = #{ownerUserId} AND is_current = 1 LIMIT 1 FOR UPDATE")
    VipInvitationCode selectCurrentByOwnerForUpdate(@Param("ownerUserId") Long ownerUserId);
}
