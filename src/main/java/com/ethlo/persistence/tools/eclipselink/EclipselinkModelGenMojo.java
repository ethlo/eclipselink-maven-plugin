package com.ethlo.persistence.tools.eclipselink;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
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
    private static final String JAVA_FILE_FILTER = "/*.java";
    private static final String[] ALL_JAVA_FILES_FILTER = new String[] { "**" + JAVA_FILE_FILTER };
    
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
     * @parameter
     */
    private boolean logOnlyOnError = false;
	
    /**
     * @parameter expression="${project.build.sourceDirectory}"
     */
    private File source;

    /**
     * @parameter expression="${project.build.directory}/generated-sources/apt"
     */
    private File generatedSourcesDirectory;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    // Use Hibernate's model generator as it does not require persistence.xml file to run
    private String processor = org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor.class.getName();
    
    // Eclipselink requires pesistence.xml
	//private String processor = "org.eclipse.persistence.internal.jpa.modelgen.CanonicalModelProcessor";

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
    	final List<File> files = new ArrayList<>(getCurrentClassPath());
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
	        getLog().debug("Source files: " + Arrays.toString(sourceFiles.toArray()));
	        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);
	
	        final String compileClassPath = StringUtils.join(getClassPathFiles(), File.pathSeparator);
	        getLog().debug("Classpath: " + compileClassPath);
	        
	        List<String> compilerOptions = buildCompilerOptions(processor , compileClassPath);
	
	        project.addCompileSourceRoot(this.generatedSourcesDirectory.getAbsolutePath());
	        
	        final Writer out = this.logOnlyOnError ? new StringWriter() : null;
	        final CompilationTask task = compiler.getTask(out, fileManager, null, compilerOptions, null, compilationUnits);
	        
	        final Boolean retVal = task.call();
	        if (! retVal)
	        {
	        	if (out != null)
	        	{
	        		getLog().error(out.toString());
	        	}
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
        String[] filters = ALL_JAVA_FILES_FILTER;
        if (includes != null && !includes.isEmpty()) {
            filters = includes.toArray(new String[includes.size()]);
            for (int i = 0; i < filters.length; i++) {
                filters[i] = filters[i].replace('.', '/') + JAVA_FILE_FILTER;
            }
        }
        
        Set<File> files = new HashSet<File>();        
        Scanner scanner = buildContext.newScanner(source);
        scanner.setIncludes(filters);
        scanner.scan();
        
        String[] includedFiles = scanner.getIncludedFiles();
        if (includedFiles != null) {
            for (String includedFile : includedFiles) {
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
        compilerOpts.put("nowarn", null);
        
        getLog().info("Output directory: " + this.generatedSourcesDirectory.getAbsolutePath());
        if (! this.generatedSourcesDirectory.exists())
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
        for (Map.Entry<String, String> compilerOption : compilerOpts.entrySet()) {
            opts.add("-" + compilerOption.getKey());
            String value = compilerOption.getValue();
            if (StringUtils.isNotBlank(value)) {
                opts.add(value);
            }
        }
        return opts;
    }
}