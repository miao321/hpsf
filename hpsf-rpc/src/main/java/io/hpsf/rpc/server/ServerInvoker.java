package io.hpsf.rpc.server;

import java.lang.reflect.Method;

import org.hpsf.registry.api.ServiceMeta;

import io.hpsf.rpc.common.IInvoker;
import io.hpsf.rpc.common.Invocation;
import io.hpsf.rpc.common.RpcException;

/**
 * 本地调用
 * 
 * @author winflex
 */
public class ServerInvoker<T> implements IInvoker<T> {

	private final Class<T> iface;
	private final RpcServer rpcServer;

	public ServerInvoker(Class<T> iface, RpcServer rpcServer) {
		this.iface = iface;
		this.rpcServer = rpcServer;
	}

	@Override
	public Object invoke(Invocation inv) throws Throwable {
		ServiceMeta meta = new ServiceMeta(inv.getServiceName(), inv.getServiceVersion());
		Publishment publishment = rpcServer.lookup(meta);
		if (publishment == null) {
			throw new RpcException(inv.getClassName() + " is not published");
		}
		
		Object instance = publishment.getServiceInstance(); // the service instance
		Method method = instance.getClass().getMethod(inv.getMethodName(), inv.getParameterTypes());
		if (method == null) {
			throw new Exception(inv.getClassName() + "." + inv.getMethodName()
					+ " is not published");
		}
		return method.invoke(instance, inv.getParemeters());
	}

	@Override
	public Class<T> getInterface() {
		return iface;
	}
}
