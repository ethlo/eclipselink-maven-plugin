package com.ethlo.persistence.tools.eclipselink;

/*-
 * #%L
 * Eclipselink Maven Plugin
 * %%
 * Copyright (C) 2013 - 2017 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * #L%
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.SmartPersistenceUnitInfo;
import org.springframework.util.StringUtils;

/**
 * @author Morten Haraldsen
 */
@Mojo(requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES, name = "ddl", requiresProject = true)
public class EclipselinkDdlGenerationMojo extends AbstractMojo
{
    @Parameter(required = false)
    private String basePackage;

    @Parameter(required = false)
    private String[] basePackages;

    @Parameter(required = true)
    private String databaseProductName;

    @Parameter(required = false)
    private String databaseMajorVersion;

    @Parameter(required = false)
    private String databaseMinorVersion;

    @Parameter(defaultValue = "${project.build.outputDirectory}/ddl-create.sql")
    private File ddlCreateTargetFile;

    @Parameter(defaultValue = "${project.build.outputDirectory}/ddl-drop.sql")
    private File ddlDropTargetFile;

    @Parameter(defaultValue = "WARNING", property = "logLevel")
    private String logLevel;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException
    {
        setLogLevel(logLevel);
        
        final Thread thread = Thread.currentThread();
        final ClassLoader currentClassLoader = thread.getContextClassLoader();
        try
        {
            thread.setContextClassLoader(getClassLoader());
            generateSchema();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        finally
        {
            thread.setContextClassLoader(currentClassLoader);
        }

        getLog().info("Eclipselink DDL completed");
    }

    public void generateSchema() throws MojoFailureException
    {
        final Map<String, Object> cfg = buildCfg();
        String[] allBasePackages = this.getBasePackages();
        getLog().info("Using base packages " + StringUtils.arrayToDelimitedString(allBasePackages, ", "));
        final PersistenceProvider provider = new PersistenceProvider();
        final DefaultPersistenceUnitManager manager = new DefaultPersistenceUnitManager();
        manager.setDefaultPersistenceUnitRootLocation(null);
        manager.setDefaultPersistenceUnitName("default");
        manager.setPackagesToScan(allBasePackages);
        manager.setPersistenceXmlLocations(new String[0]);
        manager.afterPropertiesSet();

        final SmartPersistenceUnitInfo puInfo = (SmartPersistenceUnitInfo) manager.obtainDefaultPersistenceUnitInfo();
        puInfo.setPersistenceProviderPackageName(provider.getClass().getName());
        getLog().info("Entities found : " + puInfo.getManagedClassNames().size());
        getLog().debug("Managed class names:\n    * " + StringUtils.collectionToDelimitedString(puInfo.getManagedClassNames(), "\n    * "));
        puInfo.getProperties().putAll(cfg);
        provider.generateSchema(new DelegatingPuInfo(puInfo), cfg);
    }

    private Map<String, Object> buildCfg() throws MojoFailureException
    {
        final Map<String, Object> cfg = new TreeMap<>();
        
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_DATABASE_ACTION, PersistenceUnitProperties.SCHEMA_GENERATION_NONE_ACTION);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_ACTION, PersistenceUnitProperties.SCHEMA_GENERATION_DROP_AND_CREATE_ACTION);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_CREATE_SOURCE, PersistenceUnitProperties.SCHEMA_GENERATION_METADATA_SOURCE);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_DROP_SOURCE, PersistenceUnitProperties.SCHEMA_GENERATION_METADATA_SOURCE);
        try {
            cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_DROP_TARGET, new FileWriter(ddlDropTargetFile));
        } catch(IOException ioe) {
            throw new MojoFailureException("Error Writing to DDL Drop Target: '"+ddlDropTargetFile+"'", ioe);
        }
        try {
            cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_CREATE_TARGET, new FileWriter(ddlCreateTargetFile));
        } catch(IOException ioe) {
            throw new MojoFailureException("Error Writing to DDL Create Target: '"+ddlCreateTargetFile+"' Not Found!", ioe);
        }
        cfg.put(PersistenceUnitProperties.SCHEMA_DATABASE_PRODUCT_NAME, databaseProductName);
        cfg.put(PersistenceUnitProperties.WEAVING, "false");
        
        if (databaseMajorVersion != null)
        {
            cfg.put(PersistenceUnitProperties.SCHEMA_DATABASE_MAJOR_VERSION, databaseMajorVersion);
        }
        
        if (databaseMinorVersion != null)
        {
            cfg.put(PersistenceUnitProperties.SCHEMA_DATABASE_MINOR_VERSION, databaseMinorVersion);
        }
        
        return cfg;
    }

    public void setLogLevel(String logLevel)
    {
        java.util.logging.Level.parse(logLevel);
        this.logLevel = logLevel.toUpperCase();
    }

    private ClassLoader getClassLoader() throws MojoExecutionException
    {
        try
        {
            @SuppressWarnings("unchecked")
            final List<String> classpathElements = project.getCompileClasspathElements();
            getLog().debug("Classpath URLs: " + StringUtils.collectionToCommaDelimitedString(classpathElements));
            final List<URL> projectClasspathList = new ArrayList<URL>();
            for (String element : classpathElements)
            {
                try
                {
                    projectClasspathList.add(new File(element).toURI().toURL());
                }
                catch (MalformedURLException e)
                {
                    throw new MojoExecutionException(element + " is an invalid classpath element", e);
                }
            }
            return new URLClassLoader(projectClasspathList.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new MojoExecutionException("Dependency resolution failed", e);
        }
    }

    private String[] getBasePackages() throws MojoFailureException
    {
        List<String> allBasePackages = new ArrayList<>();
        if (basePackage == null && basePackages == null)
        {
            throw new MojoFailureException("<basePackage> or <basePackages> elements are mandatory");
        } 
        else if (basePackage != null && basePackages != null)
        {
            throw new MojoFailureException("<basePackage> and <basePackages> are mutually exclusive");
        }

        if (basePackage != null)
        {
            allBasePackages.add(basePackage);
        }

        if (basePackages != null)
        {
            if (basePackages.length == 0)
            {
                throw new MojoFailureException("No <basePackage> elements specified within <basePackages>");
            }
            allBasePackages.addAll(Arrays.asList(basePackages));
        }

        return StringUtils.toStringArray(allBasePackages);
    }

}
