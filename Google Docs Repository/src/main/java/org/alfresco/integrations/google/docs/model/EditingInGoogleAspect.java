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

package org.alfresco.integrations.google.docs.model;


import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.repo.copy.CopyBehaviourCallback;
import org.alfresco.repo.copy.CopyDetails;
import org.alfresco.repo.copy.CopyServicePolicies;
import org.alfresco.repo.copy.DoNothingCopyBehaviourCallback;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.version.VersionServicePolicies;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Created by jottley on 3/27/15.
 */
public class EditingInGoogleAspect
        implements CopyServicePolicies.OnCopyNodePolicy,
        VersionServicePolicies.AfterVersionRevertPolicy
{
    private static final Log log = LogFactory.getLog(EditingInGoogleAspect.class);

    private PolicyComponent policyComponent;
    private NodeService nodeService;


    public void setPolicyComponent(PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void init()
    {
        log.debug("Registering Behaviours in EditingInGoogleAspect");
        policyComponent.bindClassBehaviour(CopyServicePolicies.OnCopyNodePolicy.QNAME, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE, new JavaBehaviour(this, "getCopyCallback"));
        policyComponent.bindClassBehaviour(VersionServicePolicies.AfterVersionRevertPolicy.QNAME, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE, new JavaBehaviour(this, "afterVersionRevert"));
    }


    /**
     * Don't allow copying of aspect when in Editing Session
     *
     * @param classRef
     * @param copyDetails
     * @return
     */
    @Override public CopyBehaviourCallback getCopyCallback(QName classRef, CopyDetails copyDetails)
    {
        log.debug("Attempting to copy EditingInGoogle Aspect - copying is not allowed - It is being removed");
        return new DoNothingCopyBehaviourCallback();
    }


    /**
     * Don't allow the EditingInGoogle aspect to be applied when on a node that is being reverted
     * if the source node had the aspect. GOOGLEDOCS-304
     *
     * @param nodeRef
     * @param version
     */
    @Override public void afterVersionRevert(NodeRef nodeRef, Version version)
    {
        log.debug("A node was reverted that has the EditingInGoogle aspect.  The aspect should be removed on the reverted node.");
        if(nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
        {
            log.debug("Removing EditingInGoogle Aspect from reverted node");
            nodeService.removeAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE);
        }

    }
}
