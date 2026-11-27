package com.gak.aisubscription.sync;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** 在 HTTP 客户端分发响应时限制累计字节数，超限后取消读取。 */
final class BoundedCostResponse implements HttpResponse.BodySubscriber<byte[]> {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private int received;

    @Override
    public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > MAX_BYTES - received) {
                subscription.cancel();
                delegate.onError(new AiSyncFailure("PROVIDER_RESPONSE_TOO_LARGE", false, 0));
                return;
            }
            received += buffer.remaining();
        }
        delegate.onNext(buffers);
    }

    @Override
    public void onError(Throwable error) {
        delegate.onError(error);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }
}
