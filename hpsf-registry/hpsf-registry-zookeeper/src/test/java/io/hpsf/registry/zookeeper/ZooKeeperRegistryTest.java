package io.hpsf.registry.zookeeper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.hpsf.common.Endpoint;
import io.hpsf.registry.api.NotifyListener;
import io.hpsf.registry.api.Registration;
import io.hpsf.registry.api.ServiceMeta;
import io.hpsf.registry.api.NotifyListener.NotifyType;

/**
 * 
 * @author winflex
 */
public class ZooKeeperRegistryTest {

	private static TestingServer server;

	private static ZookeeperRegistry registry;

	@Test
	public void testRegisterAndUnregister() throws Exception {
		ServiceMeta meta1 = new ServiceMeta("test_service_1", "1.0");
		Endpoint endpoint1 = new Endpoint("127.0.0.1", 9999);
		Registration registration1 = new Registration(endpoint1, meta1);
		registry.register(registration1);

		List<Registration> registrations = registry.lookup(meta1);
		Assert.assertEquals(1, registrations.size());
		Assert.assertEquals(registration1, registrations.get(0));

		registry.unregister(registration1);
		registrations = registry.lookup(meta1);
		Assert.assertEquals(0, registrations.size());
	}

	@Test
	public void testSubscribeOnlineAndOffline() throws Exception {
		ServiceMeta meta1 = new ServiceMeta("test_service_1", "1.0");
		Endpoint endpoint1 = new Endpoint("127.0.0.1", 9999);
		Registration registration1 = new Registration(endpoint1, meta1);

		// 测试订阅上线
		CountDownLatch registerLatch = new CountDownLatch(1);
		final List<Object> registerResult = new ArrayList<>();
		final NotifyListener registerListener = (r, type) -> {
			registerResult.add(r);
			registerResult.add(type);
			registerLatch.countDown();
		};
		registry.subscribe(meta1, registerListener);
		registry.register(registration1);
		if (!registerLatch.await(3, TimeUnit.SECONDS)) {
			throw new Exception("register timed out!!");
		}
		Assert.assertEquals(registerResult.get(0), registration1);
		Assert.assertEquals(registerResult.get(1), NotifyType.ONLINE);
		registry.unsubscribe(meta1, registerListener);

		// 测试订阅下线
		CountDownLatch unregisterLatch = new CountDownLatch(1);
		final List<Object> unregisterResult = new ArrayList<>();
		final NotifyListener unregisterListener = (r, type) -> {
			unregisterResult.add(r);
			unregisterResult.add(type);
			unregisterLatch.countDown();
		};
		registry.subscribe(meta1, unregisterListener);
		registry.unregister(registration1);
		if (!unregisterLatch.await(3, TimeUnit.SECONDS)) {
			throw new Exception("unregister timed out!!");
		}
		Assert.assertEquals(unregisterResult.get(0), registration1);
		Assert.assertEquals(unregisterResult.get(1), NotifyType.OFFLINE);
		registry.unsubscribe(meta1, unregisterListener);
	}

	@BeforeClass
	public static void setUp() throws Exception {
		while (true) {
			int port = ThreadLocalRandom.current().nextInt(10000, 60000);
			try {
				server = new TestingServer(port);
				break;
			} catch (Exception e) {
			}
		}
	}

	@AfterClass
	public static void tearDown() throws IOException {
		server.close();
	}

	@Before
	public void before() throws Exception {
		registry = new ZookeeperRegistry();
		registry.init("127.0.0.1:" + server.getPort());
		
		Field field = registry.getClass().getDeclaredField("curator");
		field.setAccessible(true);
		CuratorFramework curator = (CuratorFramework) field.get(registry);
		try {
			curator.delete().deletingChildrenIfNeeded().forPath(ZookeeperRegistry.ROOT);
		} catch (Exception e) {
		}
	}
	
	public void after() throws Exception {
		registry.close();
	}
}
