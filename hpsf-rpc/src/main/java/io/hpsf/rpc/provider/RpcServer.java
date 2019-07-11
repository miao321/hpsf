package io.hpsf.rpc.provider;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.hpsf.common.Endpoint;
import io.hpsf.common.ExtensionLoader;
import io.hpsf.common.concurrent.DefaultPromise;
import io.hpsf.common.concurrent.NamedThreadFactory;
import io.hpsf.common.concurrent.SynchronousExecutor;
import io.hpsf.registry.api.Registration;
import io.hpsf.registry.api.Registry;
import io.hpsf.registry.api.RegistryConfig;
import io.hpsf.registry.api.RegistryException;
import io.hpsf.registry.api.ServiceMeta;
import io.hpsf.rpc.RpcException;
import io.hpsf.rpc.protocol.codec.Decoder;
import io.hpsf.rpc.protocol.codec.Encoder;
import io.hpsf.serialization.api.ISerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 服务器实现
 * 
 * @author winflex
 */
@Slf4j
public class RpcServer implements Closeable {

	private final RpcServerConfig config;

	private final ThreadPoolExecutor executor;
	private final Registry registry;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel serverChannel;

	private final ConcurrentMap<String, Publishment> publishments = new ConcurrentHashMap<>();

	private final DefaultPromise<Void> closeFuture = new DefaultPromise<>();

	public RpcServer(RpcServerConfig config) throws RpcException {
		this.config = config;
		this.executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
				Runtime.getRuntime().availableProcessors() * 2, 1, TimeUnit.MINUTES,
				new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("hpsf-service-worker"));

		// 初始化注册中心
		this.registry = createAndInitRegistry(config.getRegistryConfig());
		// 初始化rpc server
		startAcceptor(config);
	}

	private Registry createAndInitRegistry(RegistryConfig config) throws RpcException {
		try {
			Registry registry = ExtensionLoader.getLoader(Registry.class).getExtension(config.getType());
			registry.init(config.getConnectString());
			return registry;
		} catch (Exception e) {
			throw new RpcException(e);
		}
	}

	private void startAcceptor(RpcServerConfig config) throws RpcException {
		ServerBootstrap b = new ServerBootstrap();
		if (Epoll.isAvailable()) {
			log.info("using epoll feature");
			bossGroup = new EpollEventLoopGroup(1, new NamedThreadFactory("hpsf-rpc-acceptor"));
			workerGroup = new EpollEventLoopGroup(config.getIoThreads(), new NamedThreadFactory("hpsf-rpc-worker"));
			b.channel(EpollServerSocketChannel.class);
			b.childOption(EpollChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		} else {
			bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory("hpsf-rpc-acceptor"));
			workerGroup = new NioEventLoopGroup(config.getIoThreads(), new NamedThreadFactory("hpsf-rpc-worker"));
			b.channel(NioServerSocketChannel.class);
			b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		}
		b.group(bossGroup, workerGroup);
		b.childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				log.info("Channel connected, channel = {}", ch);
				ch.closeFuture().addListener((future) -> {
					log.info("Channel disconnected, channel = {}", ch);
				});
				ChannelPipeline pl = ch.pipeline();
				pl.addLast(new FlushConsolidationHandler(256, true));
				pl.addLast(new IdleStateHandler(config.getHeartbeatInterval() * 2, 0, 0, TimeUnit.MILLISECONDS));
				ISerializer serializer = ExtensionLoader.getLoader(ISerializer.class)
						.getExtension(config.getSerializer());
				pl.addLast(new Decoder(serializer));
				pl.addLast(new Encoder(serializer));
				pl.addLast(new RequestHandler(RpcServer.this));
			}
		});
		ChannelFuture f = b.bind(config.getIp(), config.getPort()).syncUninterruptibly();
		if (f.isSuccess()) {
			this.serverChannel = f.channel();
			log.info("RpcServer is listening on {}:{}", config.getIp(), config.getPort());
		} else {
			throw new RpcException(f.cause());
		}
	}

	/**
	 * 发布服务
	 */
	public void publish(Class<?> iface, Object serviceInstance, String serviceVersion) throws RegistryException {
		publish(iface, serviceInstance, serviceVersion, SynchronousExecutor.INSTANCE);
	}

	/**
	 * 发布服务
	 */
	public void publish(Class<?> iface, Object serviceInstance, String serviceVersion, Executor executor)
			throws RegistryException {
		String serviceName = iface.getName();
		ServiceMeta meta = new ServiceMeta(serviceName, serviceVersion);
		// 本地上线
		publishments.put(meta.directoryString(),
				new Publishment(serviceName, serviceVersion, serviceInstance, executor));
		// 在注册中心上线
		registry.register(new Registration(new Endpoint(config.getIp(), config.getPort()), meta));
	}

	/**
	 * 反发布的服务
	 */
	public void unpublish(Class<?> iface, Object serviceInstance, String serviceVersion) throws RegistryException {
		String serviceName = iface.getName();
		ServiceMeta meta = new ServiceMeta(serviceName, serviceVersion);
		// 从注册中心下线
		registry.unregister(new Registration(new Endpoint(config.getIp(), config.getPort()), meta));
		// 本地下线
		publishments.remove(meta.directoryString());
	}

	/**
	 * 查找本地服务
	 */
	public Publishment lookup(ServiceMeta meta) {
		return publishments.get(meta.directoryString());
	}

	@Override
	public void close() throws IOException {
		if (serverChannel != null) {
			serverChannel.close();
		}
		if (bossGroup != null) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}

		executor.shutdownNow();

		registry.close();
		log.info("Server shutdown");
		closeFuture.setSuccess(null);
	}

	public final Executor getExecutor() {
		return executor;
	}

	public final RpcServerConfig getConfig() {
		return config;
	}

	public final void join() {
		closeFuture.awaitUninterruptibly();
	}
}
