/**
 * *************************************************
 *
 * cismet GmbH, Saarbruecken, Germany
 * 
* ... and it just works.
 * 
***************************************************
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cids.custom.crisma.trigger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.log4j.Logger;

import org.openide.util.lookup.ServiceProvider;

import java.io.File;
import java.io.IOException;

import de.cismet.cids.server.rest.cores.NodeCore;
import de.cismet.cids.server.rest.domain.RuntimeContainer;
import de.cismet.cids.server.rest.domain.Starter;
import de.cismet.cids.server.rest.domain.data.Node;
import de.cismet.cids.server.rest.domain.types.User;

import de.cismet.cids.trigger.AbstractEntityCoreAwareCidsTrigger;
import de.cismet.cids.trigger.CidsTrigger;
import de.cismet.cids.trigger.CidsTriggerKey;

import static de.cismet.cids.trigger.CidsTriggerKey.ALL;
import java.util.List;

/**
 * DOCUMENT ME!
 *
 * @author daniel
 * @version $Revision$, $Date$
 */
@ServiceProvider(service = CidsTrigger.class)
public class WorldstateScenarioNodeTrigger extends AbstractEntityCoreAwareCidsTrigger {

    //~ Static fields/initializers ---------------------------------------------
    private static final Logger LOGGER = Logger.getLogger(WorldstateScenarioNodeTrigger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Instance fields --------------------------------------------------------
    final CidsTriggerKey cidsTriggerKey = new CidsTriggerKey(ALL, "worldstates");
    private final NodeCore nodeCore;
    private final File scenarioNodeBaseFolder;

    //~ Constructors -----------------------------------------------------------
    /**
     * Creates a new WorldstateScenarioNodeTrigger object.
     */
    public WorldstateScenarioNodeTrigger() {
        nodeCore = RuntimeContainer.getServer().getNodeCore();
        scenarioNodeBaseFolder = new File(Starter.FS_CIDS_DIR + File.separator
                + RuntimeContainer.getServer().getDomainName()
                + File.separator + "nodes/-1");
        LOGGER.info("ScenarioNodeTrigger initialized with base folder: '" + scenarioNodeBaseFolder + "'");
        if (!scenarioNodeBaseFolder.exists()) {
            LOGGER.warn("base folder '" + scenarioNodeBaseFolder + "' does not exist, attempting to create it");
            scenarioNodeBaseFolder.mkdirs();
        }
    }

    //~ Methods ----------------------------------------------------------------
    @Override
    public void beforeInsert(final String jsonObject, final User user) {

    }

    @Override
    public void afterInsert(final String jsonObject, final User user) {
        try {
            final ObjectNode worldstate = (ObjectNode) MAPPER.reader().readTree(jsonObject);
            final String worldStateId = worldstate.get("id").asText();
            this.updateScenarioNodes(worldStateId, user);
        } catch (IOException ex) {
            LOGGER.warn("can not parse json object...", ex);
            this.updateScenarioNodes("-1", user);
        }
    }

    @Override
    public void beforeUpdate(final String jsonObject, final User user) {

    }

    @Override
    public void afterUpdate(final String jsonObject, final User user) {
        afterInsert(jsonObject, user);
    }

    @Override
    public void afterDelete(final String domain, final String classKey, final String objectId, final User user) {
        this.updateScenarioNodes(objectId, user);
    }

    @Override
    public void beforeDelete(final String domain, final String classKey, final String objectId, final User user) {

    }

    @Override
    public CidsTriggerKey getTriggerKey() {
        return cidsTriggerKey;
    }

    /**
     * DOCUMENT ME!
     *
     * @param o DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public int compareTo(final CidsTrigger o) {
        return 0;
    }

    /**
     * The "hardcore" operation for updating the scenario nodes!
     *
     * @param jsonObject
     * @param user
     */
    private void updateScenarioNodes(final String worldStateId, final User user) {
        try {
            LOGGER.info("WS[" + worldStateId + "] - attempting to create or update scenario nodes for WS or one of its children");

            final List rootNodes = this.nodeCore.getRootNodes(user, "default");
            if (rootNodes != null && !rootNodes.isEmpty()) {
                int nodesUpdated = 0;
                // hardcore-mode: clear the scenario nodes directory!
                final File[] oldScenarioNodes = this.scenarioNodeBaseFolder.listFiles();
                if (oldScenarioNodes != null && oldScenarioNodes.length > 0) {
                    LOGGER.debug("WS[" + worldStateId + "] - clearing " + oldScenarioNodes.length + " outdated or updated scenario nodes");
                    for (final File oldScenarioNode : oldScenarioNodes) {
                        if (!oldScenarioNode.delete()) {
                            LOGGER.warn("WS[" + worldStateId + "] - could not delete old scenario node " + oldScenarioNode + "'");
                        }
                    }
                } else {
                    LOGGER.warn("WS[" + worldStateId + "] - no outdated or updated scenario nodes available!");
                }

                for (final Object rootNodeObject : rootNodes) {
                    final Node rootNode = (Node) rootNodeObject;
                    // create new scenario nodes
                    nodesUpdated += this.updateScenarioNode(rootNode, user, worldStateId);
                }
                
                LOGGER.info("WS[" + worldStateId + "] - "+nodesUpdated+" scenario nodes created or updated");
            } else {
                LOGGER.error("WS[" + worldStateId + "] - no root nodes found - node file system out of sync");
            }
        } catch (Exception ex) {
            LOGGER.error("Updating Scenario nodes failed: " + ex.getMessage(), ex);
        }
    }

    private int updateScenarioNode(final Node node, final User user, final String worldStateId) {
        int nodesUpdated = 0;
        if (node.getKey() != null) {
            final String key = node.getKey();
            if (key != null && !key.isEmpty()) {
                List childNodes = null;
                try {
                    childNodes = this.nodeCore.getChildren(user, key, "default");
                } catch(Exception ex) {
                    // file system node core thows NPE if folder does not exist! :-(
                    LOGGER.warn("WS[" + worldStateId + "]  Exception in file system node core - assuming node '"+key+"' is a leaf node", ex);
                }
                // leaf root node
                if (childNodes == null || childNodes.isEmpty()) {
                    node.setLeaf(true);
                    nodesUpdated += this.writeNode(node, key, worldStateId);
                } else {
                    LOGGER.debug("WS[" + worldStateId + "] - checking " + childNodes.size() + " child nodes");
                    for (final Object childNodeObject : childNodes) {
                        final Node childNode = (Node) childNodeObject;
                        nodesUpdated += this.updateScenarioNode(childNode, user, worldStateId);
                    }
                }
            } else {
                LOGGER.error("WS[" + worldStateId + "] - key missing in node object");
            }
        }
        
        return nodesUpdated;
    }

    private int writeNode(final Node node, final String key, final String worldStateId) {
        try {
            final File scenarioNode = new File(this.scenarioNodeBaseFolder + File.separator + key + ".json");
            LOGGER.info("WS[" + worldStateId + "] - creating new scenario node '" + scenarioNode + "'");
            MAPPER.writeValue(scenarioNode, node);
            return 1;
        } catch (Exception ex) {
            LOGGER.error("WS[" + worldStateId + "] - could not create scenario node '" + ex.getMessage() + "'", ex);
            return 0;
        }
    }
}
