/**
 * 
 */
package io.hpsf.rpc.consumer;

import io.hpsf.common.ExtensionLoader;
import io.hpsf.common.concurrent.Future;
import io.hpsf.common.concurrent.NamedThreadFactory;
import io.hpsf.registry.api.Registration;
import io.hpsf.registry.api.Registry;
import io.hpsf.registry.api.ServiceMeta;
import io.hpsf.rpc.Invocation;
import io.hpsf.rpc.consumer.balance.LoadBalancerManager;
import io.hpsf.rpc.consumer.proxy.DefaultProxyFactory;
import io.hpsf.rpc.protocol.RpcRequest;
import io.hpsf.rpc.protocol.codec.Decoder;
import io.hpsf.rpc.protocol.codec.Encoder;
import io.hpsf.serialization.api.ISerializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC客户端实现
 * 
 * 
 * @author winflex
 */
@Slf4j
public class RpcClient {

	private final RpcClientConfig config; // 配置
	private EventLoopGroup workerGroup;
	private final ChannelManager channelManager;

	private final Registry registry;
	private final LoadBalancerManager loadBalancerManager = new LoadBalancerManager();

	public RpcClient() throws Exception {
		this(new RpcClientConfig());
	}

	public RpcClient(RpcClientConfig config) throws Exception {
		this.config = config;
		this.channelManager = new ChannelManager(createBootstrap(), config.getMaxConnections());
		this.registry = ExtensionLoader.getLoader(Registry.class).getExtension(config.getRegistry());
		this.registry.init(config.getRegistryAddress());
	}

	private Bootstrap createBootstrap() {
		Bootstrap b = new Bootstrap();
		if (Epoll.isAvailable()) {
			workerGroup = new EpollEventLoopGroup(config.getIoThreads(), new NamedThreadFactory("hpsf-rpc-worker"));
			b.channel(EpollSocketChannel.class);
			b.option(EpollChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis());
		} else {
			workerGroup = new NioEventLoopGroup(config.getIoThreads(), new NamedThreadFactory("hpsf-rpc-worker"));
			b.channel(NioSocketChannel.class);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis());
		}
		b.group(workerGroup);
		b.handler(new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				log.info("Channel connected, channel = {}", ch);
				ch.closeFuture().addListener((future) -> {
					log.info("Channel disconnected, channel = {}", ch);
				});

				ChannelPipeline pl = ch.pipeline();
				ISerializer serializer = ExtensionLoader.getLoader(ISerializer.class)
						.getExtension(config.getSerializer());
				pl.addLast(new FlushConsolidationHandler(256, true));
				pl.addLast(new Decoder(serializer));
				pl.addLast(new Encoder(serializer));
				pl.addLast(new ResponseHandler());
			}
		});
		return b;
	}

	public <T> T getServiceProxy(Class<T> iface) throws Exception {
		return (T) new DefaultProxyFactory().getProxy(new DefaultInvoker<>(iface, this));
	}

	public GenericService getGenericServiceProxy(String iface) throws Exception {
		return new DefaultProxyFactory().getProxy(new GenericInvoker(iface, this));
	}

	@SuppressWarnings("unchecked")
	<T> Future<T> send(Invocation inv) {
		RpcRequest request = new RpcRequest(inv);
		final long requestId = request.getId();
		final ResponseFuture future = new ResponseFuture(requestId, config.getRequestTimeoutMillis());
		try {
			ServiceMeta serviceMeta = new ServiceMeta(inv.getClassName(), inv.getVersion());
			Registration registration = loadBalancerManager.getLoadBalancer(serviceMeta)
					.select(registry.lookup(serviceMeta));
			Channel channel = channelManager.getChannel(registration.getEndpoint(), config.getConnectTimeoutMillis());
			channel.writeAndFlush(request).addListener((f) -> {
				if (!f.isSuccess()) {
					ResponseFuture.doneWithException(requestId, f.cause());
				}
			});
		} catch (Exception e) {
			ResponseFuture.doneWithException(requestId, e);
		}
		return (Future<T>) future;
	}

	public void close() {
		registry.close();
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}
	}

	public final RpcClientConfig getOptions() {
		return config;
	}
}
