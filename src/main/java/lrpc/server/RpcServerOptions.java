package lrpc.server;

/**
 * 
 * @author winflex
 */
public class RpcServerOptions {

	private int port;

	private String bindIp = "0.0.0.0";

	private int ioThreads = 0;

	private int heartbeatInterval = 10000;

	public RpcServerOptions(int port) {
		if (port <= 0) {
			throw new IllegalArgumentException("port must be positive");
		}
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getBindIp() {
		return bindIp;
	}

	public void setBindIp(String bindIp) {
		this.bindIp = bindIp;
	}

	public int getIoThreads() {
		return ioThreads;
	}

	public void setIoThreads(int ioThreads) {
		this.ioThreads = ioThreads;
	}

	public int getHeartbeatInterval() {
		return heartbeatInterval;
	}

	public void setHeartbeatInterval(int heartbeatInterval) {
		this.heartbeatInterval = heartbeatInterval;
	}
}
