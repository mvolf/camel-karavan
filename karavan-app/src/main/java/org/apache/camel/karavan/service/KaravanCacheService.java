/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.karavan.service;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
import org.apache.camel.karavan.model.*;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Default
@Singleton
public class KaravanCacheService {

    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final Map<String, ProjectFile> files = new ConcurrentHashMap<>();
    private final Map<String, DeploymentStatus> deploymentStatuses = new ConcurrentHashMap<>();
    private final Map<String, ContainerStatus> containerStatuses = new ConcurrentHashMap<>();
    private final Map<String, Boolean> transits = new ConcurrentHashMap<>();
    private final Map<String, ServiceStatus> serviceStatuses = new ConcurrentHashMap<>();
    private final Map<String, CamelStatus> camelStatuses = new ConcurrentHashMap<>();

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private static final Logger LOGGER = Logger.getLogger(KaravanCacheService.class.getName());

    public static final String DEFAULT_ENVIRONMENT = "dev";

    void start() {
        LOGGER.info("KaravanCacheService is started");
        ready.set(true);
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("KaravanCacheService is stopped");
        ready.set(false);
    }

    public List<Project> getProjects() {
        return new ArrayList<>(projects.values());
    }

    public void saveProject(Project project) {
        var key = GroupedKey.create(project.getProjectId(), DEFAULT_ENVIRONMENT, project.getProjectId());
        projects.put(key, project);
    }

    public List<ProjectFile> getProjectFiles(String projectId) {
        return files.values().stream().filter(pf -> Objects.equals(pf.getProjectId(), projectId)).toList();
    }

    public Map<String, ProjectFile> getProjectFilesMap(String projectId) {
        return files.entrySet().stream().filter(es -> !Objects.isNull(es.getValue()) && Objects.equals(es.getValue().getProjectId(), projectId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public ProjectFile getProjectFile(String projectId, String filename) {
        List<ProjectFile> list = files.values().stream().filter(pf -> Objects.equals(pf.getProjectId(), projectId) && Objects.equals(pf.getName(), filename)).toList();
        return !list.isEmpty() ? list.get(0) : null;
    }

    public List<ProjectFile> getProjectFilesByName(String filename) {
        return files.values().stream().filter(pf -> Objects.equals(pf.getName(), filename)).toList();
    }

    public void saveProjectFile(ProjectFile file) {
        files.put(GroupedKey.create(file.getProjectId(), DEFAULT_ENVIRONMENT, file.getName()), file);
    }

    public void saveProjectFiles(Map<String, ProjectFile> filesToSave) {
        long lastUpdate = Instant.now().toEpochMilli();
        filesToSave.forEach((groupedKey, projectFile) -> projectFile.setLastUpdate(lastUpdate));
        files.putAll(filesToSave);
    }

    public void deleteProject(String projectId) {
        projects.remove(GroupedKey.create(projectId, DEFAULT_ENVIRONMENT, projectId));
    }

    public void deleteProjectFile(String projectId, String filename) {
        files.remove(GroupedKey.create(projectId, DEFAULT_ENVIRONMENT, filename));
    }

    public Project getProject(String projectId) {
        return projects.get(GroupedKey.create(projectId, DEFAULT_ENVIRONMENT, projectId));
    }

    public DeploymentStatus getDeploymentStatus(String projectId, String environment) {
        return deploymentStatuses.get(GroupedKey.create(projectId, environment, projectId));
    }

    public void saveDeploymentStatus(DeploymentStatus status) {
        deploymentStatuses.put(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getProjectId()), status);
    }

    public void deleteDeploymentStatus(DeploymentStatus status) {
        deploymentStatuses.remove(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getProjectId()));
    }

    public List<DeploymentStatus> getDeploymentStatuses() {
        return new ArrayList<>(deploymentStatuses.values());
    }

    public List<DeploymentStatus> getDeploymentStatuses(String env) {
        return deploymentStatuses.values().stream().filter(pf -> Objects.equals(pf.getEnv(), env)).toList();
    }

    public void deleteAllDeploymentsStatuses() {
        deploymentStatuses.clear();
    }

    public void saveServiceStatus(ServiceStatus status) {
        serviceStatuses.put(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getProjectId()), status);
    }

