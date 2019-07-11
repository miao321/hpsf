package io.hpsf.rpc;

/**
 * 
 * @author winflex
 */
public class RpcException extends Exception {

	private static final long serialVersionUID = 6539190895617910901L;

	public RpcException() {
		super();
	}

	public RpcException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public RpcException(String message, Throwable cause) {
		super(message, cause);
	}

	public RpcException(String message) {
		super(message);
	}

	public RpcException(Throwable cause) {
		super(cause);
	}
}
