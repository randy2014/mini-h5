package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.VipInvitationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VipInvitationRecordMapper extends BaseMapper<VipInvitationRecord> {
    @Select("SELECT * FROM vip_invitation_record WHERE invitee_user_id = #{inviteeUserId} LIMIT 1 FOR UPDATE")
    VipInvitationRecord selectByInviteeForUpdate(@Param("inviteeUserId") Long inviteeUserId);
}
