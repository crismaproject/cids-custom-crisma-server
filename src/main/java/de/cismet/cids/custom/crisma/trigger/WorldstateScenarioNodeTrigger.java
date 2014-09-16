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

    private static final Logger LOGGER = Logger.getLogger(WorldstateNodeTrigger.class);
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
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public void beforeInsert(final String jsonObject, final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("beforeInsert");
        }
    }

    @Override
    public void afterInsert(final String jsonObject, final User user) {
        try {
            final ObjectNode newWorldstate = (ObjectNode)MAPPER.reader().readTree(jsonObject);

            if (!newWorldstate.hasNonNull("childworldstates")) {
                createScenarioNode(user, newWorldstate);
            } else {
                if (newWorldstate.get("childworldstates").isArray()) {
                    final ArrayNode array = (ArrayNode)newWorldstate.get("childworldstates");
                    if (array.size() > 0) {
                        createScenarioNode(user, newWorldstate);
                    }
                }
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
            final Node scenarioNode = WorldstateNodeTriggerHelper.createScenarioNode(user, worldstate);
            MAPPER.writeValue(scenarioFile, scenarioNode);
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
                        + WorldstateNodeTriggerHelper.getNodeFileName(user, worldstate));
        if (scenarioNode.exists()) {
            scenarioNode.delete();
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
                                + WorldstateNodeTriggerHelper.getNodeFileName(user, newWorldstate));
                if (!existingHasChilds && newHasChilds) {
                    // we need to delete the scenario Node
                    deleteScenarioNode(user, newWorldstate);
                } else if (existingHasChilds && !newHasChilds) {
                    // we need to create a scenario Node...
                    createScenarioNode(user, newWorldstate);
                } else if (!newHasChilds && !scenarioNode.exists()) {
                    // this means that there should be a scenario node file but there isn't one
                    // just re create it
                    createScenarioNode(user, newWorldstate);
                }
            }
        } catch (IOException ex) {
            LOGGER.error("can not parse json object...", ex);
        }
    }

    @Override
    public void afterUpdate(final String jsonObject, final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("afterUpdate");
        }
        afterInsert(jsonObject, user);
    }

    @Override
    public void beforeDelete(final String domain, final String classKey, final String objectId, final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("beforeDelete");
        }
    }

    @Override
    public void afterDelete(final String domain, final String classKey, final String objectId, final User user) {
        final ObjectNode worldstate = this.getEntityCore()
                    .getObject(user, classKey, objectId, "current", null, "1", null, null, null, true, true);
        // check if the deleted worldstate is a scenario node...
        if (!worldstate.hasNonNull("childworldsates")) {
            deleteScenarioNode(user, worldstate);
        } else {
            if (worldstate.get("childworldstates").isArray()) {
                final ArrayNode array = (ArrayNode)worldstate.get("childworldstates");
                if (array.size() > 0) {
                    deleteScenarioNode(user, worldstate);
                }
            }
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
