eclipselink-maven-plugin
========================

This plugin allows you to do away with the persistence.xml file as the classes are detected at compile, a persistence.xml file is generated,, and the classes are weaved. 

If the persistence.xml file already exists, missing <class/> entries are added automatically.

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
