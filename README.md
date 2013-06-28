Eclipselink-maven-plugin
=========================
Eclipselink JPA maven plugin made to simplify life of a JPA developer.

# Features
* No need to setup special APT processor for canonical model generation, just use goal ```modelgen```.
* Allows you to get rid of the ```persistence.xml``` file as the classes are detected automatically and a persistence.xml file is generated. 
* If the ```persistence.xml``` file already exists, missing ```<class>...</class>``` entries are added automatically. This allows you to have a basic configuration, but you do not have to manually add class entries.

Feedback and pull requests greatly appreciated.

# Build status

[![Build Status](https://travis-ci.org/ethlo/eclipselink-maven-plugin.png?branch=master)](https://travis-ci.org/ethlo/eclipselink-maven-plugin)

# Maven repository
http://ethlo.com/maven

# Maven artifact
```xml
<dependency>
  <groupId>com.ethlo.eclipselink.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>1.1-SNAPSHOT</version>
</dependency>
```

# Usage

Static weaving:
```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>1.1-SNAPSHOT</version>
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
	<version>1.1-SNAPSHOT</version>
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

Both weave and meta-model generation:

```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>1.1-SNAPSHOT</version>
	<executions>
		<execution>
			<id>weave</id>
			<phase>process-classes</phase>
			<goals>
				<goal>weave</goal>
			</goals>
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
