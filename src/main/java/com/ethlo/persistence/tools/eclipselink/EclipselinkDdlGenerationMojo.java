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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

import ee.jakarta.persistence.Persistence;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

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

    @Parameter(defaultValue = "file://${project.build.outputDirectory}/ddl.sql")
    private String ddlTargetFile;

    @Parameter(defaultValue = "file://${project.build.outputDirectory}/ddl-drop.sql")
    private String ddlDropTargetFile;

    @Parameter(defaultValue = "WARNING", property = "logLevel")
    private String logLevel;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Valid options 'create', 'drop', 'drop-and-create'
     */
    @Parameter
    private String action = PersistenceUnitProperties.SCHEMA_GENERATION_DROP_AND_CREATE_ACTION;

    @Parameter(defaultValue = "false", property = "eclipselink.ddl.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException
    {
        setLogLevel(logLevel);
        if (this.skip)
        {
            getLog().info("Skipping EclipseLink DDL by request");
            return;
        }

        // 1. Setup a temp directory for our dynamic XML
        File ddlMetaDir = new File(project.getBuild().getDirectory(), "ddl-meta");

        final Thread thread = Thread.currentThread();
        final ClassLoader currentClassLoader = thread.getContextClassLoader();

        // 2. Build classloader injecting our temp directory
        try (URLClassLoader pluginClassLoader = getClassLoader(ddlMetaDir))
        {
            thread.setContextClassLoader(pluginClassLoader);
            generateSchema(ddlMetaDir, pluginClassLoader);
            getLog().info("Eclipselink DDL completed");
        }
        catch (Exception e)
        {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        finally
        {
            thread.setContextClassLoader(currentClassLoader);
        }
    }

    public void generateSchema(File ddlMetaDir, URLClassLoader classLoader) throws MojoFailureException, MojoExecutionException
    {
        final Map<String, Object> cfg = buildCfg();
        String[] allBasePackages = this.getBasePackages();
        getLog().info("Using base packages " + String.join(", ", allBasePackages));

        // 1. Scan for entities using ClassGraph
        final Set<String> entityClasses = findEntities(allBasePackages, classLoader.getURLs());
        getLog().info("Entities found : " + entityClasses.size());
        getLog().debug("Managed class names:\n    * " + String.join("\n    * ", entityClasses));

        // 2. Generate ad-hoc persistence.xml
        File dummyXml = new File(ddlMetaDir, "META-INF/persistence.xml");
        Persistence doc = PersistenceXmlHelper.createXml("ddl-pu");
        PersistenceXmlHelper.appendClasses(doc, entityClasses);
        PersistenceXmlHelper.outputXml(doc, dummyXml.toPath());

        // 3. Delegate to Standard JPA Generation
        jakarta.persistence.Persistence.generateSchema("ddl-pu", cfg);
    }

    private Map<String, Object> buildCfg()
    {
        final Map<String, Object> cfg = new TreeMap<>();

        // No action towards the database
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_DATABASE_ACTION, PersistenceUnitProperties.SCHEMA_GENERATION_NONE_ACTION);

        // Create scripts
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_ACTION, action);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_CREATE_SOURCE, PersistenceUnitProperties.SCHEMA_GENERATION_METADATA_SOURCE);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_DROP_SOURCE, PersistenceUnitProperties.SCHEMA_GENERATION_METADATA_SOURCE);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_CREATE_TARGET, ddlTargetFile);
        cfg.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_DROP_TARGET, ddlDropTargetFile);
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

    private URLClassLoader getClassLoader(File extraDir) throws MojoExecutionException
    {
        try
        {
            final List<String> classpathElements = project.getCompileClasspathElements();
            final List<URL> projectClasspathList = getUrls(extraDir, classpathElements);
            return new URLClassLoader(projectClasspathList.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        }
        catch (DependencyResolutionRequiredException | MalformedURLException e)
        {
            throw new MojoExecutionException("Dependency resolution failed", e);
        }
    }

    private static List<URL> getUrls(File extraDir, List<String> classpathElements) throws MalformedURLException, MojoExecutionException
    {
        final List<URL> projectClasspathList = new ArrayList<>();

        // Inject our temp dir first so EclipseLink finds our dynamic persistence.xml
        projectClasspathList.add(extraDir.toURI().toURL());

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
        return projectClasspathList;
    }

    private Set<String> findEntities(String[] allBasePackages, final URL[] classPath)
    {
        final Set<String> result = new TreeSet<>();
        try (final ScanResult scanResult = new ClassGraph().acceptPackages(allBasePackages).enableAnnotationInfo().overrideClasspath((Object[]) classPath).scan())
        {
            result.addAll(extract(scanResult, Entity.class));
            result.addAll(extract(scanResult, MappedSuperclass.class));
            result.addAll(extract(scanResult, Embeddable.class));
            result.addAll(extract(scanResult, Converter.class));
        }
        return result;
    }

    private Collection<? extends String> extract(final ScanResult scanResult, final Class<?> type)
    {
        return scanResult.getClassesWithAnnotation(type.getCanonicalName()).getNames();
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

        return allBasePackages.toArray(new String[0]);
    }
}