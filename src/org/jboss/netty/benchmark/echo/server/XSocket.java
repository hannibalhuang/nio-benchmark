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
import java.nio.ByteBuffer;

import org.jboss.netty.benchmark.echo.Constant;
import org.xsocket.Execution;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 1691 $, $Date: 2009-08-28 15:47:35 +0800 (星期五, 28 八月 2009) $
 *
 */
public class XSocket {

    public static void main(String[] args) throws Exception {
        final boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");

        System.setProperty("org.xsocket.connection.server.workerpoolSize", "" + Constant.THREAD_POOL_SIZE);
        System.setProperty("org.xsocket.connection.dispatcher.initialCount", "" + Runtime.getRuntime().availableProcessors());
        System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.minSize", "" + Constant.MIN_READ_BUFFER_SIZE);
        System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.size", "" + Constant.MAX_READ_BUFFER_SIZE);

        // Using direct buffers causes too much memory consumption.
        System.setProperty("org.xsocket.connection.server.readbuffer.usedirect", "false");

        // Set per-channel memory limit only - xSocket doesn't have global limit.
        System.setProperty("org.xsocket.connection.server.readbuffer.defaultMaxReadBufferThreshold", "" + Constant.CHANNEL_MEMORY_LIMIT);

        final IServer srv;

        if (threadPoolDisabled) {
            srv = new Server(Constant.PORT, new NonthreadedEchoHandler());
        } else {
            srv = new Server(Constant.PORT, new MultithreadedEchoHandler());
        }
        srv.setFlushMode(FlushMode.ASYNC);

        new Thread() {
            @Override
            public void run() {
                while (!srv.isOpen()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }

                System.out.println("xSocket EchoServer is ready to serve at port " + Constant.PORT + ".");
                System.out.println("Enter 'ant benchmark' on the client side to begin.");
                System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
            }
        }.start();

        srv.run();
    }

    private static class EchoHandler implements IConnectHandler, IDataHandler {

        protected EchoHandler() {
            super();
        }

        public boolean onData(INonBlockingConnection nbc) throws IOException {
            int size = nbc.available();
            if (size < 0) {
                return true;
            }

            ByteBuffer[] buf = nbc.readByteBufferByLength(size);
            nbc.write(buf);
            return true;
        }

        public boolean onConnect(INonBlockingConnection nbc) throws IOException {
            nbc.setOption("IPPROTO_TCP.TCP_NODELAY", true);
            return true;
        }
    }

    @Execution(Execution.NONTHREADED)
    private static class NonthreadedEchoHandler extends EchoHandler {
        NonthreadedEchoHandler() {
            super();
        }
    }


    @Execution(Execution.MULTITHREADED)
    private static class MultithreadedEchoHandler extends EchoHandler {
        MultithreadedEchoHandler() {
            super();
        }
    }
}
