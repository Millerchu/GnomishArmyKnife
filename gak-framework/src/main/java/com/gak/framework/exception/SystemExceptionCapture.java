package com.gak.framework.exception;

/**
 * 未处理系统异常的采集口。实现必须自行吞掉写入失败，不能改变原异常的处理结果。
 */
public interface SystemExceptionCapture {

    String SOURCE_HTTP = "HTTP";

    String SOURCE_ASYNC_TASK = "ASYNC_TASK";

    String SOURCE_SCHEDULER = "SCHEDULER";

    /**
     * 采集一条已经判定为未处理系统异常的失败。
     *
     * @return 服务端生成的编号；跳过采集时返回 null。有编号不保证写入已完成或成功
     * @param exception 原始异常，实现不得持久化其消息正文
     * @param source 来源，仅接受本接口声明的三个常量
     */
    String capture(Throwable exception, String source);
}
