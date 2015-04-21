/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 * 
 * This file is part of Alfresco
 * 
 * Alfresco is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Alfresco. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.alfresco.integrations.google.docs.webscripts;


import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.repo.management.subsystems.ApplicationContextFactory;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.Path;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.extensions.webscripts.DeclarativeWebScript;


public abstract class GoogleDocsWebScripts
    extends DeclarativeWebScript
    implements ApplicationContextAware
{
    protected final static String GOOGLEDOCS_DRIVE_SUBSYSTEM = "googledocs_drive";
    protected final static String GOOGLEDOCSSERVICE       = "GoogleDocsService";

    protected ApplicationContext  applicationContext;

    protected NodeService nodeService;

    abstract void setGoogledocsService(GoogleDocsService googledocsService);

    public void setApplicationContext(ApplicationContext applicationContext)
    {
        this.applicationContext = applicationContext;
    }


    protected void getGoogleDocsServiceSubsystem()
    {
        try
        {
            ApplicationContextFactory subsystem = (ApplicationContextFactory)applicationContext.getBean(GOOGLEDOCS_DRIVE_SUBSYSTEM);
            ConfigurableApplicationContext childContext = (ConfigurableApplicationContext)subsystem.getApplicationContext();
            setGoogledocsService((GoogleDocsService)childContext.getBean(GOOGLEDOCSSERVICE));
        }
        catch (NoSuchBeanDefinitionException nsbde)
        {
            // googledocs_v2 bean is not present on Community
        }
    }

    protected String getPathElement(NodeRef nodeRef, int position)
    {
        Path path = nodeService.getPath(nodeRef);
        Path.Element element = path.get(position);

        return element.toString();
    }
}
