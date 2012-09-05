/**
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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

package org.alfresco.integrations.web.evaluator;


import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.web.evaluator.BaseEvaluator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;


/**
 * Checks that one or more aspects is not present. Based on HasAspectEvaluator
 * 
 * @author: mikeh
 * @author jottley
 */
@Deprecated
public class DoesNotHaveAspectEvaluator
    extends BaseEvaluator
{
    private static final Log  log = LogFactory.getLog(DoesNotHaveAspectEvaluator.class);

    private ArrayList<String> aspects;


    /**
     * Define the list of aspects to check for
     * 
     * @param aspects
     */
    public void setAspects(ArrayList<String> aspects)
    {
        this.aspects = aspects;
    }


    @Override
    public boolean evaluate(JSONObject jsonObject)
    {
        if (aspects.size() > 0)
        {
            try
            {
                JSONArray nodeAspects = getNodeAspects(jsonObject);
                if (nodeAspects != null)
                {
                    for (String aspect : aspects)
                    {
                        if (nodeAspects.contains(aspect))
                        {
                            log.debug("NodeRef: " + ((JSONObject)jsonObject.get("node")).get("nodeRef") + "; Does not have aspect "
                                      + aspect);
                            return false;
                        }
                        log.debug("NodeRef: " + ((JSONObject)jsonObject.get("node")).get("nodeRef") + "; Has aspect " + aspect);
                    }
                }
            }
            catch (Exception err)
            {
                throw new AlfrescoRuntimeException("Failed to run action evaluator: " + err.getMessage());
            }
        }

        return true;
    }
}
