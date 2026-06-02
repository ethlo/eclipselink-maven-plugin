# EclipseLink Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.ethlo.persistence.tools/eclipselink-maven-plugin.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.ethlo.persistence.tools%22%20AND%20a%3A%22eclipselink-maven-plugin%22)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](LICENSE)

The EclipseLink Maven Plugin is designed to streamline the workflow for [EclipseLink](http://www.eclipse.org/eclipselink/) JPA developers by automating essential configuration and generation tasks.

## Features

* **Automated Meta-Model Generation:** Eliminates the need to configure a dedicated APT processor for canonical model generation. Simply execute the `modelgen` goal.
* **Dynamic `persistence.xml` Generation:** Allows you to bypass the `persistence.xml` file entirely. Entity classes are detected automatically, and the required persistence file is generated during the build process.
* **Automatic Class Registration:** If a `persistence.xml` file already exists, any missing `<class>...</class>` entries are appended automatically. This enables a minimal baseline configuration without the maintenance overhead of manual class registration.

## Compatibility

* **4.x Releases:** Target the `jakarta.*` namespace and Eclipselink 5.x.
* **3.x Releases:** Target the `jakarta.*` namespace.
* **2.x Releases:** Target the `javax.*` namespace.

## Usage

### Static Weaving

```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
	<configuration>
		<basePackages>
			<basePackage>com.yourcompany.project</basePackage>
		</basePackages>
	</configuration>
	<executions>
		<execution>
			<phase>process-classes</phase>
			<goals>
				<goal>weave</goal>
			</goals>
		</execution>
	</executions>
</plugin>

```

### Meta-Model Generation

```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
	<configuration>
		<basePackages>
			<basePackage>com.yourcompany.project</basePackage>
		</basePackages>
	</configuration>
	<executions>
		<execution>
			<phase>generate-sources</phase>
			<goals>
				<goal>modelgen</goal>
			</goals>
		</execution>
	</executions>
</plugin>

```

### Combined: Weaving, DDL, and Meta-Model Generation

```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
	<configuration>
		<basePackages>
			<basePackage>org.my.projectA</basePackage>
			<basePackage>org.my.projectB</basePackage>
		</basePackages>
	</configuration>
	<executions>
		<execution>
			<id>weave</id>
			<phase>process-classes</phase>
			<goals>
				<goal>weave</goal>
			</goals>
		</execution>
		<execution>
			<id>ddl</id>
			<phase>process-classes</phase>
			<goals>
				<goal>ddl</goal>
			</goals>
			<configuration>
				<databaseProductName>mysql</databaseProductName>
			</configuration>
		</execution>
		<execution>
			<id>modelgen</id>
			<phase>generate-sources</phase>
			<goals>
				<goal>modelgen</goal>
			</goals>
		</execution>
	</executions>
</plugin>

```
