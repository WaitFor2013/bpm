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

package org.flowable.engine.test.api.identity;

import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.test.impl.CustomConfigurationFlowableTestCase;
import org.flowable.idm.api.User;
import org.junit.jupiter.api.Test;

public class ChangePasswordIdentityServiceTest extends CustomConfigurationFlowableTestCase {

    public ChangePasswordIdentityServiceTest() {
        super(ChangePasswordIdentityServiceTest.class.getName());
    }

    @Override
    protected void configureConfiguration(ProcessEngineConfigurationImpl processEngineConfiguration) {
        processEngineConfiguration.setIdmEngineConfigurator(
            new PasswordEncoderIdmEngineConfigurator()
        );
    }


    @Test
    public void testChangePassword() {
        try {
            User user = identityService.newUser("johndoe");
            user.setPassword("xxx");
            identityService.saveUser(user);

            user = identityService.createUserQuery().userId("johndoe").list().get(0);
            user.setFirstName("John Doe");
            identityService.saveUser(user);
            User johndoe = identityService.createUserQuery().userId("johndoe").list().get(0);
            assertFalse(johndoe.getPassword().equals("xxx"));
            assertEquals("John Doe", johndoe.getFirstName());
            assertTrue(identityService.checkPassword("johndoe", "xxx"));

            user = identityService.createUserQuery().userId("johndoe").list().get(0);
            user.setPassword("yyy");
            identityService.saveUser(user);
            assertTrue(identityService.checkPassword("johndoe", "xxx"));

            user = identityService.createUserQuery().userId("johndoe").list().get(0);
            user.setPassword("yyy");
            identityService.updateUserPassword(user);
            assertTrue(identityService.checkPassword("johndoe", "yyy"));

        } finally {
            identityService.deleteUser("johndoe");
        }
    }

}
