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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.benchmark.echo.Constant;

import ch.unifr.nio.framework.AbstractAcceptor;
import ch.unifr.nio.framework.BufferSizeListener;
import ch.unifr.nio.framework.ChannelHandler;
import ch.unifr.nio.framework.Dispatcher;
import ch.unifr.nio.framework.FrameworkTools;
import ch.unifr.nio.framework.HandlerAdapter;
import ch.unifr.nio.framework.transform.ChannelReader;
import ch.unifr.nio.framework.transform.ChannelWriter;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 2105 $, $Date: 2010-01-28 16:19:52 +0800 (星期四, 28 一月 2010) $
 *
 */
public class NIOFramework extends AbstractAcceptor {

    NIOFramework(Dispatcher dispatcher, SocketAddress socketAddress) throws IOException {
        super(dispatcher, socketAddress);
    }

    public static void main(String[] args) throws Exception {
        boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");

        Dispatcher dispatcher = new Dispatcher();

        if (threadPoolDisabled) {
            dispatcher.setExecutor(new ImmediateExecutor());
        } else {
            dispatcher.setExecutor(Executors.newFixedThreadPool(Constant.THREAD_POOL_SIZE));
        }
        dispatcher.start();
        SocketAddress socketAddress = new InetSocketAddress(Constant.PORT);
        NIOFramework echoServer = new NIOFramework(dispatcher, socketAddress);
        echoServer.start();

        System.out.println("NIOFramework EchoServer is ready to serve at port " + Constant.PORT + ".");
        System.out.println("Enter 'ant benchmark' on the client side to begin.");
        System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
    }

    @Override
    protected ChannelHandler getHandler(SocketChannel socketChannel) {
        return new EchoHandler();
    }

    private class EchoHandler
            implements ChannelHandler, BufferSizeListener {

        private final ChannelReader reader;
        private final ChannelWriter writer;
        private HandlerAdapter handlerAdapter;

        public EchoHandler() {
            reader = new ChannelReader(
                    false,
                    Constant.MAX_READ_BUFFER_SIZE,
                    Constant.CHANNEL_MEMORY_LIMIT);
            writer = new ChannelWriter(false);
            writer.addBufferSizeListener(this);
        }

        @SuppressWarnings("synthetic-access")
        public void channelRegistered(HandlerAdapter handlerAdapter) {
            this.handlerAdapter = handlerAdapter;
            SocketChannel channel = (SocketChannel) handlerAdapter.getChannel();
            try {
                channel.socket().setTcpNoDelay(true);
            } catch (SocketException e) {
                try {
                    handlerAdapter.closeChannel();
                } catch (IOException e2) {
                    FrameworkTools.handleStackTrace(logger, e2);
                }
            }
        }

        @SuppressWarnings("synthetic-access")
        public void handleInput() {
            try {
                writer.transform(reader.getBuffer());
            } catch (Exception e) {
                try {
                    handlerAdapter.closeChannel();
                } catch (IOException e2) {
                    FrameworkTools.handleStackTrace(logger, e);
                }
            }
        }

        @SuppressWarnings("synthetic-access")
        public void channelException(Exception exception) {
            try {
                handlerAdapter.closeChannel();
            } catch (IOException e) {
                FrameworkTools.handleStackTrace(logger, e);
            }
        }

        public void bufferSizeChanged(Object source, int newLevel) {
            if (newLevel == 0) {
                handlerAdapter.addInterestOps(SelectionKey.OP_READ);
            } else {
                handlerAdapter.removeInterestOps(SelectionKey.OP_READ);
            }
        }

        public void inputClosed() {
            // NOOP
        }

        public ChannelReader getChannelReader() {
            return reader;
        }

        public ChannelWriter getChannelWriter() {
            return writer;
        }
    }
    
    private static class ImmediateExecutor implements Executor {
    	ImmediateExecutor() {
    		super();
    	}
    	
    	public void execute(Runnable runnable) {
    		runnable.run();
    	}
    }
}
