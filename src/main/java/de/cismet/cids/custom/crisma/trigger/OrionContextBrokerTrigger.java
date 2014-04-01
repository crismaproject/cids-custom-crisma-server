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

import com.conwet.samson.QueryBroker;
import com.conwet.samson.QueryFactory;
import com.conwet.samson.jaxb.ContextAttribute;
import com.conwet.samson.jaxb.ContextAttributeList;
import com.conwet.samson.jaxb.ContextElement;
import com.conwet.samson.jaxb.ContextElementResponse;
import com.conwet.samson.jaxb.ContextResponseList;
import com.conwet.samson.jaxb.EntityId;
import com.conwet.samson.jaxb.ObjectFactory;
import com.conwet.samson.jaxb.QueryContextResponse;
import com.conwet.samson.jaxb.StatusCode;
import com.conwet.samson.jaxb.UpdateActionType;
import com.conwet.samson.jaxb.UpdateContextResponse;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.openide.util.lookup.ServiceProvider;

import org.w3c.dom.Node;

import java.io.IOException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.cismet.cids.server.rest.domain.types.User;

import de.cismet.cids.trigger.AbstractEntityCoreAwareCidsTrigger;
import de.cismet.cids.trigger.CidsTrigger;
import de.cismet.cids.trigger.CidsTriggerKey;

import static de.cismet.cids.trigger.CidsTriggerKey.ALL;
//import org.apache.log4j.BasicConfigurator;

/**
 * DOCUMENT ME!
 *
 * @author   pascal
 * @version  $Revision$, $Date$
 */
@ServiceProvider(service = CidsTrigger.class)
public class OrionContextBrokerTrigger extends AbstractEntityCoreAwareCidsTrigger {

    //~ Static fields/initializers ---------------------------------------------

    private static final Logger logger = Logger.getLogger(OrionContextBrokerTrigger.class);

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    // private static final ThreadLocal<ContextElement> contextThreadLocal = new ThreadLocal();

    //~ Enums ------------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @version  $Revision$, $Date$
     */
    public static enum Classification {

        //~ Enum constants -----------------------------------------------------

        WORLDATE_TYPE, DATADESCRIPTOR_TYPE, DATAITEM_TYPE;

        //~ Methods ------------------------------------------------------------

        /**
         * DOCUMENT ME!
         *
         * @return  DOCUMENT ME!
         */
        String value() {
            switch (this) {
                case WORLDATE_TYPE: {
                    return "WS-TYPE";
                }
                case DATADESCRIPTOR_TYPE: {
                    return "DATADESCRIPTOR-TYPE";
                }
                case DATAITEM_TYPE: {
                    return "DATAITEM-TYPE";
                }
                default: {
                    return null;
                }
            }
        }
    }

    //~ Instance fields --------------------------------------------------------

    final CidsTriggerKey cidsTriggerKey = new CidsTriggerKey(ALL, "worldstates");
    final QueryBroker queryBroker;

    private final String host = "http://localhost:8890/";
    private final String contextName = "CRISMA.worldstates";

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new OrionContextBrokerTrigger object.
     */
    public OrionContextBrokerTrigger() {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.ERROR);
        logger.setLevel(Level.ALL);

