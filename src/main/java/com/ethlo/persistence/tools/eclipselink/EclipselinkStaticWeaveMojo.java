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
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.persistence.logging.AbstractSessionLog;
import org.eclipse.persistence.tools.weaving.jpa.StaticWeaveProcessor;
import ee.jakarta.persistence.ObjectFactory;
import ee.jakarta.persistence.Persistence;
import org.springframework.util.StringUtils;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

/**
 * @author Morten Haraldsen
 */
@Mojo(requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES, name = "weave", requiresProject = true)
public class EclipselinkStaticWeaveMojo extends AbstractMojo
{
    @Parameter
    private String basePackage;

    @Parameter
    private String[] basePackages;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File source;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File target;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File persistenceInfoLocation;

    @Parameter(defaultValue = "WARNING", property = "logLevel")
    private String logLevel;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "true")
    private boolean addClassesToPersistenceFile;

    @Parameter(defaultValue = "true")
    private boolean updatePersistenceXml;

    @Parameter(defaultValue = "false", property = "eclipselink.weave.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException
    {
        setLogLevel(logLevel);
        if (this.skip)
        {
            getLog().info("Skipping EclipseLink weaving by request");
        }
        else
        {
            final ClassLoader classLoader = new URLClassLoader(getClassPath(), Thread.currentThread().getContextClassLoader());
            try
            {
                processWeaving(classLoader);
            }
            catch (Exception e)
            {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            getLog().info("Eclipselink weaving completed");
        }
    }

    private void processWeaving(ClassLoader classLoader) throws MojoExecutionException, MojoFailureException
    {
        if (!source.exists())
        {
            throw new MojoExecutionException("Source directory " + source + " does not exist");
        }

        try
        {
            String[] allBasePackages = this.getBasePackages();
            if (allBasePackages.length > 0)
            {
                getLog().info("Only entities from base packages '" + StringUtils.arrayToDelimitedString(allBasePackages, ", ") + "' will be included in persistence.xml");
            }
            final URL[] classPath = getClassPath();
            getLog().debug("Scanning class-path: " + Arrays.toString(classPath));

            final Set<String> entityClasses = findEntities(allBasePackages, classPath);
            getLog().info("Entities found : " + entityClasses.size());

            if (updatePersistenceXml)
            {
                getLog().debug("Updating persistence.xml file");
                processPersistenceXml(entityClasses);
            }
            else
            {
                getLog().debug("Skipping update of persistence.xml file");
            }

            getLog().info("Source classes dir: " + source);
            getLog().info("Target classes dir: " + target);

            final StaticWeaveProcessor weaveProcessor = new StaticWeaveProcessor(source, target);
            weaveProcessor.setPersistenceInfo(persistenceInfoLocation);
            weaveProcessor.setClassLoader(classLoader);
            weaveProcessor.setLog(new PrintWriter(System.out));
            weaveProcessor.setLogLevel(getLogLevel());
            weaveProcessor.performWeaving();
        }
        catch (URISyntaxException | IOException e)
        {
            throw new MojoExecutionException("Error", e);
        }
    }

    private void processPersistenceXml(Set<String> entityClasses)
    {
        final ObjectFactory objectFactory = new ObjectFactory();
        objectFactory.createPersistence();
        final Path targetFile = Paths.get(this.persistenceInfoLocation.getAbsolutePath(), "/META-INF/persistence.xml");
        getLog().info("persistence.xml location: " + targetFile);

        final String name = project.getArtifactId();
        final Persistence doc = Files.exists(targetFile) ? PersistenceXmlHelper.parseXml(targetFile) : PersistenceXmlHelper.createXml(name);

        checkExisting(targetFile, doc, entityClasses);
        if (addClassesToPersistenceFile)
        {
            PersistenceXmlHelper.appendClasses(doc, entityClasses);
        }
        PersistenceXmlHelper.outputXml(doc, targetFile);
    }

    private void checkExisting(Path targetFile, Persistence doc, Set<String> entityClasses)
    {
        if (Files.exists(targetFile))
        {
            final Set<String> alreadyDefined = PersistenceXmlHelper.getClassesAlreadyDefined(doc);

            if (!alreadyDefined.containsAll(entityClasses))
            {
                final Set<String> undefined = new TreeSet<>();
                for (String className : entityClasses)
                {
                    if (!alreadyDefined.contains(className))
                    {
                        undefined.add(className);
                    }
                }

                getLog().warn("The following classes was not defined in " + targetFile + " even " + "though they are available on the class path: " + Arrays.toString(undefined.toArray()));
            }

            // Don't add so we end up with duplicates
            entityClasses.removeAll(alreadyDefined);
        }
    }

    private int getLogLevel()
    {
        return AbstractSessionLog.translateStringToLoggingLevel(logLevel);
    }

    public void setLogLevel(String logLevel)
    {
        java.util.logging.Level.parse(logLevel);
        this.logLevel = logLevel.toUpperCase();
    }

    private URL[] getClassPath()
    {
        final List<URL> urls = new ArrayList<>();
        try
        {
            for (File file : Utils.getClassPathFiles(project))
            {
                urls.add(file.toURI().toURL());
            }
            return urls.toArray(new URL[0]);
        }
        catch (MalformedURLException exc)
        {
            throw new RuntimeException(exc.getMessage(), exc);
        }
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
        if (basePackage != null && basePackages != null)
        {
            throw new MojoFailureException("<basePackage> and <basePackages> are mutually exclusive");
        }

        if (basePackage != null)
        {
            allBasePackages.add(basePackage);
        }
        else if (basePackages != null)
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
