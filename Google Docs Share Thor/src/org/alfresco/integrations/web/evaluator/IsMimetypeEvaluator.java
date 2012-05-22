
package org.alfresco.integrations.web.evaluator;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.web.evaluator.BaseEvaluator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class IsMimetypeEvaluator extends BaseEvaluator
{

    private String accessor;

    public void setMimetypes(String accessor)
    {
        this.accessor = accessor;
    }

    @Override
    public boolean evaluate(JSONObject jsonObject)
    {
        JSONArray nodeArray = (JSONArray) getJSONValue(getMetadata(), accessor);

        try
        {
            JSONObject node = (JSONObject) jsonObject.get("node");
            if (node == null)
            {
                return false;
            }
            else
            {
                String mimetype = (String) node.get("mimetype");
                if (mimetype == null || !nodeArray.contains(mimetype)) { return false; }
            }
        }
        catch (Exception err)
        {
            throw new AlfrescoRuntimeException("Failed to run action evaluator: "
                        + err.getMessage());
        }

        return true;
    }

}