    public void deleteServiceStatus(ServiceStatus status) {
        serviceStatuses.remove(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getProjectId()));
    }

    public List<ServiceStatus> getServiceStatuses() {
        return new ArrayList<>(serviceStatuses.values());
    }

    public List<Boolean> getTransits() {
        return new ArrayList<>(transits.values());
    }

    public Boolean getTransit(String projectId, String env, String containerName) {
        return transits.get(GroupedKey.create(projectId, env, containerName));
    }

    public void setTransit(String projectId, String env, String containerName) {
        transits.put(GroupedKey.create(projectId, env, containerName), true);
    }

    public List<ContainerStatus> getContainerStatuses() {
        return new ArrayList<>(containerStatuses.values());
    }

    public List<ContainerStatus> getContainerStatuses(String projectId, String env) {
        return containerStatuses.values().stream().filter(el -> Objects.equals(el.getProjectId(), projectId) && Objects.equals(el.getEnv(), env)).toList();
    }

    public ContainerStatus getContainerStatus(String projectId, String env, String containerName) {
        return getContainerStatus(GroupedKey.create(projectId, env, containerName));
    }

    public ContainerStatus getContainerStatus(String key) {
        return containerStatuses.get(key);
    }

    public ContainerStatus getDevModeContainerStatus(String projectId, String env) {
        return containerStatuses.get(GroupedKey.create(projectId, env, projectId));
    }

    public List<ContainerStatus> getContainerStatuses(String env) {
        return containerStatuses.values().stream().filter(el -> Objects.equals(el.getEnv(), env)).toList();
    }

    public List<ContainerStatus> getAllContainerStatuses() {
        return new ArrayList<>(containerStatuses.values());
    }

    public void saveContainerStatus(ContainerStatus status) {
        containerStatuses.put(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getContainerName()), status);
    }

    public void deleteContainerStatus(ContainerStatus status) {
        containerStatuses.remove(GroupedKey.create(status.getProjectId(), status.getEnv(), status.getContainerName()));
    }

    public void deleteAllContainersStatuses() {
        containerStatuses.clear();
    }

    public void deleteContainerStatus(String projectId, String env, String containerName) {
        containerStatuses.remove(GroupedKey.create(projectId, env, containerName));
    }

    public CamelStatus getCamelStatus(String projectId, String env, String containerName) {
        var key = GroupedKey.create(projectId, env, containerName);
        return camelStatuses.get(key);
    }

    public CamelStatus getCamelStatus(String key) {
        return camelStatuses.get(key);
    }

    public List<CamelStatus> getCamelStatusesByEnv(CamelStatusValue.Name name) {
        return camelStatuses.values().stream().map(cs -> {
            var values = cs.getStatuses();
            cs.setStatuses(values.stream().filter(v -> Objects.equals(v.getName(), name)).toList());
            return cs;
        }).toList();
    }

    public List<CamelStatus> getCamelStatusesByProjectAndEnv(String projectId, String env) {
        return camelStatuses.values().stream().filter(el -> Objects.equals(el.getProjectId(), projectId) && Objects.equals(el.getEnv(), env)).toList();
    }

    public void saveCamelStatus(CamelStatus status) {
        var key = GroupedKey.create(status.getProjectId(), status.getEnv(), status.getContainerName());
        camelStatuses.put(key, status);
    }

    public void deleteCamelStatus(String projectId, String name, String env) {
        var key = GroupedKey.create(projectId, env, name);
        camelStatuses.remove(key);
    }

    public void deleteCamelStatuses(String projectId, String env) {
        camelStatuses.values().stream().filter(el -> Objects.equals(el.getProjectId(), projectId) && Objects.equals(el.getEnv(), env))
                .forEach(s -> {
                    var key = GroupedKey.create(projectId, env, s.getContainerName());
                    camelStatuses.remove(key);
                });
    }

    public void deleteAllCamelStatuses() {
        camelStatuses.clear();
    }

    public List<ContainerStatus> getLoadedDevModeStatuses() {
        return containerStatuses.values().stream().filter(el -> Objects.equals(el.getType(), ContainerStatus.ContainerType.devmode) && Objects.equals(el.getCodeLoaded(), true)).toList();
    }

    public List<ContainerStatus> getDevModeStatuses() {
        return containerStatuses.values().stream().filter(el -> Objects.equals(el.getType(), ContainerStatus.ContainerType.devmode)).toList();
    }

    public List<ContainerStatus> getContainerStatusByEnv(String env) {
        return containerStatuses.values().stream().filter(el -> Objects.equals(el.getEnv(), env)).toList();
    }

    public void clearAllStatuses() {
        deploymentStatuses.clear();
        containerStatuses.clear();
        camelStatuses.clear();
    }

    private String getResourceFile(String path) {
        try {
            InputStream inputStream = KaravanCacheService.class.getResourceAsStream(path);
            return new BufferedReader(new InputStreamReader(inputStream))
                    .lines().collect(Collectors.joining(System.getProperty("line.separator")));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isReady() {
        return ready.get();
    }
}
