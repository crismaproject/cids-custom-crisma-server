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

/**
 * DOCUMENT ME!
 *
 * @author   daniel
 * @version  $Revision$, $Date$
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
        LOGGER.info("ScenarioNodeTrigger initialized with base folder: '"+scenarioNodeBaseFolder+"'");
        if (!scenarioNodeBaseFolder.exists()) {
            LOGGER.warn("base folder '"+scenarioNodeBaseFolder+"' does not exist, attempting to create it");
            scenarioNodeBaseFolder.mkdirs();
        }
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public void beforeInsert(final String jsonObject, final User user) {
        if (LOGGER.isDebugEnabled()) {
            //LOGGER.debug("beforeInsert");
        }
    }

    @Override
    public void afterInsert(final String jsonObject, final User user) {
        try {
            final ObjectNode newWorldstate = (ObjectNode)MAPPER.reader().readTree(jsonObject);
            LOGGER.info("WS["+newWorldstate.get("id").asText()+"] - attempting to create scenario node for WS '"+newWorldstate.get("name").asText()+"' or one of its children");
            if (!newWorldstate.hasNonNull("childworldstates") 
                    || !newWorldstate.get("childworldstates").isArray() 
                    || ((ArrayNode)newWorldstate.get("childworldstates")).size() == 0) {
                LOGGER.debug("WS["+newWorldstate.get("id").asText()+"] - WS is leaf -> create new scenario node");
                createScenarioNode(user, newWorldstate);
            } else {
                 LOGGER.debug("WS["+newWorldstate.get("id").asText()+"] - WS is no leaf -> don't create new scenario node");
            }
        } catch (IOException ex) {
            LOGGER.error("can not parse json Object...", ex);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user        DOCUMENT ME!
     * @param   worldstate  DOCUMENT ME!
     *
     * @throws  IOException  DOCUMENT ME!
     */
    private void createScenarioNode(final User user, final ObjectNode worldstate) throws IOException {
        final String scenarioFileName = WorldstateNodeTriggerHelper.getNodeFileName(user, worldstate);
        final File scenarioFile = new File(scenarioNodeBaseFolder + File.separator + scenarioFileName + ".json");
        if (!scenarioFile.exists()) {
            LOGGER.info("WS["+worldstate.get("id").asText()+"] - creating new scenario node for WS at '" +
                    scenarioFile.getCanonicalPath()+"'");
            final Node scenarioNode = WorldstateNodeTriggerHelper.createScenarioNode(user, worldstate);
            MAPPER.writeValue(scenarioFile, scenarioNode);
        } else {
            LOGGER.debug("WS["+worldstate.get("id").asText()+"] - scenario node for WS already exists at '" +
                    scenarioFile.getCanonicalPath()+"'");
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  user        DOCUMENT ME!
     * @param  worldstate  DOCUMENT ME!
     */
    private void deleteScenarioNode(final User user, final ObjectNode worldstate) {
        final File scenarioNode = new File(scenarioNodeBaseFolder + File.separator
                        + WorldstateNodeTriggerHelper.getNodeFileName(user, worldstate)+".json");
        if (scenarioNode.exists()) {
            LOGGER.debug("WS["+worldstate.get("id").asText()+"] - deleting scenario node for WS at '" +
                    scenarioNode+"'");
            scenarioNode.delete();
        } else {
            LOGGER.warn("WS["+worldstate.get("id").asText()+"] - cannot deleting scenario node for WS at '" +
                    scenarioNode+"', node does not exist!");
        }
    }

    @Override
    public void beforeUpdate(final String jsonObject, final User user) {
        try {
            // it can happen that childs were added or removed
            final ObjectNode newWorldstate = (ObjectNode)MAPPER.reader().readTree(jsonObject);
            ObjectNode existingWorldstate = null;

            final String id = this.getEntityCore().getObjectId(newWorldstate);
            // worldstate id is set -> either new object and id 'invented' by the client
            // or existing WS that is updated by the client.
            if (!id.equals("-1")) {
                // check if WS is existing -> create ot update
                existingWorldstate = this.getEntityCore()
                            .getObject(
                                    user,
                                    this.getEntityCore().getClassKey(newWorldstate),
                                    id,
                                    "current",
                                    null,
                                    "1",
                                    null,
                                    "full",
                                    "default",
                                    true,
                                    true);
            } else {
                LOGGER.error("id of worldstate is not set!");
                throw new Error("id of worldstate is not set!");
            }

            // new worldstate created
            if (null != existingWorldstate) {
                final boolean newHasChilds = newWorldstate.hasNonNull("childworldstates")
                            && (newWorldstate.get("childworldstates").isArray()
                                && (((ArrayNode)newWorldstate.get("childworldstates")).size() > 0));
                final boolean existingHasChilds = existingWorldstate.hasNonNull("childworldstates")
                            && (existingWorldstate.get("childworldstates").isArray()
                                && (((ArrayNode)existingWorldstate.get("childworldstates")).size() > 0));
                final File scenarioNode = new File(scenarioNodeBaseFolder + File.separator
                                + WorldstateNodeTriggerHelper.getNodeFileName(user, newWorldstate) + ".json");
                if (!existingHasChilds && newHasChilds) {
                    // we need to delete the scenario Node
                    LOGGER.debug("WS["+id+"] - WS has new children -> delete existing scenario node");
                    deleteScenarioNode(user, newWorldstate);
                } else if (existingHasChilds && !newHasChilds) {
                    // we need to create a scenario Node...
                    LOGGER.debug("WS["+id+"] - WS children have been removed -> create new scenario node");
                    createScenarioNode(user, newWorldstate);
                } else if (!newHasChilds && !scenarioNode.exists()) {
                    // this means that there should be a scenario node file but there isn't one
                    // just re create it
                    LOGGER.debug("WS["+id+"] - scenario node does not exist -> create new scenario node");
                    createScenarioNode(user, newWorldstate);
                } else {
                    LOGGER.debug("WS["+id+"] new WS hasNewChildren: "+newHasChilds
                            +", existing WS hasChildren: "+existingHasChilds 
                            +", scenario node '"+scenarioNode+"' exists: '"+scenarioNode.exists() 
                            +", dont create new scenario node");
                }
            } else {
                LOGGER.warn("WS["+id+"] - WS does not exist, no update is performed (insert instead)");
            }
        } catch (IOException ex) {
            LOGGER.error("can not parse json object...", ex);
        }
    }

    @Override
    public void afterUpdate(final String jsonObject, final User user) {
        if (LOGGER.isDebugEnabled()) {
            //LOGGER.debug("afterUpdate");
        }
        afterInsert(jsonObject, user);
    }

    @Override
    public void afterDelete(final String domain, final String classKey, final String objectId, final User user) {
        if (LOGGER.isDebugEnabled()) {
            //LOGGER.debug("beforeDelete");
        }
    }

    @Override
    public void beforeDelete(final String domain, final String classKey, final String objectId, final User user) {
        
        String theClassKey = classKey;
        
        if(classKey.indexOf(domain) != 0) {
            LOGGER.warn("WS["+objectId+"] - wrong class key '"+classKey+"' provided "
                    + "in deleteObject, changing to '" + domain + "." + classKey+"'");
            theClassKey = domain + "." + classKey;
        }
        
        final ObjectNode worldstate = this.getEntityCore()
                .getObject(
                        user,
                        theClassKey,
                        objectId,
                        "current",
                        null,
                        "1",
                        null,
                        "full",
                        "default",
                        true,
                        true);
        
        // check if the deleted worldstate is a scenario node...
        if (!worldstate.hasNonNull("childworldstates") || 
                (worldstate.get("childworldstates").isArray()
                    && ((ArrayNode)worldstate.get("childworldstates")).size() == 0)) {
            LOGGER.debug("WS["+objectId+"] - WS to be deleted is leaf -> remove the respective scenario node");
            deleteScenarioNode(user, worldstate);
        } else {
            LOGGER.debug("WS["+objectId+"] - WS to be deleted is no leaf -> keep the respective scenario node");
        }
    }

    @Override
    public CidsTriggerKey getTriggerKey() {
        return cidsTriggerKey;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   o  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public int compareTo(final CidsTrigger o) {
        return 0;
    }
}
