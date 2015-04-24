/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cids.custom.crisma.trigger;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;

import java.util.ArrayList;

import de.cismet.cids.server.rest.cores.EntityCore;
import de.cismet.cids.server.rest.domain.RuntimeContainer;
import de.cismet.cids.server.rest.domain.data.Node;
import de.cismet.cids.server.rest.domain.types.User;
import org.apache.log4j.Logger;

/**
 * DOCUMENT ME!
 *
 * @author   daniel
 * @version  $Revision$, $Date$
 */
public class WorldstateNodeTriggerHelper {

    //~ Static fields/initializers ---------------------------------------------

    private static final Logger LOGGER = Logger.getLogger(WorldstateNodeTriggerHelper.class);
    private static final EntityCore entityCore = RuntimeContainer.getServer().getEntityCore("worldstates");

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param   user        DOCUMENT ME!
     * @param   worldstate  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getNodeFileName(final User user, final ObjectNode worldstate) {
        final String nodePath = getNodePath(user, worldstate);
        final String id = entityCore.getObjectId(worldstate);
        final StringBuilder keyBuilder = new StringBuilder();
        final String fileSeparator;
        if(File.separator.equals("\\")) {
            fileSeparator = "\\";
        } else {
            fileSeparator = File.separator;
        
        }
        
        if(nodePath.length() > 0) {
            final String[] splittedNodePath = nodePath.split(fileSeparator);
            if (!nodePath.isEmpty()) {
                for (int i = 0; i < splittedNodePath.length; i++) {
                    keyBuilder.append(splittedNodePath[i]);
                    keyBuilder.append(".");
                }
            }
        }
        
        keyBuilder.append(id);
        //LOGGER.debug("WS["+worldstate.get("id").asText()+"] - file name: '"+keyBuilder.toString()+"'");
        return keyBuilder.toString();
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user  DOCUMENT ME!
     * @param   node  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getNodePath(final User user, final ObjectNode node) {
        final ArrayList<String> nodePath = new ArrayList<String>();
        final StringBuilder pathBuilder = new StringBuilder();
        ObjectNode tmp = node.deepCopy();
        while (tmp.hasNonNull("parentworldstate")) {
            final ObjectNode parentRef = (ObjectNode)tmp.get("parentworldstate");
            final ObjectNode parent = entityCore.getObject(
                    user,
                    entityCore.getClassKey(parentRef),
                    entityCore.getObjectId(parentRef),
                    "current",
                    null,
                    "1",
                    null,
                    "full",
                    "default",
                    true,
                    true);
            final String parentId = entityCore.getObjectId(parent);
            nodePath.add(parentId);
            tmp = parent;
        }
        if (!nodePath.isEmpty()) {
            for (int i = nodePath.size() - 1; i >= 0; i--) {
                pathBuilder.append(nodePath.get(i));
                pathBuilder.append(File.separator);
            }
            LOGGER.debug("WS["+tmp.get("id").asText()+"] - node path: '"+pathBuilder.toString()+"'");
            return pathBuilder.toString();
        }
        
        LOGGER.debug("WS["+tmp.get("id").asText()+"] - no node path for WS found, probably a root WS");
        return "";
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user            DOCUMENT ME!
     * @param   jsonWorldstate  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static Node createScenarioNode(final User user, final ObjectNode jsonWorldstate) {
        LOGGER.debug("WS["+jsonWorldstate.get("id").asText()+"] - checking if WS is a scenario node");
        // determine the id for the new Node Object
        // ToDo Check if the node id can be the same as the worldstate id
        final String name = jsonWorldstate.get("name").asText();
        final String objectKey = jsonWorldstate.get("$self").asText();
        // create a new Node object
        final Node node = new Node();
        final String key = getNodeFileName(user, jsonWorldstate);
        node.setKey(key);
        node.setName(name);
        node.setObjectKey(objectKey);
        if (!(jsonWorldstate.hasNonNull("childworldstates")
                        && jsonWorldstate.get("childworldstates").isArray()
                        && (((ArrayNode)jsonWorldstate.get("childworldstates")).size() > 0))) {
            LOGGER.info("WS["+jsonWorldstate.get("id").asText()+"] - node '"+name+"' is a scenario node! (leaf WS)");
            node.setLeaf(true);
        } else {
            LOGGER.debug("WS["+jsonWorldstate.get("id").asText()+"] - is no scenario node! (no leaf WS)");
        }
        return node;
    }
}
