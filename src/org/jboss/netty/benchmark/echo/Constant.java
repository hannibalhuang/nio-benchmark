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
package org.jboss.netty.benchmark.echo;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 2109 $, $Date: 2010-01-29 00:10:01 +0800 (星期五, 29 一月 2010) $
 *
 */
public class Constant {
    /**
     * 8000 + 7 (the default ECHO protocol port)
     */
    public static final int PORT = 8007;
    public static final int MIN_READ_BUFFER_SIZE = 64;
    public static final int INITIAL_READ_BUFFER_SIZE = 16384;
    public static final int MAX_READ_BUFFER_SIZE = 65536;
    public static final int THREAD_POOL_SIZE = 16;
    public static final int MESSAGE_COUNT = 500000;
    public static final int CHANNEL_MEMORY_LIMIT = MAX_READ_BUFFER_SIZE * 2;
    public static final long GLOBAL_MEMORY_LIMIT = Runtime.getRuntime().maxMemory() / 3;
}
