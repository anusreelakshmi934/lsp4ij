/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerFileAssociation;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI settings to configure a given language server:
 *
 * <ul>
 *     <li>Debug port</li>
 *     <li>Suspend and wait for a debugger</li>
 * </ul>
 */
public class LanguageServerConfigurable extends NamedConfigurable<LanguageServerDefinition> {

    private final LanguageServerDefinition languageServerDefinition;
    private final Project project;

    private LanguageServerView myView;

    public LanguageServerConfigurable(LanguageServerDefinition languageServerDefinition, Runnable updater, Project project) {
        super(languageServerDefinition instanceof UserDefinedLanguageServerDefinition, updater);
        this.languageServerDefinition = languageServerDefinition;
        this.project = project;
    }

    @Override
    public void setDisplayName(String name) {
        // Do nothing: the language server name is not editable.
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition launchConfiguration) {
            launchConfiguration.setName(name);
        }
    }

    @Override
    public LanguageServerDefinition getEditableObject() {
        return languageServerDefinition;
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new LanguageServerView(languageServerDefinition);
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return languageServerDefinition.getDisplayName();
    }

    @Override
    public @Nullable Icon getIcon(boolean expanded) {
        return languageServerDefinition.getIcon();
    }

    @Override
    public boolean isModified() {
        String languageServerId = languageServerDefinition.getId();
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
            if (settings == null) {
                return true;
            }
            return !(Objects.equals(getDisplayName(), settings.getServerName())
                    && Objects.equals(myView.getCommandLine(), settings.getCommandLine())
                    && Objects.equals(myView.getMappings(), settings.getMappings())
                    && Objects.equals(myView.getConfigurationContent(), settings.getConfigurationContent())
                    && Objects.equals(myView.getInitializationOptionsContent(), settings.getInitializationOptionsContent())
            );
        }
        com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        if (settings == null) {
            return true;
        }
        return !(myView.getDebugPort().equals(settings.getDebugPort())
                && myView.isDebugSuspend() == settings.isDebugSuspend()
                && myView.getServerTrace() == settings.getServerTrace()
                && myView.getReportErrorKind() == settings.getReportErrorKind()
        );
    }

    @Override
    public void apply() throws ConfigurationException {
        String languageServerId = languageServerDefinition.getId();
        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition launch) {
            LanguageServersRegistry.getInstance().updateServerDefinition(
                    launch,
                    getDisplayName(),
                    myView.getCommandLine(),
                    myView.getMappings(),
                    myView.getConfigurationContent(),
                    myView.getInitializationOptionsContent());
        } else {
            com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = new com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings();
            settings.setDebugPort(myView.getDebugPort());
            settings.setDebugSuspend(myView.isDebugSuspend());
            settings.setServerTrace(myView.getServerTrace());
            settings.setReportErrorKind(myView.getReportErrorKind());
            com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project).setLanguageServerSettings(languageServerId, settings);
        }
    }

    @Override
    public void reset() {
        String languageServerId = languageServerDefinition.getId();

        // Commons settings (user defined language server + extension point)
        com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        final ErrorReportingKind errorReportingKind = settings != null && settings.getReportErrorKind() != null ? settings.getReportErrorKind() :  ErrorReportingKind.as_notification;
        final ServerTrace serverTrace = settings != null && settings.getServerTrace() != null ? settings.getServerTrace() :  ServerTrace.off;
        myView.setReportErrorKind(errorReportingKind);
        myView.setServerTrace(serverTrace);

        if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
            // User defined language server
            UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings userDefinedLanguageServerSettings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
            if (userDefinedLanguageServerSettings != null) {
                myView.setCommandLine(userDefinedLanguageServerSettings.getCommandLine());
                myView.setConfigurationContent(userDefinedLanguageServerSettings.getConfigurationContent());
                myView.setInitializationOptionsContent(userDefinedLanguageServerSettings.getInitializationOptionsContent());

                List<ServerMappingSettings> languageMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                        .collect(Collectors.toList());
                myView.setLanguageMappings(languageMappings);

                List<ServerMappingSettings> fileTypeMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                        .collect(Collectors.toList());
                myView.setFileTypeMappings(fileTypeMappings);

                List<ServerMappingSettings> fileNamePatternMappings = userDefinedLanguageServerSettings.getMappings()
                        .stream()
                        .filter(mapping -> mapping.getFileNamePatterns() != null)
                        .collect(Collectors.toList());
                myView.setFileNamePatternMappings(fileNamePatternMappings);
            }
        } else {
            // Language server from extension point
            if (settings != null) {
                myView.setDebugPort(settings.getDebugPort());
                myView.setDebugSuspend(settings.isDebugSuspend());
            }
            List<LanguageServerFileAssociation> mappings = LanguageServersRegistry.getInstance().findLanguageServerDefinitionFor(languageServerId);
            List<ServerMappingSettings> languageMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getLanguage() != null)
                    .map(mapping -> {
                        Language language = mapping.getLanguage();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createLanguageMappingSettings(language.getID(), languageId);
                    })
                    .collect(Collectors.toList());
            myView.setLanguageMappings(languageMappings);

            List<ServerMappingSettings> fileTypeMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getFileType() != null)
                    .map(mapping -> {
                        FileType fileType = mapping.getFileType();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createFileTypeMappingSettings(fileType.getName(), languageId);
                    })
                    .collect(Collectors.toList());
            myView.setFileTypeMappings(fileTypeMappings);

            List<ServerMappingSettings> fileNamePatternMappings = mappings
                    .stream()
                    .filter(mapping -> mapping.getFileNameMatchers() != null)
                    .map(mapping -> {
                        List<FileNameMatcher> matchers = mapping.getFileNameMatchers();
                        String languageId = mapping.getLanguageId();
                        return ServerMappingSettings.createFileNamePatternsMappingSettings(matchers.
                                stream()
                                .map(FileNameMatcher::getPresentableString)
                                .toList(), languageId);
                    })
                    .collect(Collectors.toList());
            myView.setFileNamePatternMappings(fileNamePatternMappings);
        }
    }

    @Override
    public void disposeUIResources() {
        if (myView != null) Disposer.dispose(myView);
    }
}
