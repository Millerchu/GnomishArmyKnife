package com.gak.framework.message;

/** 消息发布端口；实现必须加入调用方事务，提交后才通知在线用户。 */
public interface MessagePublisher {
    Long publish(PublishMessageCommand command);
}
