package io.hpsf.rpc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.hpsf.common.concurrent.DefaultPromise;
import io.hpsf.common.concurrent.Future;

/**
 * 
 * @author winflex
 */
public class RpcContext {
	
	private static final ThreadLocal<RpcContext> LOCAL = new ThreadLocal<RpcContext>() {
        @Override
        protected RpcContext initialValue() {
            return new RpcContext();
        }
    };
	
	public static RpcContext getContext() {
        return LOCAL.get();
    }
	
	public static void removeContext() {
        LOCAL.remove();
    }
	
	
	private boolean async;
	
	private Future<?> future;
	
	private Map<String, String> attachments = new HashMap<>();
	
	/**
	 * 同步转异步调用
	 */
	@SuppressWarnings("unchecked")
	public <T> Future<T> asyncCall(Callable<T> callable) {
		this.async = true;
		try {
			callable.call();
		} catch (Throwable e) {
			if (future == null) {
				DefaultPromise<T> future = new DefaultPromise<>();
				future.setFailure(e);
				return future;
			}
		} finally {
			async = false;
		}
		return (Future<T>) this.future;
	}

	public final boolean isAsync() {
		return async;
	}

	public final Future<?> getFuture() {
		return future;
	}

	public final void setFuture(Future<?> future) {
		this.future = future;
	}

	public Map<String, String> getAttachments() {
		return attachments;
	}

	public void setAttachments(Map<String, String> attachments) {
		this.attachments.clear();
		this.attachments.putAll(attachments);
	}
	
	public void addAttachments(Map<String, String> attachments) {
		this.attachments.putAll(attachments);
	}
	
	public void addAttachment(String key, String value) {
		this.attachments.put(key, value);
	}

	public String getAttachment(String key) {
		return this.attachments.get(key);
	}
}
