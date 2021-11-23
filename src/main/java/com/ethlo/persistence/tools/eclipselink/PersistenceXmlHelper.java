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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.jcp.persistence.ObjectFactory;
import org.jcp.persistence.Persistence;

/**
 * @author Morten Haraldsen
 */
public class PersistenceXmlHelper
{
    private static final ObjectFactory factory = new ObjectFactory();
    final static JAXBContext jc;

    static
    {
        try
        {
            jc = JAXBContext.newInstance(Persistence.class);
        }
        catch (JAXBException e)
        {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public static Persistence createXml(String name)
    {
        final Persistence persistence = factory.createPersistence();
        final Persistence.PersistenceUnit pu = factory.createPersistencePersistenceUnit();
        persistence.getPersistenceUnit().add(pu);
        pu.setName(name);
        pu.setProvider(org.eclipse.persistence.jpa.PersistenceProvider.class.getCanonicalName());
        final Persistence.PersistenceUnit.Properties props = factory.createPersistencePersistenceUnitProperties();
        final Persistence.PersistenceUnit.Properties.Property prop = factory.createPersistencePersistenceUnitPropertiesProperty();
        prop.setName("eclipselink.weaving");
        prop.setValue("static");
        props.getProperty().add(prop);
        pu.setProperties(props);
        return persistence;
    }

    public static void appendClasses(Persistence doc, Set<String> entityClasses)
    {
        doc.getPersistenceUnit().get(0).getClazz().addAll(entityClasses);
    }

    public static Persistence parseXml(Path targetFile)
    {
        try
        {
            final Unmarshaller unmarshaller = jc.createUnmarshaller();
            return (Persistence) unmarshaller.unmarshal(targetFile.toFile());
        }
        catch (JAXBException e)
        {
            throw new UncheckedIOException("Cannot parse " + targetFile, new IOException(e));
        }
    }

    public static Set<String> getClassesAlreadyDefined(Persistence doc)
    {

        return doc.getPersistenceUnit().stream()
                .map(Persistence.PersistenceUnit::getClazz)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

    }

    public static void outputXml(Persistence doc, Path targetFile)
    {
        try
        {
            Files.createDirectories(targetFile.getParent());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        prettyPrint(doc, targetFile);
    }

    public static void prettyPrint(Persistence document, Path file)
    {
        try
        {
            final Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(document, file.toFile());
        }
        catch (JAXBException e)
        {
            throw new UncheckedIOException("Cannot write " + file, new IOException(e));
        }
    }
}