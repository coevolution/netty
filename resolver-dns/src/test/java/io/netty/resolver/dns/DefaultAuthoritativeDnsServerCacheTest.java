/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.resolver.dns;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DefaultAuthoritativeDnsServerCacheTest {

    @Test
    public void testExpire() throws Throwable {
        InetSocketAddress resolved1 = new InetSocketAddress(
                InetAddress.getByAddress("ns1", new byte[] { 10, 0, 0, 1 }), 53);
        InetSocketAddress resolved2 = new InetSocketAddress(
                InetAddress.getByAddress("ns2", new byte[] { 10, 0, 0, 2 }), 53);
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultAuthoritativeDnsServerCache cache = new DefaultAuthoritativeDnsServerCache();
            cache.cache("netty.io", resolved1, 1, loop);
            cache.cache("netty.io", resolved2, 10000, loop);

            Throwable error = loop.schedule(new Callable<Throwable>() {
                @Override
                public Throwable call() {
                    try {
                        assertNull(cache.get("netty.io"));
                        return null;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            }, 1, TimeUnit.SECONDS).get();
            if (error != null) {
                throw error;
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testExpireWithDifferentTTLs() {
        testExpireWithTTL0(1);
        testExpireWithTTL0(1000);
        testExpireWithTTL0(1000000);
    }

    private static void testExpireWithTTL0(int days) {
        EventLoopGroup group = new NioEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultAuthoritativeDnsServerCache cache = new DefaultAuthoritativeDnsServerCache();
            cache.cache("netty.io", new InetSocketAddress(NetUtil.LOCALHOST, 53), days, loop);
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testAddMultipleDnsServerForSameHostname() throws Exception {
        InetSocketAddress resolved1 = new InetSocketAddress(
                InetAddress.getByAddress("ns1", new byte[] { 10, 0, 0, 1 }), 53);
        InetSocketAddress resolved2 = new InetSocketAddress(
                InetAddress.getByAddress("ns2", new byte[] { 10, 0, 0, 2 }), 53);
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultAuthoritativeDnsServerCache cache = new DefaultAuthoritativeDnsServerCache();
            cache.cache("netty.io", resolved1, 100, loop);
            cache.cache("netty.io", resolved2, 10000, loop);

            List<InetSocketAddress> entries = cache.get("netty.io");
            assertEquals(2, entries.size());
            assertEquals(entries.get(0), resolved1);
            assertEquals(entries.get(1), resolved2);
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testUnresolvedReplacedByResolved() throws Exception {
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("ns1", 53);
        InetSocketAddress resolved1 = new InetSocketAddress(
                InetAddress.getByAddress("ns2", new byte[] { 10, 0, 0, 2 }), 53);
        InetSocketAddress resolved2 = new InetSocketAddress(
                InetAddress.getByAddress("ns1", new byte[] { 10, 0, 0, 1 }), 53);
        EventLoopGroup group = new DefaultEventLoopGroup(1);

        try {
            EventLoop loop = group.next();
            final DefaultAuthoritativeDnsServerCache cache = new DefaultAuthoritativeDnsServerCache();
            cache.cache("netty.io", unresolved, 100, loop);
            cache.cache("netty.io", resolved1, 10000, loop);

            List<InetSocketAddress> entries = cache.get("netty.io");
            assertEquals(2, entries.size());
            assertEquals(unresolved, entries.get(0));
            assertEquals(resolved1, entries.get(1));

            cache.cache("netty.io", resolved2, 100, loop);
            List<InetSocketAddress> entries2 = cache.get("netty.io");

            assertEquals(2, entries2.size());
            assertEquals(resolved2, entries2.get(0));
            assertEquals(resolved1, entries2.get(1));
        } finally {
            group.shutdownGracefully();
        }
    }
}
