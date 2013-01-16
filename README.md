Eclipselink-maven-plugin
=========================
Eclipselink JPA maven plugin made to simplify life of a JPA developer.

# Features
* Allows you to get rid of the ```persistence.xml``` file as the classes are detected automatically and a persistence.xml file is generated. 
* If the ```persistence.xml``` file already exists, missing ```<class>...</class>``` entries are added automatically. This allows you to have a basic configuration, but you do not have to manually add class entries.

# Build status

[![Build Status](https://travis-ci.org/ethlo/eclipselink-maven-plugin.png?branch=master)](https://travis-ci.org/ethlo/eclipselink-maven-plugin)

# Maven repository
http://ethlo.com/maven

# Maven artifact
```xml
<dependency>
  <groupId>com.ethlo.eclipselink.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>0.3-SNAPSHOT</version>
</dependency>
```

# Usage

```xml
<plugin>
	<groupId>com.ethlo.persistence.tools</groupId>
	<artifactId>eclipselink-maven-plugin</artifactId>
	<version>0.3-SNAPSHOT</version>
	<configuration>
		<prefix>com.kezzler</prefix>
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
