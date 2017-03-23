eclipselink-maven-plugin
=========================
[![Maven Central](https://img.shields.io/maven-central/v/com.ethlo.persistence.tools/eclipselink-maven-plugin.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.ethlo.persistence.tools%22%20AND%20a%3A%22eclipselink-maven-plugin%22)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](LICENSE)
[![Build Status](https://travis-ci.org/ethlo/eclipselink-maven-plugin.svg?branch=master)](https://travis-ci.org/ethlo/eclipselink-maven-plugin)

Eclipselink JPA maven plugin made to simplify life of the [Eclipselink](http://www.eclipse.org/eclipselink/) JPA developer.

# Features
* No need to setup special APT processor for canonical model generation, just use goal ```modelgen```.
* Allows you to get rid of the ```persistence.xml``` file as the classes are detected automatically and a persistence.xml file is generated. 
* If the ```persistence.xml``` file already exists, missing ```<class>...</class>``` entries are added automatically. This allows you to have a basic configuration, but you do not have to manually add class entries.

# Test project
A simple test project can be found [here](https://github.com/ethlo/eclipselink-maven-plugin-test)

# Usage

Static weaving:
```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
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

Meta-model generation:
```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
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

Both weave, DDL and meta-model generation and setting `basePackage`:
```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>${eclipselink-maven-plugin.version}</version>
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
	<configuration>
		<basePackage>org.my.project</basePackage>
	</configuration>
</plugin>
```
