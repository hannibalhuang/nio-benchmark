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
package org.jboss.netty.benchmark.echo.client;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.benchmark.echo.Constant;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.AdaptiveReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 2105 $, $Date: 2010-01-28 16:19:52 +0800 (星期四, 28 一月 2010) $
 *
 */
public class Benchmark {

    private static final int[] messageSizeTable = {
        128
        //64 //, 128, 256, 1024, 4096, 16384,
    };

    private static final int[] connectionTable = {
        5
        //1, 5, 10, 50, 100, 500, 1000, 5000, 10000,
    };

    static final AtomicLong counter = new AtomicLong();
    static final BlockingQueue<String> msgs = new LinkedBlockingQueue<String>();

    public static void main(String[] args) {
        String host = args.length == 0? "localhost" : args[0];
        ExecutorService executor = Executors.newCachedThreadPool();
        ChannelFactory factory = new NioClientSocketChannelFactory(executor, executor);

        // Warm up.
        System.out.println("Warming up ...");
        runTest(factory, host, 256, 10, true, false);
        System.out.println("... done");
        System.out.println();
        rest();

        // Synchronous tests
        for (int messageSize: messageSizeTable) {
            for (int connections: connectionTable) {
                runTest(factory, host, messageSize, connections, false, true);
                rest();
            }
        }

        // Asynchronous tests - disabled temporarily because of too much memory consumption.
        //for (int messageSize: messageSizeTable) {
        //    for (int connections: connectionTable) {
        //        runTest(factory, host, messageSize, connections, true, true);
        //        rest();
        //    }
        //}

        executor.shutdownNow();
    }

