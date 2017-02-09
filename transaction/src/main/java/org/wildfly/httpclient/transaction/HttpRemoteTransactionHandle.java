/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.httpclient.transaction;

import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class HttpRemoteTransactionHandle implements SimpleTransactionControl {

    private final HttpTargetContext targetContext;
    private final AtomicInteger statusRef = new AtomicInteger(Status.STATUS_ACTIVE);
    private final int id;

    HttpRemoteTransactionHandle(final int id, final HttpTargetContext targetContext) {
        this.id = id;
        this.targetContext = targetContext;
    }

    public int getId() {
        return id;
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {

    }

    @Override
    public void rollback() throws SecurityException, SystemException {

    }

    @Override
    public void setRollbackOnly() throws SystemException {

    }

    @Override
    public void safeRollback() {

    }

    @Override
    public <T> T getProviderInterface(Class<T> aClass) {
        return null;
    }
}
