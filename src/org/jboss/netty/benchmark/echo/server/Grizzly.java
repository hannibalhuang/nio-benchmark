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

import org.jboss.netty.benchmark.echo.Constant;

import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultPipeline;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 1691 $, $Date: 2009-08-28 15:47:35 +0800 (星期五, 28 八月 2009) $
 *
 */
public class Grizzly {

    public static void main(String[] args) throws Exception {
        final boolean threadPoolDisabled = args.length > 0 && args[0].equals("nothreadpool");

        if (threadPoolDisabled) {
            System.err.println("'nothreadpool' option is not supported in Grizzly.");
            return;
        }

        // XXX I couldn't set the memory limitation because I couldn't find
        // a property related with memory limitation.  Perhaps a user needs
        // to implement one by his or her own?

        ByteBufferFactory.capacity = Constant.MAX_READ_BUFFER_SIZE;

        TCPSelectorHandler selector = new TCPSelectorHandler();
        selector.setPort(Constant.PORT);
        selector.setTcpNoDelay(true);

        // TODO Need to write a new pipeline to support nothreadpool mode.
        DefaultPipeline pipeline = new DefaultPipeline();

        // Another proposed change by JFA - Grizzly doesn't need many worker
        // threads, according to him.
        pipeline.setMaxThreads(Runtime.getRuntime().availableProcessors());
        pipeline.setMinThreads(Runtime.getRuntime().availableProcessors());

        // Seems like a single buffer is reused again and again per channel.
        pipeline.setInitialByteBufferSize(Constant.MAX_READ_BUFFER_SIZE);
        pipeline.setByteBufferType(ByteBufferType.HEAP_VIEW);

        ProtocolChainInstanceHandler pciHandler =
            new ProtocolChainInstanceHandler() {

            final private ProtocolChain protocolChain = new DefaultProtocolChain();

            public ProtocolChain poll() {
                return protocolChain;
            }

            public boolean offer(ProtocolChain instance) {
                return true;
            }
        };

        final Controller controller = new Controller();
        controller.setReadThreadsCount(Runtime.getRuntime().availableProcessors());
        controller.addSelectorHandler(selector);
        controller.setHandleReadWriteConcurrently(true);
        controller.setPipeline(pipeline);
        controller.setProtocolChainInstanceHandler(pciHandler);

        ProtocolChain protocolChain = pciHandler.poll();
        protocolChain.addFilter(new ReadFilter());

        // Changed to EchoFilter as advised by JFA - EchoFilter performs better
        // than EchoAsyncWriteQueueFilter.
        protocolChain.addFilter(new EchoFilter());

        new Thread() {
            @Override
            public void run() {
                while (!controller.isStarted()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }

                System.out.println("Grizzly EchoServer is ready to serve at port " + Constant.PORT + ".");
                System.out.println("Enter 'ant benchmark' on the client side to begin.");
                System.out.println("Thread pool: " + (threadPoolDisabled? "DISABLED" : "ENABLED"));
            }
        }.start();

        controller.start();
    }
}
