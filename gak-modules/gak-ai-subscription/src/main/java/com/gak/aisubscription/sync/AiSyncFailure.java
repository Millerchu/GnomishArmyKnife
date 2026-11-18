package com.gak.aisubscription.sync;

/** 只保存允许公开的错误码，绝不携带厂商响应正文或请求头。 */
public final class AiSyncFailure extends RuntimeException {
    private final boolean retryable;
    private final long retryAfterSeconds;
    public AiSyncFailure(String code, boolean retryable, long retryAfterSeconds) {
        super(code);
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public boolean retryable() { return retryable; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
