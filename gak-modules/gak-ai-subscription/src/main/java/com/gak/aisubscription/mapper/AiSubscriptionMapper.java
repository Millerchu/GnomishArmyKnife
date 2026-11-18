package com.gak.aisubscription.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.aisubscription.domain.AiSubscription;
import org.apache.ibatis.annotations.Mapper;
/** 智订记录持久化。 */
@Mapper
public interface AiSubscriptionMapper extends BaseMapper<AiSubscription> {}
