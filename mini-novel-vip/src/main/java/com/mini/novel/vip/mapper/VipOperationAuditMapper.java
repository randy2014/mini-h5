package com.mini.novel.vip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.vip.entity.VipOperationAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VipOperationAuditMapper extends BaseMapper<VipOperationAudit> {
    @Select("SELECT * FROM vip_operation_audit WHERE request_id = #{requestId} LIMIT 1")
    VipOperationAudit selectByRequestId(@Param("requestId") String requestId);
}
