/*
 * Copyright (c) 2014-2016 dCentralizedSystems, LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.dcentralized.core.common.serialization;

import com.esotericsoftware.kryo.io.Output;

/**
 * An Output the knows the root of the serialization call.
 */
public final class OutputWithRoot extends Output {
    private Object root;

    public OutputWithRoot(byte[] buffer, int maxSize, Object o) {
        super(buffer, maxSize);
        this.root = o;
    }

    public Object getRoot() {
        return this.root;
    }
}
