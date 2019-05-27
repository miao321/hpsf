package io.hpsf.rpc.server;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.hpsf.common.ExtensionLoader;
import io.hpsf.common.concurrent.DefaultPromise;
import io.hpsf.common.concurrent.IFuture;
import io.hpsf.common.concurrent.NamedThreadFactory;
import io.hpsf.rpc.common.RpcException;
import io.hpsf.rpc.common.codec.Decoder;
import io.hpsf.rpc.common.codec.Encoder;
import io.hpsf.serialization.api.ISerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 服务器实现
 * 
 * @author winflex
 */
@Slf4j
public class RpcServer extends DefaultRegistryCenter {
	private final RpcServerOptions options;
	private final Executor executor;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private Channel serverChannel;

	private final AtomicBoolean closed = new AtomicBoolean();
	private final DefaultPromise<Void> closeFuture = new DefaultPromise<>();

	public RpcServer(int port) {
		this(new RpcServerOptions(port));
	}

	public RpcServer(RpcServerOptions options) {
		this.options = options;
		if (options.getExecutor() == null) {
			executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
					Runtime.getRuntime().availableProcessors() * 2, 1, TimeUnit.MINUTES,
					new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("Rpc-Service-Exeecutor"));
		} else {
			executor = options.getExecutor();
		}
	}

	public RpcServer start() throws RpcException {
		this.bossGroup = new NioEventLoopGroup(1);
		this.workerGroup = new NioEventLoopGroup(options.getIoThreads());

		ServerBootstrap b = new ServerBootstrap();
		b.group(bossGroup, workerGroup);
		b.channel(NioServerSocketChannel.class);
		b.childHandler(new ChannelInitializer<NioSocketChannel>() {

			@Override
			protected void initChannel(NioSocketChannel ch) throws Exception {
				log.info("Channel connected, channel = {}", ch);
				ch.closeFuture().addListener((future) -> {
					log.info("Channel disconnected, channel = {}", ch);
				});
				ChannelPipeline pl = ch.pipeline();
				pl.addLast(new FlushConsolidationHandler(256, true));
				pl.addLast(new IdleStateHandler(options.getHeartbeatInterval() * 2, 0, 0, TimeUnit.MILLISECONDS));
				ISerializer serializer = ExtensionLoader.getLoader(ISerializer.class).getExtension(options.getSerializer());
				pl.addLast(new Decoder(serializer));
				pl.addLast(new Encoder(serializer));
				pl.addLast(new RequestHandler(RpcServer.this));
			}
		});

		ChannelFuture f = null;
		f = b.bind(options.getBindIp(), options.getPort()).syncUninterruptibly();

		if (f.isSuccess()) {
			this.serverChannel = f.channel();
			log.info("Server listening on {}:{}", options.getBindIp(), options.getPort());
		} else {
			throw new RpcException(f.cause());
		}
		return this;
	}

	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}

		try {
			if (serverChannel != null) {
				serverChannel.close().syncUninterruptibly();
			}
			if (bossGroup != null) {
				bossGroup.shutdownGracefully();
			}
			if (workerGroup != null) {
				workerGroup.shutdownGracefully();
			}
			
			if (options.getExecutor() == null) { // shutdown executor only if it was created by server
				((ThreadPoolExecutor) executor).shutdownNow();
			}
	
			log.info("Server shutdown");
			closeFuture.setSuccess(null);
		} catch (Throwable e) {
			closeFuture.setFailure(e);
		}
	}
	
	@Override
	public void register(Class<?> iface, Object instance, Executor executor) {
		super.register(iface, instance, executor);
		log.info("Published interface {}, instance = {}", iface, instance);
	}

	public final Executor getExecutor() {
		return executor;
	}
	
	public final RpcServerOptions getOptions() {
		return options;
	}

	public final IFuture<Void> closeFuture() {
		return this.closeFuture;
	}
}