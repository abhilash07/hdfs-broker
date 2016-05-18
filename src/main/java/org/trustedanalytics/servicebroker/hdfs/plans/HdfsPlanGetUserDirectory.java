/**
 * Copyright (c) 2015 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trustedanalytics.servicebroker.hdfs.plans;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.ImmutableMap;
import org.cloudfoundry.community.servicebroker.exception.ServiceBrokerException;
import org.cloudfoundry.community.servicebroker.exception.ServiceInstanceExistsException;
import org.cloudfoundry.community.servicebroker.model.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.trustedanalytics.servicebroker.framework.service.ServicePlanDefinition;
import org.trustedanalytics.servicebroker.framework.store.CredentialsStore;
import org.trustedanalytics.servicebroker.hdfs.path.HdfsBrokerInstancePath;
import org.trustedanalytics.servicebroker.hdfs.plans.binding.HdfsSpecificOrgBindingOperations;
import org.trustedanalytics.servicebroker.hdfs.plans.provisioning.HdfsDirectoryProvisioningOperations;
import org.trustedanalytics.servicebroker.hdfs.users.GroupMappingOperations;

@Component("get-user-directory")
class HdfsPlanGetUserDirectory implements ServicePlanDefinition {

  private static final Logger LOGGER = LoggerFactory.getLogger(HdfsPlanCreateUserDirectory.class);
  private static final String USER = "user";
  private static final String PASSWORD = "password";
  private static final String URI_KEY = "uri";

  private final HdfsDirectoryProvisioningOperations hdfsOperations;
  private final HdfsSpecificOrgBindingOperations bindingOperations;
  private final CredentialsStore credentialsStore;

  @Autowired
  public HdfsPlanGetUserDirectory(HdfsDirectoryProvisioningOperations hdfsOperations,
      HdfsSpecificOrgBindingOperations bindingOperations,
      GroupMappingOperations groupMappingOperations, CredentialsStore zookeeperCredentialsStore) {
    this.hdfsOperations = hdfsOperations;
    this.bindingOperations = bindingOperations;
    this.credentialsStore = zookeeperCredentialsStore;
  }

  @Override
  public void provision(ServiceInstance serviceInstance, Optional<Map<String, Object>> parameters)
      throws ServiceInstanceExistsException, ServiceBrokerException {
    if (!isMapNotNullAndNotEmpty(parameters)) {
      throw new ServiceBrokerException("This plan require parametere uri");
    }
    String uri =
        getParameterUri(parameters.get(), URI_KEY).orElseThrow(
            () -> new ServiceBrokerException("No required parameter uri")).toString();
    LOGGER.info("Detected parameter path: " + uri);

    HdfsBrokerInstancePath instance = HdfsBrokerInstancePath.createInstance(uri);
    credentialsStore.save(
        ImmutableMap.<String, Object>builder()
            .putAll(credentialsStore.get(instance.getInstanceId())).put(URI_KEY, uri).build(),
        UUID.fromString(serviceInstance.getServiceInstanceId()));
  }

  @Override
  public Map<String, Object> bind(ServiceInstance serviceInstance) throws ServiceBrokerException {
    UUID instanceId = UUID.fromString(serviceInstance.getServiceInstanceId());
    UUID orgId = UUID.fromString(serviceInstance.getOrganizationGuid());
    Map<String, Object> configurationMap =
        bindingOperations.createCredentialsMap(instanceId, orgId);
    Map<String, Object> credentials = credentialsStore.get(instanceId);

    if (getParameterUri(credentials, URI_KEY).isPresent()) {
      configurationMap.remove(URI_KEY);
    }
    return new HashMap<String, Object>() {
      {
        putAll(configurationMap);
        putAll(credentials);
      }
    };
  }

  private boolean isMapNotNullAndNotEmpty(Optional<Map<String, Object>> map) {
    return map.isPresent() && !map.get().isEmpty();
  }

  private Optional<Object> getParameterUri(Map<String, Object> parameters, String key) {
    return Optional.ofNullable(parameters.get(key));
  }
}
