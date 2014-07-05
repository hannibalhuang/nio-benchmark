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

import static org.jboss.xnio.Buffers.clear;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.benchmark.echo.Constant;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.TcpChannel;
import org.jboss.xnio.nio.NioXnio;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author David M. Lloyd (david.lloyd@redhat.com)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 1691 $, $Date: 2009-08-28 15:47:35 +0800 (星期五, 28 八月 2009) $
 *
 */
public class XNIO {

    private static final int WRITE_QUEUE_DEPTH = 4;

    private XNIO() {
    }

    public static void main(String[] args) throws Exception {
        final boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");
        final int nProcessors = Runtime.getRuntime().availableProcessors();

        BlockingQueue<Runnable> taskQueue =
            new ArrayBlockingQueue<Runnable>(10240);

        Xnio xnio;
        if (threadPoolDisabled) {
            xnio = NioXnio.create(IoUtils.directExecutor(), nProcessors, nProcessors, 1);
        } else {
            xnio = NioXnio.create(new ThreadPoolExecutor(
                    Constant.THREAD_POOL_SIZE, Constant.THREAD_POOL_SIZE,
                    0L, TimeUnit.MILLISECONDS, taskQueue,
                    new ThreadPoolExecutor.CallerRunsPolicy()),
                    nProcessors, nProcessors, 1);
        }

        ConfigurableFactory<Closeable> tcpServer = xnio.createTcpServer(
                new EchoHandlerFactory(), new InetSocketAddress(Constant.PORT));

        tcpServer.create();

        System.out.println("XNIO EchoServer is ready to serve at port " + Constant.PORT + ".");
        System.out.println("Enter 'ant benchmark' on the client side to begin.");
        System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
    }

    private static final ByteBuffer POISON_PILL = ByteBuffer.allocate(0);

    private static class EchoHandlerFactory implements IoHandlerFactory<TcpChannel> {

        public IoHandler<? super TcpChannel> createHandler() {
            return new EchoHandler();
        }
    }

    private static class EchoHandler implements IoHandler<TcpChannel> {
        private final BufferAllocator<ByteBuffer> allocator = new QueueAllocator();
        private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<ByteBuffer>();
        private final AtomicInteger queueDepth = new AtomicInteger();

        public void handleOpened(final TcpChannel channel) {
            channel.resumeReads();
        }

        public void handleReadable(final TcpChannel channel) {
            for (;;) {
                final ByteBuffer buffer = allocator.allocate();
                try {
                    int t = 0;
                    while (buffer.hasRemaining()) {
                        final int res = channel.read(buffer);
                        if (res == -1) {
                            if (t == 0) {
                                writeQueue.add(POISON_PILL);
                                if (queueDepth.getAndIncrement() == 0) {
                                    // no writer is running
                                    IoUtils.safeClose(channel);
                                }
                                allocator.free(buffer);
                                return;
                            } else {
                                break;
                            }
                        } else if (res == 0) {
                            if (t == 0) {
                                allocator.free(buffer);
                                channel.resumeReads();
                                return;
                            } else {
                                break;
                            }
                        } else {
                            t += res;
                        }
                    }
                    buffer.flip();
                    writeQueue.add(buffer);
                    final int old = queueDepth.getAndIncrement();
                    if (old == 0) {
                        channel.resumeWrites();
                    } else if (old == WRITE_QUEUE_DEPTH) {
                        // new value is WQD + 1
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    IoUtils.safeClose(channel);
                    return;
                }
            }
        }

        public void handleWritable(final TcpChannel channel) {
            for (;;) {
                final ByteBuffer buffer;
                try {
                    buffer = writeQueue.peek();
                    if (buffer == null) {
                        // spurious wakeup
                        return;
                    }
                    if (buffer == POISON_PILL) {
                        // done
                        channel.close();
                        return;
                    }
                    while (buffer.hasRemaining()) {
                        final int res = channel.write(buffer);
                        if (res == 0) {
                            channel.resumeWrites();
                            return;
                        }
                    }
                    allocator.free(buffer);
                    writeQueue.remove();
                    final int newval = queueDepth.decrementAndGet();
                    if (newval == 0) {
                        return;
                    } else if (newval == WRITE_QUEUE_DEPTH) {
                        // old value was WQD + 1
                        channel.resumeReads();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    IoUtils.safeClose(channel);
                    return;
                }
            }
        }

        public void handleClosed(final TcpChannel channel) {
            // NOOP
        }
    }

    private static final class QueueAllocator implements BufferAllocator<ByteBuffer> {

        private final Queue<ByteBuffer> freeQueue = new ArrayBlockingQueue<ByteBuffer>(30);

        public ByteBuffer allocate() {
            final ByteBuffer buffer = freeQueue.poll();
            return buffer == null ? ByteBuffer.allocate(Constant.MAX_READ_BUFFER_SIZE) : buffer;
        }

        public void free(final ByteBuffer buffer) {
            freeQueue.add(clear(buffer));
        }
    }
}
