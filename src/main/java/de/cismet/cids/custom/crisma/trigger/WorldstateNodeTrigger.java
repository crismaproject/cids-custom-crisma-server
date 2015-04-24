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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.log4j.Logger;

import org.openide.util.lookup.ServiceProvider;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

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
 * This Trigger is a workaround for the missing dynamic children support of the
 * filesystem node core. The idea is to build a fix node structure based on the
 * worldstate structure.
 *
 * @author daniel
 * @version $Revision$, $Date$
 */
@ServiceProvider(service = CidsTrigger.class)
public class WorldstateNodeTrigger extends AbstractEntityCoreAwareCidsTrigger {

    //~ Static fields/initializers ---------------------------------------------
    private static final Logger LOGGER = Logger.getLogger(WorldstateNodeTrigger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Instance fields --------------------------------------------------------
    final CidsTriggerKey cidsTriggerKey = new CidsTriggerKey(ALL, "worldstates");
    private final NodeCore nodeCore;
    private final File nodeBaseFolder;

    //~ Constructors -----------------------------------------------------------
    /**
     * Creates a new WorldstateNodeTrigger object.
     */
    public WorldstateNodeTrigger() {
        nodeCore = RuntimeContainer.getServer().getNodeCore();
        nodeBaseFolder = new File(Starter.FS_CIDS_DIR + File.separator + RuntimeContainer.getServer().getDomainName()
                + File.separator + "nodes");
        LOGGER.info("NodeTrigger initialized with base folder: '" + nodeBaseFolder + "'");
        if (!nodeBaseFolder.exists()) {
            LOGGER.warn("base folder '" + nodeBaseFolder + "' does not exist, attempting to create it");
            nodeBaseFolder.mkdirs();
        }
    }

    //~ Methods ----------------------------------------------------------------
    @Override
    public void beforeInsert(final String jsonObject, final User user) {
        try {
            // ToDo: If a new Worldstate is created we need to create a node...
            final ObjectNode newWorldstate = (ObjectNode) MAPPER.reader().readTree(jsonObject);

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
                LOGGER.info("WS[" + id + "] - inserting WS '" + newWorldstate.get("name").asText() + "'");
            } else {
                LOGGER.error("id of worldstate is not set!");
                throw new Error("id of worldstate is not set!");
            }

            // new worldstate created
            if (existingWorldstate == null) {
                // it is a new root node

                if (!newWorldstate.hasNonNull("parentworldstate")) {
                    LOGGER.info("WS[" + id + "] - WS is a new ROOT WS!");
                    createWorldstateNode(user, newWorldstate, null);
                } else {
                    // a new node is added as child
                    // build the Path up to the root node
                    LOGGER.debug("WS[" + id + "] - WS is added as new child to parent WS '"
                            + newWorldstate.get("parentworldstate").get("$ref").asText() + "'");
                    final String nodePath = WorldstateNodeTriggerHelper.getNodePath(user, newWorldstate);
                    createWorldstateNode(user, newWorldstate, nodePath);
                }
            } else {
                // worldstate updated
                LOGGER.debug("WS[" + id + "] - already existing -> WS has been updated");
                // update the children directory
                final String nodePath = WorldstateNodeTriggerHelper.getNodePath(user, existingWorldstate);
                createWorldstateNode(user, newWorldstate, nodePath);

                // Check if the parent has changed?
                final String oldParentPath;
                final String newParentPath;
                final boolean oldParentNonNull = existingWorldstate.hasNonNull("parentworldstate");
                final boolean newParentNonNull = newWorldstate.hasNonNull("parentworldstate");
                boolean pathChanged;
                if (oldParentNonNull && newParentNonNull) {
                    pathChanged = !existingWorldstate.get("parentworldstate")
                            .equals(newWorldstate.get("parentworldstate"));
                    oldParentPath = WorldstateNodeTriggerHelper.getNodePath(user, existingWorldstate);
                    newParentPath = WorldstateNodeTriggerHelper.getNodePath(user, newWorldstate);
                } else {
                    pathChanged = (oldParentNonNull && !newParentNonNull) || (!oldParentNonNull && newParentNonNull);
                    if (oldParentNonNull) {
                        oldParentPath = WorldstateNodeTriggerHelper.getNodePath(user, existingWorldstate);
                    } else {
                        oldParentPath = "";
                    }
                    if (newParentNonNull) {
                        newParentPath = WorldstateNodeTriggerHelper.getNodePath(user, newWorldstate);
                    } else {
                        newParentPath = "";
                    }
                }

                // we need to move the node
                if (pathChanged) {
                    LOGGER.debug("WS[" + id + "] - WS parent has changed, move the node");
                    final String fileName = WorldstateNodeTriggerHelper.getNodeFileName(user, newWorldstate);
                    final Path f = new File(nodeBaseFolder + File.separator + oldParentPath + File.separator + fileName)
                            .toPath();
                    final Path f2 = new File(nodeBaseFolder + File.separator + newParentPath).toPath();
                    final Path f3 = new File(nodeBaseFolder + File.separator + oldParentPath + File.separator + fileName
                            + ".json").toPath();
                    final Path f4 = new File(nodeBaseFolder + File.separator + newParentPath + File.separator).toPath();
                    // move the json file
                    Files.move(f3, f4.resolve(f3.getFileName()));
                    // move the children folder
                    if (newWorldstate.hasNonNull("childworldstates")
                            && newWorldstate.get("childworldstates").isArray()) {
                        // create the nodes for the child worldstates
                        final ArrayNode array = (ArrayNode) newWorldstate.get("childworldstates");
                        if (array.size() > 0) {
                            Files.move(f, f2.resolve(f.getFileName()));
                        }
                    }
                } else {
                    LOGGER.debug("WS[" + id + "] - Path of existing node '" + nodePath + "' not changed, no need to move the node");
                }
            }
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void afterInsert(final String jsonObject,
            final User user) {
    }

    @Override
    public void beforeUpdate(final String jsonObject,
            final User user) {
        this.beforeInsert(jsonObject, user);
    }

    @Override
    public void afterUpdate(final String jsonObject,
            final User user) {
    }

    @Override
    public void beforeDelete(final String domain,
            final String classKey,
            final String objectId,
            final User user) {

        String theClassKey = classKey;
        if (classKey.indexOf(domain) != 0) {
            LOGGER.warn("WS[" + objectId + "] - wrong class key '" + classKey + "' provided "
                    + "in deleteObject, chaning to '" + domain + "." + classKey + "'");
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

        if (worldstate != null) {
            final String nodePath = WorldstateNodeTriggerHelper.getNodePath(user, worldstate);
            final String nodeFileName = WorldstateNodeTriggerHelper.getNodeFileName(user, worldstate);
            final File json = new File(nodeBaseFolder + File.separator + nodePath + File.separator + nodeFileName + ".json");
            final File childrenFolder = new File(nodeBaseFolder + File.separator + nodePath + File.separator + objectId);
            LOGGER.info("WS[" + objectId + "] - deleting WS Node file '" + json + "'");
            if (!json.delete()) {
                LOGGER.warn("WS[" + objectId + "] - could not delete old scenario node '" + json + "'");
            }
            if (childrenFolder.exists()) {
                LOGGER.debug("WS[" + objectId + "] - deleting node children folder '" + childrenFolder + "'");
                if (!childrenFolder.delete()) {
                    LOGGER.warn("WS[" + objectId + "] - could not delete old scenario node children folder '" + childrenFolder + "'");
                }
            }

            if (worldstate.hasNonNull("parentworldstate")) {
                LOGGER.debug("WS[" + objectId + "] - WS is deleted from parent WS '"
                        + worldstate.get("parentworldstate").get("$ref").asText() + "'");

                    //TODO: here we would have to check if the parent WS still has children. If not,
                //the 'leaf' property of the parent node has to 'true', thus a scenario node!
                //Current workaround: The WorldstateScenarioNodeTrigger checks for
                // isLeaf() AND childNodes.isEmpty()
            }

        } else {
            LOGGER.warn("WS[" + objectId + "] - cannot delete WS Node: WS already deleted! This should  not happen in beforeDelete operation!");
        }
    }

    @Override
    public void afterDelete(final String domain,
            final String classKey,
            final String objectId,
            final User user) {
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
        // ToDo: Why are Triggers Comparable?
        return 0;
    }

    /**
     * DOCUMENT ME!
     *
     * @param user DOCUMENT ME!
     * @param jsonWorldstate DOCUMENT ME!
     * @param nodePath DOCUMENT ME!
     */
    /**
     * DOCUMENT ME!
     *
     * @param user DOCUMENT ME!
     * @param jsonWorldstate DOCUMENT ME!
     * @param nodePath DOCUMENT ME!
     */
    /**
     * DOCUMENT ME!
     *
     * @param user DOCUMENT ME!
     * @param jsonWorldstate DOCUMENT ME!
     * @param nodePath DOCUMENT ME!
     */
    private void createWorldstateNode(final User user, final ObjectNode jsonWorldstate, final String nodePath) {
        try {
            final String id = entityCore.getObjectId(jsonWorldstate);
            // create a new Node object
            final Node node = WorldstateNodeTriggerHelper.createScenarioNode(user, jsonWorldstate);
            final String key = WorldstateNodeTriggerHelper.getNodeFileName(user, jsonWorldstate);
            // create a file that contains a json representation of the node object, with the id as name
            final File f;
            if ((nodePath != null) && !nodePath.isEmpty()) {
                f = new File(nodeBaseFolder + File.separator + nodePath + File.separator + key
                        + ".json");
            } else {
                f = new File(nodeBaseFolder + File.separator + key + ".json");
            }

            LOGGER.debug("WS[" + id + "] - attempting to create WS Node file '" + f + "'");

            if (!f.getParentFile().exists()) {
                // we need to create also a node for the parent worldstate....
                LOGGER.warn("WS[" + id + "] - parent WS Node does not exist - creating new WS Parent Node");
                final ObjectNode parentRef = (ObjectNode) jsonWorldstate.get("parentworldstate");
                final ObjectNode parent = this.getEntityCore()
                        .getObject(
                                user,
                                this.getEntityCore().getClassKey(parentRef),
                                this.getEntityCore().getObjectId(parentRef),
                                "current",
                                null,
                                "1",
                                null,
                                "full",
                                "default",
                                true,
                                true);
                createWorldstateNode(user, parent, WorldstateNodeTriggerHelper.getNodePath(user, parent));
            }

            MAPPER.writeValue(f, node);
            final String path = ((nodePath != null) && !nodePath.isEmpty()) ? (nodePath + File.separator + id) : id;
            final File dir = new File(nodeBaseFolder + File.separator + path);
            if (dir.exists() && dir.isDirectory()) {
                LOGGER.debug("WS[" + id + "] - deleting nodes directory '" + dir + "'");
                if(!dir.delete()) {
                    LOGGER.warn("WS[" + id + "] - could not delete nodes directory '" + dir + "'");
                    final File[] nodesToDelete = dir.listFiles();
                    int i = 0;
                    for(final File nodeToDelete:nodesToDelete) {
                        if(!nodeToDelete.delete()) {
                            LOGGER.error("WS[" + id + "] - could not delete node file '"+nodeToDelete+"'");
                        } else {
                            i++;
                        }
                    }
                    LOGGER.debug("WS[" + id + "] - " + i + " node files of "+nodesToDelete.length+" files in nodes directory deleted");
                }
            }

            if (jsonWorldstate.hasNonNull("childworldstates")
                    && jsonWorldstate.get("childworldstates").isArray()
                    && (((ArrayNode) jsonWorldstate.get("childworldstates")).size() > 0)) {
                // create the nodes for the child worldstates
                final ArrayNode array = (ArrayNode) jsonWorldstate.get("childworldstates");
                LOGGER.debug("WS[" + id + "] - has " + array.size() + " child WS - creating nodes for child WS in directory '" + dir + "'");
                
                if(!dir.exists()) {
                    if(!dir.mkdirs()) {
                        LOGGER.error("WS[" + id + "] - could not create nodes directory '" + dir + "'");
                    }
                }
                
                for (final JsonNode childWorldstateJson : jsonWorldstate.get("childworldstates")) {
                    final ObjectNode cws = (ObjectNode) childWorldstateJson;
                    final ObjectNode child = entityCore.getObject(
                            user,
                            entityCore.getClassKey(cws),
                            entityCore.getObjectId(cws),
                            "current",
                            null,
                            "1",
                            null,
                            "full",
                            "default",
                            true,
                            true);
                    final String childNodePath = ((nodePath != null) && !nodePath.isEmpty())
                            ? (nodePath + File.separator + id) : id;

//                    if (cws.hasNonNull("$ref")) {
//                       // assume that the WS is not updated! 
//                    }                   
                    if (child != null) {
                        createWorldstateNode(user, child, childNodePath);
                    } else {
                        LOGGER.debug("WS[" + id + "] - creating child node for new child WS '" + entityCore.getObjectId(cws) + "'");
                        createWorldstateNode(user, cws, childNodePath);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }
}
