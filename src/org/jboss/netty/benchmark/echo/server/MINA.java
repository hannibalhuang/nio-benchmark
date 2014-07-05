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

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jboss.netty.benchmark.echo.Constant;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 1691 $, $Date: 2009-08-28 15:47:35 +0800 (星期五, 28 八月 2009) $
 *
 */
public class MINA {

    public static void main(String[] args) throws Exception {
        boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");

        SocketAcceptor acceptor = new NioSocketAcceptor(Runtime.getRuntime().availableProcessors());
        acceptor.getSessionConfig().setMinReadBufferSize(Constant.MIN_READ_BUFFER_SIZE);
        acceptor.getSessionConfig().setReadBufferSize(Constant.INITIAL_READ_BUFFER_SIZE);
        acceptor.getSessionConfig().setMaxReadBufferSize(Constant.MAX_READ_BUFFER_SIZE);
        acceptor.getSessionConfig().setThroughputCalculationInterval(0);
        acceptor.getSessionConfig().setTcpNoDelay(true);
        acceptor.setDefaultLocalAddress(new InetSocketAddress(Constant.PORT));

        if (!threadPoolDisabled) {
            // Throttling has been disabled because it causes a dead lock.
            // Also, it doesn't have per-channel memory limit.
            acceptor.getFilterChain().addLast(
                    "executor",
                    new ExecutorFilter(
                            Constant.THREAD_POOL_SIZE, Constant.THREAD_POOL_SIZE));
        }

        acceptor.setHandler(new EchoHandler());
        acceptor.bind();

        System.out.println("MINA EchoServer is ready to serve at port " + Constant.PORT + ".");
        System.out.println("Enter 'ant benchmark' on the client side to begin.");
        System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
    }

    private static class EchoHandler extends IoHandlerAdapter {

        EchoHandler() {
            super();
        }

        @Override
        public void messageReceived(IoSession session, Object message)
                throws Exception {
            session.write(((IoBuffer) message).duplicate());
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable cause)
                throws Exception {
            session.close();
        }
    }
}