        logger.info("Orion Query Broker: connecting to crisma.ait.ac.at:80");
        queryBroker = QueryFactory.newQuerier("crisma.ait.ac.at", 80, "orion");
        if (logger.isDebugEnabled()) {
            logger.debug("connected to Orion Query Broker");
        }
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param  string  DOCUMENT ME!
     * @param  user    DOCUMENT ME!
     */
    private void beforeInsertOrUpdate(final String string, final User user) {
        // FIXME: We assume that the client generates the ids. If this behaviour changes
        // in the future, we have to adapt this method to delegate all operations
        // that require id and  $self properties to the afterInsertOrUpdate method
        try {
            final ContextElement contextElement = new ContextElement();
            final ContextAttributeList contextAttributeList = new ContextAttributeList();
            contextElement.setContextAttributeList(contextAttributeList);

            final ObjectNode newWorldstate = (ObjectNode)MAPPER.reader().readTree(string);

            ObjectNode existingWorldstate = null;

            // time attribute
            ContextAttribute contextAttribute = new ContextAttribute();
            contextAttribute.setName("time");
            contextAttribute.setContextValue(String.valueOf(System.currentTimeMillis() / 1000));
            contextAttributeList.getContextAttribute().add(contextAttribute);

            final String id = this.getEntityCore().getObjectId(newWorldstate);
            // worldstate id is set -> either new object and id 'invented' by the client
            // or existing WS that is updated by the client.
            if (!id.equals("-1")) {
                contextElement.setEntityId(queryBroker.newEntityId(contextName, id, false));
                // check if WS is existing -> create ot update
                existingWorldstate = this.getEntityCore()
                            .getObject(
                                    user,
                                    this.getEntityCore().getClassKey(newWorldstate),
                                    id,
                                    "current",
                                    "worldstatedata,categories,classification",
                                    "3",
                                    null,
                                    "full",
                                    "default",
                                    true,
                                    true);
            } else {
                logger.error("id of worldstate is not set!");
                throw new Error("id of worldstate is not set!");
            }

            // worldstate updated
            if (existingWorldstate != null) {
                contextAttribute = new ContextAttribute();
                String wsCategory = this.getCategory(user, newWorldstate, Classification.WORLDATE_TYPE);
                if (wsCategory == null) {
                    // another try ....
                    wsCategory = this.getCategory(user, existingWorldstate, Classification.WORLDATE_TYPE);
                }

                if (wsCategory != null) {
                    contextAttribute.setName("worldstate" + '_' + wsCategory);
                } else {
                    contextAttribute.setName("worldstate");
                }

                contextAttribute.setType(existingWorldstate.get("name").asText());
//                contextAttribute.setContextValue(
//                    "<![CDATA[{\"operation\":\"updated\",\"time\":"
//                            + (System.currentTimeMillis() / 1000)
//                            + ",\"URI\":\""
//                            + this.host
//                            + existingWorldstate.get("$self").asText()
//                            + "\"}]]>");
                contextAttribute.setContextValue("updated");
                contextAttributeList.getContextAttribute().add(contextAttribute);
            } else // worldstate created
            {
                contextAttribute = new ContextAttribute();
                final String wsCategory = this.getCategory(user, newWorldstate, Classification.WORLDATE_TYPE);

                if (wsCategory != null) {
                    contextAttribute.setName("worldstate" + '_' + wsCategory);
                } else {
                    contextAttribute.setName("worldstate");
                }

                contextAttribute.setType(newWorldstate.get("name").asText());
//                contextAttribute.setContextValue(
//                    "{\"operation\":\"created\",\"time\":"
//                            + System.currentTimeMillis()/1000
//                            + ",\"URI\":\""
//                            + this.host
//                            + newWorldstate.get("$self").asText()
//                            + "\"}");
                contextAttribute.setContextValue("created");
                contextAttributeList.getContextAttribute().add(contextAttribute);
            }

            this.checkDataItems(newWorldstate, existingWorldstate, contextAttributeList, user);

            logger.info("publish update to context '" + contextName + "': " + contextElement.getEntityId().getId());
            final UpdateContextResponse updateContextResponse = queryBroker.updateContext(
                    contextElement,
                    UpdateActionType.APPEND);
            if (logger.isDebugEnabled()) {
                logger.debug(updateContextResponse);
            }

            printAttributeList(contextAttributeList);

            // store the context element
            // contextThreadLocal.set(contextElement);

        } catch (IOException ex) {
            logger.fatal(ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.fatal(ex.getMessage(), ex);
        }
    }

    @Override
    public void beforeInsert(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("beforeInsert");
        }

        this.beforeInsertOrUpdate(string, user);
    }

