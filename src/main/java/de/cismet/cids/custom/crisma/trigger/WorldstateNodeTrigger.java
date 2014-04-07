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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.log4j.Logger;

import org.openide.util.lookup.ServiceProvider;

import java.io.File;
import java.io.IOException;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.logging.Level;

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
 * This Trigger is a workaround for the missing dynamic children support of the filesystem node core. The idea is to
 * build a fix node structure based on the worldstate structure.
 *
 * @author   daniel
 * @version  $Revision$, $Date$
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
    }

    //~ Methods ----------------------------------------------------------------

    @Override
    public void beforeInsert(final String jsonObject, final User user) {
        try {
            // ToDo: If a new Worldstate is created we need to create a node...
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
            if (existingWorldstate == null) {
                // it is a new root node
                if (!newWorldstate.hasNonNull("parentworldstate")) {
                    createWorldstateNode(user, newWorldstate, null);
                } else {
                    // a new node is added as child
                    // build the Path up to the root node
                    final String nodePath = getNodePath(user, newWorldstate);
                    createWorldstateNode(user, newWorldstate, nodePath);
                }
            } else {
                // worldstate updated

                // update the children directory
                final String nodePath = getNodePath(user, existingWorldstate);
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
                    oldParentPath = getNodePath(user, existingWorldstate);
                    newParentPath = getNodePath(user, newWorldstate);
                } else {
                    pathChanged = (oldParentNonNull && !newParentNonNull) || (!oldParentNonNull && newParentNonNull);
                    if (oldParentNonNull) {
                        oldParentPath = getNodePath(user, existingWorldstate);
                    } else {
                        oldParentPath = "";
                    }
                    if (newParentNonNull) {
                        newParentPath = getNodePath(user, newWorldstate);
                    } else {
                        newParentPath = "";
                    }
                }

                // we need to move the node
                if (pathChanged) {
                    final Path f = new File(nodeBaseFolder + File.separator + oldParentPath + File.separator + id)
                                .toPath();
                    final Path f2 = new File(nodeBaseFolder + File.separator + newParentPath).toPath();
                    final Path f3 = new File(nodeBaseFolder + File.separator + oldParentPath + File.separator + id
                                    + ".json").toPath();
                    final Path f4 = new File(nodeBaseFolder + File.separator + newParentPath + File.separator).toPath();
                    // move the json file
                    Files.move(f3, f4.resolve(f3.getFileName()));
                    // move the children folder
                    if (newWorldstate.hasNonNull("childworldstates")
                                && newWorldstate.get("childworldstates").isArray()) {
                        // create the nodes for the child worldstates
                        final ArrayNode array = (ArrayNode)newWorldstate.get("childworldstates");
                        if (array.size() > 0) {
                            Files.move(f, f2.resolve(f.getFileName()));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.fatal(ex.getMessage(), ex);
        }
    }

    @Override
    public void afterInsert(final String jsonObject,
            final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("afterInsert");
        }
    }

    @Override
    public void beforeUpdate(final String jsonObject,
            final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("beforeUpdate");
        }

        this.beforeInsert(jsonObject, user);
    }

    @Override
    public void afterUpdate(final String jsonObject,
            final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("afterUpdate");
        }
    }

    @Override
    public void beforeDelete(final String domain,
            final String classKey,
            final String objectId,
            final User user) {
        final ObjectNode worldstate = this.getEntityCore()
                    .getObject(user, classKey, objectId, "current", null, "1", null, null, null, true, true);
        final String nodePath = getNodePath(user, worldstate);
        final File json = new File(nodeBaseFolder + File.separator + nodePath + File.separator + objectId + ".json");
        final File childrenFolder = new File(nodeBaseFolder + File.separator + nodePath + File.separator + objectId);
        json.delete();
        childrenFolder.delete();
    }

    @Override
    public void afterDelete(final String domain,
            final String classKey,
            final String objectId,
            final User user) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("afterDelete");
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
        // ToDo: Why are Triggers Comparable?
        return 0;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   user  DOCUMENT ME!
     * @param   node  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private String getNodePath(final User user, final ObjectNode node) {
        final ArrayList<String> nodePath = new ArrayList<String>();
        final StringBuilder pathBuilder = new StringBuilder();
        ObjectNode tmp = node.deepCopy();
        while (tmp.hasNonNull("parentworldstate")) {
            final ObjectNode parentRef = (ObjectNode)tmp.get("parentworldstate");
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
            final String parentId = this.getEntityCore().getObjectId(parent);
            nodePath.add(parentId);
            tmp = parent;
        }
        if (!nodePath.isEmpty()) {
            for (int i = nodePath.size() - 1; i >= 0; i--) {
                pathBuilder.append(nodePath.get(i));
                pathBuilder.append(File.separator);
            }
            return pathBuilder.toString();
        }
        return "";
    }

    /**
     * DOCUMENT ME!
     *
     * @param  user            DOCUMENT ME!
     * @param  jsonWorldstate  DOCUMENT ME!
     * @param  nodePath        DOCUMENT ME!
     */
    private void createWorldstateNode(final User user, final ObjectNode jsonWorldstate, final String nodePath) {
        try {
            // determine the id for the new Node Object
            // ToDo Check if the node id can be the same as the worldstate id
            final String id = this.getEntityCore().getObjectId(jsonWorldstate);
            final String name = jsonWorldstate.get("name").asText();
            final String objectKey = jsonWorldstate.get("$self").asText();
            // create a new Node object
            final Node node = new Node();
            node.setKey(id);
            node.setName(name);
            node.setObjectKey(objectKey);
            // create a file that contains a json representation of the node object, with the id as name
            final File f;
            if ((nodePath != null) && !nodePath.isEmpty()) {
                f = new File(nodeBaseFolder + File.separator + nodePath + File.separator + id + ".json");
            } else {
                f = new File(nodeBaseFolder + File.separator + id + ".json");
            }

            MAPPER.writeValue(f, node);
            final String path = ((nodePath != null) && !nodePath.isEmpty()) ? (nodePath + File.separator + id) : id;
            final File dir = new File(nodeBaseFolder + File.separator + path);
            dir.delete();
            if (jsonWorldstate.hasNonNull("childworldstates") && jsonWorldstate.get("childworldstates").isArray()) {
                // create the nodes for the child worldstates
                final ArrayNode array = (ArrayNode)jsonWorldstate.get("childworldstates");
                if (array.size() > 0) {
                    dir.mkdirs();
                    for (final JsonNode childWorldstateJson : jsonWorldstate.get("childworldstates")) {
                        final ObjectNode cws = (ObjectNode)childWorldstateJson;
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
                        createWorldstateNode(user, child, childNodePath);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.fatal(ex.getMessage(), ex);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  args  DOCUMENT ME!
     */
    public static void main(final String[] args) {
    }
}
