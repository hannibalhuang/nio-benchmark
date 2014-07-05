/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.benchmark.echo.server;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.benchmark.echo.Constant;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 2105 $, $Date: 2010-01-28 16:19:52 +0800 (星期四, 28 一月 2010) $
 *
 */
public class Netty {

    public static void main(String[] args) throws Exception {
        boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");

        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        if (!threadPoolDisabled) {
            bootstrap.getPipeline().addLast(
                    "executor",
                    new ExecutionHandler(
                            new OrderedMemoryAwareThreadPoolExecutor(
                                    Constant.THREAD_POOL_SIZE,
                                    Constant.CHANNEL_MEMORY_LIMIT,
                                    Constant.GLOBAL_MEMORY_LIMIT,
                                    0, TimeUnit.MILLISECONDS)));
        }

        bootstrap.getPipeline().addLast("handler", new EchoHandler());

        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption(
                "child.receiveBufferSizePredictorFactory",
                new AdaptiveReceiveBufferSizePredictorFactory(
                        Constant.MIN_READ_BUFFER_SIZE,
                        Constant.INITIAL_READ_BUFFER_SIZE,
                        Constant.MAX_READ_BUFFER_SIZE)
                );

        bootstrap.bind(new InetSocketAddress(8007));

        System.out.println("Netty EchoServer is ready to serve at port " + Constant.PORT + ".");
        System.out.println("Enter 'ant benchmark' on the client side to begin.");
        System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
    }

    @ChannelPipelineCoverage("all")
    private static class EchoHandler extends SimpleChannelHandler {

        EchoHandler() {
            super();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            e.getCause().printStackTrace();
            e.getChannel().close();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            e.getChannel().write(e.getMessage());
        }
    }
}
