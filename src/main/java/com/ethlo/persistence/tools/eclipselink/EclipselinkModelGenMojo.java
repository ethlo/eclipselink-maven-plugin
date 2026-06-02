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
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.StringUtils;

import ee.jakarta.persistence.Persistence;

/**
 * @author Morten Haraldsen
 */
@Mojo(requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.GENERATE_SOURCES, name = "modelgen", requiresProject = true)
public class EclipselinkModelGenMojo extends AbstractMojo
{
    public static final String PLUGIN_PREFIX = "JPA modelgen: ";
    public static final String JAVA_FILE_FILTER = "/*.java";
    public static final String[] ALL_JAVA_FILES_FILTER = new String[]{"**" + JAVA_FILE_FILTER};

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * A list of inclusion package filters for the apt processor.
     * If not specified all sources will be used
     */
    @Parameter
    private Set<String> includes = new LinkedHashSet<>();

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    private File source;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/apt")
    private File generatedSourcesDirectory;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    @Parameter(defaultValue = "false", property = "eclipselink.modelgen.skip")
    private boolean skip;

    private List<File> getCurrentClassPath()
    {
        final List<File> retVal = new ArrayList<>();
        final URLClassLoader cl = (URLClassLoader) this.getClass().getClassLoader();
        try
        {
            for (URL url : cl.getURLs())
            {
                retVal.add(new File(url.toURI()));
            }
            return retVal;
        }
        catch (URISyntaxException exc)
        {
            throw new RuntimeException(exc.getMessage(), exc);
        }
    }

    /**
     * For some reason this method must be here and cannot use the {@link Utils#getClassPathFiles(MavenProject)} ()}
     */
    private File[] getClassPathFiles(File extraClasspathDir)
    {
        final Set<File> files = new LinkedHashSet<>(getCurrentClassPath());

        // Inject our isolated directory containing the dummy persistence.xml
        if (extraClasspathDir != null)
        {
            files.add(extraClasspathDir);
        }

        List<?> classpathElements;
        try
        {
            classpathElements = project.getTestClasspathElements();
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }

        for (final Object o : classpathElements)
        {
            if (o != null)
            {
                final File file = new File(o.toString());
                if (file.canRead())
                {
                    files.add(file);
                }
            }
        }

        return files.toArray(new File[0]);
    }

    @Override
    public void execute() throws MojoExecutionException
    {
        if (!this.skip)
        {
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null)
            {
                throw new MojoExecutionException("You need to run build with JDK or have tools.jar on the classpath");
            }

            try (final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null))
            {
                final Set<File> sourceFiles = getSourceFiles();
                if (sourceFiles.isEmpty())
                {
                    info("No files to process");
                    return;
                }

                // Generate an isolated dummy persistence.xml to satisfy EclipseLink
                final File modelgenMetaDir = new File(project.getBuild().getDirectory(), "modelgen-meta");
                final File dummyXml = new File(modelgenMetaDir, "META-INF/eclipselink-modelgen.xml");
                if (!dummyXml.exists())
                {
                    debug("Generating isolated persistence.xml for CanonicalModelProcessor");
                    Persistence doc = PersistenceXmlHelper.createXml("modelgen-pu");
                    PersistenceXmlHelper.outputXml(doc, dummyXml.toPath());
                }

                info("Found " + sourceFiles.size() + " source files for potential processing");
                debug("Source files: " + Arrays.toString(sourceFiles.toArray()));
                Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

                // Pass the directory containing the dummy XML to the classpath
                final File[] classPathFiles = getClassPathFiles(modelgenMetaDir);

                final String compileClassPath = StringUtils.join(classPathFiles, File.pathSeparator);
                debug("Classpath: " + compileClassPath);

                final String processor = "org.eclipse.persistence.internal.jpa.modelgen.CanonicalModelProcessor";
                List<String> compilerOptions = buildCompilerOptions(compileClassPath);

                project.addCompileSourceRoot(this.generatedSourcesDirectory.getAbsolutePath());

                final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                final CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilerOptions, null, compilationUnits);
                final Boolean retVal = task.call();
                final StringBuilder s = new StringBuilder();
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics())
                {
                    s.append("\n").append(diagnostic);
                }

                if (!retVal)
                {
                    throw new MojoExecutionException("Processing failed: " + s);
                }
            }
            catch (IOException e)
            {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    private Set<File> getSourceFiles()
    {
        final Set<File> fromSource = getFilesFromDirectory(source);
        final Set<File> fromGenerated = getFilesFromDirectory(generatedSourcesDirectory);
        final Set<File> all = new TreeSet<>();
        all.addAll(fromSource);
        all.addAll(fromGenerated);
        return all;
    }

    private Set<File> getFilesFromDirectory(File dir)
    {
        if (dir == null || !dir.exists() || !dir.isDirectory())
        {
            return new TreeSet<>();
        }

        String[] filters = ALL_JAVA_FILES_FILTER;
        if (includes != null && !includes.isEmpty())
        {
            filters = includes.toArray(new String[0]);
            for (int i = 0; i < filters.length; i++)
            {
                filters[i] = filters[i].replace('.', '/') + JAVA_FILE_FILTER;
            }
        }

        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(dir);
        scanner.setIncludes(filters);
        scanner.scan();

        final Set<File> files = new HashSet<>();
        final String[] includedFiles = scanner.getIncludedFiles();

        if (includedFiles != null)
        {
            for (String includedFile : includedFiles)
            {
                files.add(new File(dir, includedFile));
            }
        }

        return files;
    }

    private List<String> buildCompilerOptions(String compileClassPath)
    {
        final Map<String, String> compilerOpts = new LinkedHashMap<>();
        compilerOpts.put("cp", compileClassPath);
        compilerOpts.put("proc:only", null);
        compilerOpts.put("processor", "org.eclipse.persistence.internal.jpa.modelgen.CanonicalModelProcessor");
        compilerOpts.put("Aeclipselink.persistencexml=META-INF/eclipselink-modelgen.xml", null);

        if (!StringUtils.isEmpty(encoding))
        {
            compilerOpts.put("encoding", encoding);
        }

        info("Output directory: " + this.generatedSourcesDirectory.getAbsolutePath());
        if (!this.generatedSourcesDirectory.exists())
        {
            this.generatedSourcesDirectory.mkdirs();
        }
        compilerOpts.put("d", this.generatedSourcesDirectory.getAbsolutePath());

        try
        {
            compilerOpts.put("sourcepath", source.getCanonicalPath());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }

        final List<String> opts = new ArrayList<>(compilerOpts.size() * 2);
        for (Map.Entry<String, String> compilerOption : compilerOpts.entrySet())
        {
            opts.add("-" + compilerOption.getKey());
            String value = compilerOption.getValue();
            if (StringUtils.isNotBlank(value))
            {
                opts.add(value);
            }
        }
        return opts;
    }

    private void debug(String msg)
    {
        getLog().debug(PLUGIN_PREFIX + msg);
    }

    private void info(String msg)
    {
        getLog().info(PLUGIN_PREFIX + msg);
    }
}