/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.eventsubscription.service.impl.util;

import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.db.DbSqlSession;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.eventsubscription.service.EventSubscriptionServiceConfiguration;
import org.flowable.eventsubscription.service.impl.persistence.entity.EventSubscriptionEntityManager;

public class CommandContextUtil {

    public static EventSubscriptionServiceConfiguration getEventSubscriptionServiceConfiguration() {
        return getEventSubscriptionServiceConfiguration(getCommandContext());
    }
    
    public static EventSubscriptionServiceConfiguration getEventSubscriptionServiceConfiguration(CommandContext commandContext) {
        if (commandContext != null) {
            return (EventSubscriptionServiceConfiguration) commandContext.getCurrentEngineConfiguration()
                            .getServiceConfigurations().get(EngineConfigurationConstants.KEY_EVENT_SUBSCRIPTION_SERVICE_CONFIG);
        }
        return null;
    }
    
    public static DbSqlSession getDbSqlSession() {
        return getDbSqlSession(getCommandContext());
    }
    
    public static DbSqlSession getDbSqlSession(CommandContext commandContext) {
        return commandContext.getSession(DbSqlSession.class);
    }
    
    public static EventSubscriptionEntityManager getEventSubscriptionEntityManager() {
        return getEventSubscriptionEntityManager(getCommandContext());
    }
    
    public static EventSubscriptionEntityManager getEventSubscriptionEntityManager(CommandContext commandContext) {
        return getEventSubscriptionServiceConfiguration(commandContext).getEventSubscriptionEntityManager();
    }
    
    public static CommandContext getCommandContext() {
        return Context.getCommandContext();
    }

}
