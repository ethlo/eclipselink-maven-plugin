package com.ethlo.persistence.tools.eclipselink;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Scanner;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author Morten Haraldsen
 * @goal modelgen
 * @phase generate-sources
 * @requiresDependencyResolution compile
 */
public class EclipselinkModelGenMojo extends AbstractMojo
{
    public static final String PLUGIN_PREFIX = "JPA modelgen: ";
    public static final String JAVA_FILE_FILTER = "/*.java";
    public static final String[] ALL_JAVA_FILES_FILTER = new String[] { "**" + JAVA_FILE_FILTER };

    /**
     * @component
     */
    private BuildContext buildContext;

    /**
     * A list of inclusion package filters for the apt processor.
     * 
     * If not specified all sources will be used for apt processor
     * 
     * @parameter
     */
    private Set<String> includes = new HashSet<String>();

    /**
     * @parameter expression="${project.build.sourceDirectory}"
     */
    private File source;

    /**
     * @parameter expression="${project.build.directory}/generated-sources/apt"
     */
    private File generatedSourcesDirectory;

    private boolean verbose = false;
    private boolean noWarn = false;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    // Use Hibernate's model generator as it does not require persistence.xml
    // file to run
    private String processor = org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor.class.getName();

    // Eclipselink requires pesistence.xml
    // private String processor =
    // "org.eclipse.persistence.internal.jpa.modelgen.CanonicalModelProcessor";

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

    private File[] getClassPathFiles()
    {
        final Set<File> files = new TreeSet<>(getCurrentClassPath());
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

        return files.toArray(new File[files.size()]);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
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

            info("Found " + sourceFiles.size() + " source files for potential processing");
            debug("Source files: " + Arrays.toString(sourceFiles.toArray()));
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
            final File[] classPathFiles = getClassPathFiles();

            final String compileClassPath = StringUtils.join(classPathFiles, File.pathSeparator);
            debug("Classpath: " + compileClassPath);

            List<String> compilerOptions = buildCompilerOptions(processor, compileClassPath);

            project.addCompileSourceRoot(this.generatedSourcesDirectory.getAbsolutePath());

            final Writer out = new PrintWriter(System.out);
            final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
            final CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilerOptions, null, compilationUnits);
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
            {
                warn(String.format("Error on line %d in %d%n", diagnostic.getLineNumber(), diagnostic.getSource().toUri()));
            }
            final Boolean retVal = task.call();
            out.flush();
            if (!retVal)
            {
                throw new MojoExecutionException("Processing failed");
            }

            buildContext.refresh(this.generatedSourcesDirectory);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Set<File> getSourceFiles()
    {
        if (source == null || !source.exists())
        {
            return new TreeSet<>();
        }

        String[] filters = ALL_JAVA_FILES_FILTER;
        if (includes != null && !includes.isEmpty())
        {
            filters = includes.toArray(new String[includes.size()]);
            for (int i = 0; i < filters.length; i++)
            {
                filters[i] = filters[i].replace('.', '/') + JAVA_FILE_FILTER;
            }
        }

        Set<File> files = new HashSet<File>();
        final Scanner scanner = buildContext.newScanner(source);
        scanner.setIncludes(filters);
        scanner.scan();

        String[] includedFiles = scanner.getIncludedFiles();
        if (includedFiles != null)
        {
            for (String includedFile : includedFiles)
            {
                files.add(new File(scanner.getBasedir(), includedFile));
            }
        }
        return files;
    }

    private List<String> buildCompilerOptions(String processor, String compileClassPath)
    {
        final Map<String, String> compilerOpts = new LinkedHashMap<String, String>();
        compilerOpts.put("cp", compileClassPath);
        compilerOpts.put("proc:only", null);
        compilerOpts.put("processor", processor);

        if (this.noWarn)
        {
            compilerOpts.put("nowarn", null);
        }

        if (this.verbose)
        {
            compilerOpts.put("verbose", null);
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

        final List<String> opts = new ArrayList<String>(compilerOpts.size() * 2);
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

    private void warn(String msg)
    {
        getLog().warn(PLUGIN_PREFIX + msg);
    }
}