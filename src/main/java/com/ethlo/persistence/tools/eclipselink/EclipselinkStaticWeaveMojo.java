package com.ethlo.persistence.tools.eclipselink;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.persistence.logging.AbstractSessionLog;
import org.eclipse.persistence.tools.weaving.jpa.StaticWeaveProcessor;
import org.w3c.dom.Document;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;

/**
 * @author Morten Haraldsen
 */
@Mojo(requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES, name = "weave", requiresProject = true)
public class EclipselinkStaticWeaveMojo extends AbstractMojo {

	@Parameter
	private String basePackage;

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

	@Override
	public void execute() throws MojoExecutionException {
		setLogLevel(logLevel);
		final ClassLoader classLoader = new URLClassLoader(getClassPath(),
				Thread.currentThread().getContextClassLoader());
		processWeaving(classLoader);
		getLog().info("Eclipselink JPA weaving completed");
	}

	private void processWeaving(ClassLoader classLoader) throws MojoExecutionException {
		if (!source.exists()) {
			throw new MojoExecutionException("Source directory " + source + " does not exist");
		}

		try {
			if (basePackage != null) {
				getLog().info("Only entities from base package '" + basePackage + "' will be included in persistence.xml");
			}
			final URL[] classPath = getClassPath();
			getLog().debug("Scanning class-path: " + Arrays.toString(classPath));

			final FastClasspathScanner scanner = new FastClasspathScanner(basePackage != null ? new String[]{basePackage} : new String[0])
			      .overrideClasspath((Object[]) classPath);
			
            final ScanResult scanResult = scanner.scan();
            final Set<String> entityClasses = findEntities(scanResult);
			getLog().info("Entities found : " + entityClasses.size());

			processPersistenceXml(classLoader, entityClasses);

			getLog().info("Source classes dir: " + source);
			getLog().info("Target classes dir: " + target);

			final StaticWeaveProcessor weaveProcessor = new StaticWeaveProcessor(source, target);
			weaveProcessor.setPersistenceInfo(persistenceInfoLocation);
			weaveProcessor.setClassLoader(classLoader);
			weaveProcessor.setLog(new PrintWriter(System.out));
			weaveProcessor.setLogLevel(getLogLevel());
			weaveProcessor.performWeaving();
		} catch (URISyntaxException | IOException e) {
			throw new MojoExecutionException("Error", e);
		}

	}

	private void processPersistenceXml(ClassLoader classLoader, Set<String> entityClasses) {
		final File targetFile = new File(this.persistenceInfoLocation + "/META-INF/persistence.xml");
		getLog().info("persistence.xml location: " + targetFile);

		final String name = project.getArtifactId();
		final Document doc = targetFile.exists() ? PersistenceXmlHelper.parseXml(targetFile)
				: PersistenceXmlHelper.createXml(name);

		checkExisting(targetFile, classLoader, doc, entityClasses);

		PersistenceXmlHelper.appendClasses(doc, entityClasses);
		PersistenceXmlHelper.outputXml(doc, targetFile);
	}

	private void checkExisting(File targetFile, ClassLoader classLoader, Document doc, Set<String> entityClasses) {
		if (targetFile.exists()) {
			final Set<String> alreadyDefined = PersistenceXmlHelper.getClassesAlreadyDefined(doc);

			if (!alreadyDefined.containsAll(entityClasses)) {
				final Set<String> undefined = new TreeSet<>();
				for (String className : entityClasses) {
					if (!alreadyDefined.contains(className)) {
						undefined.add(className);
					}
				}

				getLog().warn("The following classes was not defined in " + targetFile + " even "
						+ "though they are available on the class path: " + Arrays.toString(undefined.toArray()));
			}

			// Don't add so we end up with duplicates
			entityClasses.removeAll(alreadyDefined);
		}
	}

	private int getLogLevel() {
		return AbstractSessionLog.translateStringToLoggingLevel(logLevel);
	}

	public void setLogLevel(String logLevel) {
		java.util.logging.Level.parse(logLevel);
		this.logLevel = logLevel.toUpperCase();
	}

	private File[] getClassPathFiles() {
		final List<File> files = new ArrayList<>();
		List<?> classpathElements;
		try {
			classpathElements = project.getTestClasspathElements();
		} catch (DependencyResolutionRequiredException e) {
			throw new RuntimeException(e.getMessage(), e);
		}

		for (final Object o : classpathElements) {
			if (o != null) {
				final File file = new File(o.toString());
				if (file.canRead()) {
					files.add(file);
				}
			}
		}
		return files.toArray(new File[files.size()]);
	}

	private URL[] getClassPath() {
		final List<URL> urls = new ArrayList<URL>();
		try {
			for (File file : getClassPathFiles()) {
				urls.add(file.toURI().toURL());
			}
			return urls.toArray(new URL[urls.size()]);
		} catch (MalformedURLException exc) {
			throw new RuntimeException(exc.getMessage(), exc);
		}
	}

	private Set<String> findEntities(ScanResult scanResult)
    {
        return new TreeSet<>(scanResult.getNamesOfClassesWithAnnotationsAnyOf(Entity.class, MappedSuperclass.class, Embeddable.class, Converter.class));
    }
}