    @Override
    public void afterInsert(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("afterInsert");
        }
    }

    @Override
    public void beforeUpdate(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("beforeUpdate");
        }

        this.beforeInsertOrUpdate(string, user);
    }

    @Override
    public void afterUpdate(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("afterUpdate");
        }
    }

    @Override
    public void beforeDelete(final String string, final String string1, final String string2, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("beforeDelete");
        }
    }

    @Override
    public void afterDelete(final String domain, final String classKey, final String objectId, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("afterDelete");
        }
    }

    @Override
    public CidsTriggerKey getTriggerKey() {
        return this.cidsTriggerKey;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   t  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public int compareTo(final CidsTrigger t) {
        return 0;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  entity  DOCUMENT ME!
     */
    private static void printEntity(final EntityId entity) {
        System.out.println("*** Entity ***");
        System.out.println("Type: " + entity.getType());
        System.out.println("ID: " + entity.getId());
        System.out.println("isPattern: " + entity.isIsPattern());
        System.out.println();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cxtAttrList  DOCUMENT ME!
     */
    private static void printAttributeList(final ContextAttributeList cxtAttrList) {
        System.out.println("*** Attributes ***");

        for (final ContextAttribute cxtAttr : cxtAttrList.getContextAttribute()) {
            System.out.println("name: " + cxtAttr.getName()
                        + ", contextValue: " + extractNodeValue(
                            cxtAttr.getContextValue()));
        }

        System.out.println();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  status  DOCUMENT ME!
     */
    private static void printStatusCode(final StatusCode status) {
        System.out.println("*** Status ***");
        System.out.println("Code: " + status.getCode());
        System.out.println("ReasonPhrase: " + status.getReasonPhrase());
        System.out.println("Details: " + extractNodeValue(status.getDetails()));
        System.out.println();
    }

    /**
     * DOCUMENT ME!
     *
     * @param   obj  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private static String extractNodeValue(final Object obj) {
        if (obj instanceof Node) {
            return ((Node)obj).getTextContent();
        }

        return String.valueOf(obj);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  args  DOCUMENT ME!
     */
    public static void main(final String[] args) {
        try {
            // final Pattern patter = Pattern.compile("^/([^/]*)/");
            final Pattern patter = Pattern.compile("([^/?]+)(?=/?(?:$|\\?))");
            final Matcher m = patter.matcher("/CRISMA.classifications/ghgh/1");

            if (m.find()) {
                final String s = m.group(1);
                System.out.println(s);
                // s now contains "BAR"
            }
            System.exit(0);

            BasicConfigurator.configure();
            Logger.getRootLogger().setLevel(Level.ERROR);
            logger.setLevel(Level.ALL);

            final QueryBroker queryBroker = QueryFactory.newQuerier("crisma.ait.ac.at", 80, "orion");
            final ObjectFactory objectFactory = new ObjectFactory();
            final String type = "CRISMA.worldstate";

            final QueryContextResponse response = queryBroker.queryContext(queryBroker.newEntityId(
                        "CRISMA.worldstate",
                        ".*",
                        true));

            if (response.getErrorCode() != null) {
                System.err.println("*** E R R O R ***");
                printStatusCode(response.getErrorCode());
            } else {
                final ContextResponseList cxtResp = response.getContextResponseList();

                for (final ContextElementResponse resp : cxtResp.getContextElementResponse()) {
                    System.out.println("---------------");
                    printStatusCode(resp.getStatusCode());

                    final ContextElement elem = resp.getContextElement();
                    printEntity(elem.getEntityId());
                    printAttributeList(elem.getContextAttributeList());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Extracts the category name (key) from a JSOn Object, e.g. a Worldstate or a DataItem
     *
     * @param   user            DOCUMENT ME!
     * @param   jsonNode        dataitem
     * @param   classification  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private String getCategory(final User user, final JsonNode jsonNode, final Classification classification) {
        if (jsonNode.hasNonNull("categories") && jsonNode.get("categories").isArray()) {
            for (JsonNode categoryNode : jsonNode.get("categories")) {
                // oh no, it's just a reference!
                if (categoryNode.hasNonNull("$ref")) {
                    categoryNode = this.getEntityCore()
                                .getObject(
                                        user,
                                        this.getEntityCore().getClassKey((ObjectNode)categoryNode),
                                        this.getEntityCore().getObjectId((ObjectNode)categoryNode),
                                        "current",
                                        null,
                                        "2",
                                        null,
                                        "full",
                                        "default",
                                        true,
                                        true);
                }

                // check the classifications
                if ((categoryNode != null) && categoryNode.hasNonNull("classification")
                            && categoryNode.get("classification").isObject()) {
                    JsonNode classificationNode = categoryNode.get("classification");
                    if (classificationNode.hasNonNull("$ref")) {
                        classificationNode = this.getEntityCore()
                                    .getObject(
                                            user,
                                            this.getEntityCore().getClassKey((ObjectNode)classificationNode),
                                            this.getEntityCore().getObjectId((ObjectNode)classificationNode),
                                            "current",
                                            null,
                                            "1",
                                            null,
                                            "full",
                                            "default",
                                            true,
                                            true);
                    }

                    if ((classificationNode != null) && classificationNode.hasNonNull("key")
                                && classificationNode.get("key").asText().equalsIgnoreCase(
                                    classification.value())) {
                        return categoryNode.get("key").asText();
                    }
                }
            }
        }

        return null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  newJsonObject         DOCUMENT ME!
     * @param  existingJsonObject    DOCUMENT ME!
     * @param  contextAttributeList  DOCUMENT ME!
     * @param  user                  DOCUMENT ME!
     */
    private void checkDataItems(final JsonNode newJsonObject,
            final JsonNode existingJsonObject,
            final ContextAttributeList contextAttributeList,
            final User user) {
        ContextAttribute contextAttribute;

        if (newJsonObject.hasNonNull("worldstatedata") && newJsonObject.get("worldstatedata").isArray()) {
            for (JsonNode newDataitem : newJsonObject.get("worldstatedata")) {
                final String newDataitemId = this.getEntityCore().getObjectId((ObjectNode)newDataitem);
                // oh no, it's just a reference!
                if (newDataitem.hasNonNull("$ref")) {
                    newDataitem = this.getEntityCore()
                                .getObject(
                                        user,
                                        this.getEntityCore().getClassKey((ObjectNode)newDataitem),
                                        newDataitemId,
                                        "current",
                                        null,
                                        "4",
                                        null,
                                        "full",
                                        "default",
                                        true,
                                        true);
                }

                // probably updated!

                if (!newDataitemId.equals("-1")) {
                    // check if worldstate really updated!

                    if ((existingJsonObject != null) && existingJsonObject.hasNonNull("worldstatedata")
                                && existingJsonObject.get("worldstatedata").isArray()) {
                        // dataitem updated!
                        for (JsonNode existingDataitem : existingJsonObject.get("worldstatedata")) {
                            final String existingDataitemId = this.getEntityCore()
                                        .getObjectId((ObjectNode)existingDataitem);
                            // oh no, it's just a reference!
                            if (existingDataitem.hasNonNull("$ref")) {
                                existingDataitem = this.getEntityCore()
                                            .getObject(
                                                    user,
                                                    this.getEntityCore().getClassKey((ObjectNode)existingDataitem),
                                                    existingDataitemId,
                                                    "current",
                                                    null,
                                                    "4",
                                                    null,
                                                    "full",
                                                    "default",
                                                    true,
                                                    true);
                            }

                            // dataitem updated!
                            if (newDataitemId.equals(existingDataitemId)) {
                                if (
                                    !newDataitem.get("actualaccessinfo").asText().equals(
                                                existingDataitem.get("actualaccessinfo").asText())) {
                                    contextAttribute = new ContextAttribute();

                                    String diCategory = this.getCategory(
                                            user,
                                            newDataitem,
                                            Classification.DATAITEM_TYPE);
                                    if (diCategory == null) {
                                        // another try ....
                                        diCategory = this.getCategory(
                                                user,
                                                existingDataitem,
                                                Classification.DATAITEM_TYPE);
                                    }

                                    if (diCategory != null) {
                                        contextAttribute.setName("dataitem" + '_' + diCategory);
                                    } else {
                                        contextAttribute.setName("dataitem");
                                    }

                                    contextAttribute.setType(newDataitem.get("name").asText());
//                                contextAttribute.setContextValue(
//                                    "{\"operation\":\"updated\",\"time\":"
//                                            + System.currentTimeMillis()/1000
//                                            + ",\"URI\":\""
//                                            + this.host
//                                            + existingDataitem.get("$self").asText()
//                                            + "\"}");
                                    contextAttribute.setContextValue("updated");
                                    contextAttributeList.getContextAttribute().add(contextAttribute);
                                }
                            } else {
                                contextAttribute = new ContextAttribute();
                                final String diCategory = this.getCategory(
                                        user,
                                        newDataitem,
                                        Classification.DATAITEM_TYPE);
                                if (diCategory != null) {
                                    contextAttribute.setName("dataitem" + '_' + diCategory);
                                } else {
                                    contextAttribute.setName("dataitem");
                                }

                                contextAttribute.setType(newDataitem.get("name").asText());
                                contextAttribute.setContextValue(
                                    "\"operation\":\"created\",\"time\":"
                                            + (System.currentTimeMillis() / 1000)
                                            + ",\"URI\":\""
                                            + this.host
                                            + newDataitem.get("$self").asText()
                                            + "\"");
                                // contextAttribute.setContextValue("created");
                                contextAttributeList.getContextAttribute().add(contextAttribute);
                            }
                        }
                    } else // either no existing WS  or no existing data item -> di created
                    {
                        contextAttribute = new ContextAttribute();
                        final String diCategory = this.getCategory(user, newDataitem, Classification.DATAITEM_TYPE);
                        if (diCategory != null) {
                            contextAttribute.setName("dataitem" + '_' + diCategory);
                        } else {
                            contextAttribute.setName("dataitem");
                        }

                        contextAttribute.setType(newDataitem.get("name").asText());
//                        contextAttribute.setContextValue(
//                            "{\"operation\":\"created\",\"time\":"
//                                    + System.currentTimeMillis()/1000
//                                    + ",\"URI\":\""
//                                    + this.host
//                                    + newDataitem.get("$self").asText()
//                                    + "\"}");
                        contextAttribute.setContextValue("created");
                        contextAttributeList.getContextAttribute().add(contextAttribute);
                    }
                } else // new item!
                {
                    logger.warn("invalid data item: no id provided!");
                }
            }
        } else {
            logger.warn("worldstate without dataitems or no worldstate object at all!");
        }
    }
}