    private static void rest() {
        for (int i = 0; i < 5; i ++) {
            System.gc();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Ignore - shouldn't happen.
            }
        }
    }

    public static void runTest(
            ChannelFactory factory, String host,
            final int messageSize, int connections, boolean asynchronous, boolean print) {

        if (print) {
            System.out.println(
                    "Host: " + host +
                    ", Message Size: " + messageSize +
                    ", Connections: " + connections +
                    ", Mode: " + (asynchronous? "ASYNC" : "SYNC"));
        }

        final long counterLimit =
            messageSize * (asynchronous? 5 : 1) *
            (long) (Constant.MESSAGE_COUNT * Math.log(connections * Math.E));

        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption(
                "child.receiveBufferSizePredictorFactory",
                new AdaptiveReceiveBufferSizePredictorFactory(
                        Constant.MIN_READ_BUFFER_SIZE,
                        Constant.INITIAL_READ_BUFFER_SIZE,
                        Constant.MAX_READ_BUFFER_SIZE)
                );

        if (asynchronous) {
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    ChannelPipeline p = Channels.pipeline();
                    p.addLast("handler", new AsynchronousEchoHandler(counterLimit, messageSize));
                    return p;
                }
            });
        } else {
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    ChannelPipeline p = Channels.pipeline();
                    p.addLast("handler", new SynchronousEchoHandler(counterLimit, messageSize));
                    return p;
                }
            });
        }

        long connectionStartTime = System.nanoTime();

        ChannelFuture[] fa = new ChannelFuture[connections];
        for (int i = 0; i < connections; i ++) {
            fa[i] = bootstrap.connect(new InetSocketAddress(host, Constant.PORT));
        }

        for (ChannelFuture f: fa) {
            f.awaitUninterruptibly();
            if (!f.isSuccess()) {
                f.getCause().printStackTrace();
                System.exit(0);
            }
        }

        long connectionEndTime = System.nanoTime();
        if (print) {
            System.out.format(
                    "* Acceptance Throughput: %10.0f Con/s (%.3f ms)%n",
                    connections * 1000000000.0 / (connectionEndTime - connectionStartTime),
                    (connectionEndTime - connectionStartTime) / 1000000.0);
        }

        // Reset all counters.
        counter.set(0L);
        for (ChannelFuture f: fa) {
            Channel channel = f.getChannel();
            EchoHandler handler = (EchoHandler) channel.getPipeline().getLast();
            handler.reset();
        }

        long startTime = System.currentTimeMillis();
        for (ChannelFuture f: fa) {
            Channel channel = f.getChannel();
            EchoHandler handler = (EchoHandler) channel.getPipeline().getLast();
            handler.start(channel);
        }

        int success = 0;
        int failure = 0;
        for (int i = 0; i < connections;) {
            try {
                String msg = msgs.poll(10, TimeUnit.SECONDS);
                if (msg != null) {
                    if (msg.equals("O")) {
                        success ++;
                    } else {
                        failure ++;
                    }
                    i ++;
                }
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        long endTime = System.currentTimeMillis();

        if (failure > 0) {
            System.out.println(failure + " failures.");
            System.exit(0);
        }

        if (print) {
            System.out.format(
                    "* Read/Write Throughput: %10.3f MiB/s (%.3f s)%n%n",
                    counter.get() * 1000.0 / 1048576.0 / (endTime - startTime),
                    (endTime - startTime) / 1000.0);
        }
    }

    private static interface EchoHandler {
        void reset();
        void start(Channel channel);
    }

    @ChannelPipelineCoverage("all")
    private static class SynchronousEchoHandler extends SimpleChannelHandler implements EchoHandler {

        private final long counterLimit;
        private final byte[] message;
        private volatile boolean failure;

        SynchronousEchoHandler(long counterLimit, int messageSize) {
            this.counterLimit = counterLimit;
            message = new byte[messageSize];
        }

        public void reset() {
            // Nothing to do.
        }

        public void start(Channel channel) {
            channel.write(ChannelBuffers.wrappedBuffer(message));
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            ChannelBuffer buf = (ChannelBuffer) e.getMessage();
            if (counter.addAndGet(buf.readableBytes()) >= counterLimit) {
                e.getChannel().close();
            } else {
                e.getChannel().write(buf);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            failure = true;
            e.getCause().printStackTrace();
            e.getChannel().close();
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            msgs.add(failure ? "X" : "O");
        }
    }

    @ChannelPipelineCoverage("one")
    private static class AsynchronousEchoHandler extends SimpleChannelHandler implements EchoHandler {

        private static final AtomicLong globalSentBytes = new AtomicLong();
        private final long counterLimit;
        private final byte[] message;
        private volatile boolean outboundDone;
        private volatile boolean failure;
        private long sentBytes;
        private long receivedBytes;

        AsynchronousEchoHandler(long counterLimit, int messageSize) {
            this.counterLimit = counterLimit;
            message = new byte[messageSize];
        }

        public void reset() {
            globalSentBytes.set(0);
            outboundDone = false;
            sentBytes = 0;
        }

        public void start(Channel channel) {
            channel.write(ChannelBuffers.wrappedBuffer(message));
            sentBytes += message.length;
            globalSentBytes.addAndGet(message.length);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            ChannelBuffer buf = (ChannelBuffer) e.getMessage();

            counter.addAndGet(buf.readableBytes());
            receivedBytes += buf.readableBytes();
            if (outboundDone && receivedBytes >= sentBytes) {
                e.getChannel().close();
            } else {
                generateTraffic(e.getChannel());
            }
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            if (!outboundDone) {
                generateTraffic(e.getChannel());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            failure = true;
            e.getCause().printStackTrace();
            e.getChannel().close();
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            msgs.add(failure ? "X" : "O");
        }

        private void generateTraffic(Channel channel) {
            while (channel.isWritable()) {
                channel.write(ChannelBuffers.wrappedBuffer(message));
                sentBytes += message.length;
                if (globalSentBytes.addAndGet(message.length) >= counterLimit) {
                    outboundDone = true;
                    break;
                }
            }
        }
    }
}
