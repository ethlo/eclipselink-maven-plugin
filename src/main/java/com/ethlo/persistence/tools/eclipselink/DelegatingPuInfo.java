package com.ethlo.persistence.tools.eclipselink;

import java.net.MalformedURLException;

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

import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

public class DelegatingPuInfo implements PersistenceUnitInfo
{
    private final PersistenceUnitInfo delegate;

    public DelegatingPuInfo(PersistenceUnitInfo delegate)
    {
        this.delegate = delegate;
    }

    public String getPersistenceUnitName()
    {
        return delegate.getPersistenceUnitName();
    }

    public String getPersistenceProviderClassName()
    {
        return delegate.getPersistenceProviderClassName();
    }

    public PersistenceUnitTransactionType getTransactionType()
    {
        return delegate.getTransactionType();
    }

    public DataSource getJtaDataSource()
    {
        return delegate.getJtaDataSource();
    }

    public DataSource getNonJtaDataSource()
    {
        return delegate.getNonJtaDataSource();
    }

    public List<String> getMappingFileNames()
    {
        return delegate.getMappingFileNames();
    }

    public List<URL> getJarFileUrls()
    {
        return delegate.getJarFileUrls();
    }

    public URL getPersistenceUnitRootUrl()
    {
        try
        {
            return new URL("http://foo.bar");
        }
        catch (MalformedURLException exc)
        {
            throw new RuntimeException(exc);
        }
    }

    public List<String> getManagedClassNames()
    {
        return delegate.getManagedClassNames();
    }

    public boolean excludeUnlistedClasses()
    {
        return delegate.excludeUnlistedClasses();
    }

    public SharedCacheMode getSharedCacheMode()
    {
        return delegate.getSharedCacheMode();
    }

    public ValidationMode getValidationMode()
    {
        return delegate.getValidationMode();
    }

    public Properties getProperties()
    {
        return delegate.getProperties();
    }

    public String getPersistenceXMLSchemaVersion()
    {
        return delegate.getPersistenceXMLSchemaVersion();
    }

    public ClassLoader getClassLoader()
    {
        return delegate.getClassLoader();
    }

    public void addTransformer(ClassTransformer transformer)
    {
        delegate.addTransformer(transformer);
    }

    public ClassLoader getNewTempClassLoader()
    {
        return delegate.getNewTempClassLoader();
    }

}
