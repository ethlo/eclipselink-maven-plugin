eclipselink-maven-plugin
=========================
[![Maven Central](https://img.shields.io/maven-central/v/com.ethlo.persistence.tools/eclipselink-maven-plugin.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.ethlo.persistence.tools%22%20AND%20a%3A%22eclipselink-maven-plugin%22)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](LICENSE)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/5a0f641b2f944f4fbf46998fe9d184dc)](https://www.codacy.com/app/morten/eclipselink-maven-plugin?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ethlo/eclipselink-maven-plugin&amp;utm_campaign=Badge_Grade)

Eclipselink JPA maven plugin made to simplify life of the [Eclipselink](http://www.eclipse.org/eclipselink/) JPA developer.

## Features
* No need to setup special APT processor for canonical model generation, just use goal ```modelgen```.
* Allows you to get rid of the ```persistence.xml``` file as the classes are detected automatically and a persistence.xml file is generated. 
* If the ```persistence.xml``` file already exists, missing ```<class>...</class>``` entries are added automatically. This allows you to have a basic configuration, but you do not have to manually add class entries.

## Versions
* 3.x releases uses the jakarta.* packages
* 2.x releases uses the javax.* packages

## Usage

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

Both weave, DDL and meta-model generation and setting `basePackages`:
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
		<basePackages>
			<basePackage>org.my.projectA</basePackage>
			<basePackage>org.my.projectB</basePackage>
		</basePackages>
	</configuration>
</plugin>
```
