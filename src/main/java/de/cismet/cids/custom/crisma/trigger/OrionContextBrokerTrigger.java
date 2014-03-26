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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.openide.util.lookup.ServiceProvider;

import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;

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

    static final Logger logger = Logger.getLogger(OrionContextBrokerTrigger.class);

    private static final ThreadLocal<ContextElement> contextThreadLocal = new ThreadLocal();

    //~ Instance fields --------------------------------------------------------

    final CidsTriggerKey cidsTriggerKey = new CidsTriggerKey(ALL, "worldstates");
    final QueryBroker queryBroker;

    private final String host = "http://localhost:8890/";
    private final String contextName = "CRISMA.worldstate";

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
    private void beforeInsertOrCreate(final String string, final User user) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode rootNode = objectMapper.readTree(string);
            final ContextElement contextElement = new ContextElement();

            if (rootNode.hasNonNull("id")) {
                // Worldstate could be new
                final String id = rootNode.get("id").asText();
                contextElement.setEntityId(queryBroker.newEntityId(contextName, id, false));
            } else {
                // Worldstate must be new

                final ContextAttribute createdAttribute = new ContextAttribute();
                createdAttribute.setName("created");
                createdAttribute.setType("created");

                // type: created, updated, deleted
                // name: worldstate, dataitem,

            }

//            final String self = rootNode.get("$self").hasNonNull(contextName).asText();
            final String id = rootNode.get("id").asText();

            final ContextAttribute createdAttribute = new ContextAttribute();
            createdAttribute.setName("created");
            createdAttribute.setType("created");
//            createdAttribute.setContextValue(this.host + self);

            final ContextAttributeList contextAttributeList = new ContextAttributeList();
            contextAttributeList.getContextAttribute().add(createdAttribute);
            contextElement.setContextAttributeList(contextAttributeList);

            logger.info("publish update to context '" + contextName + "': " + createdAttribute.getContextValue());

            final UpdateContextResponse updateContextResponse = queryBroker.updateContext(
                    contextElement,
                    UpdateActionType.APPEND);
            if (logger.isDebugEnabled()) {
                logger.debug(updateContextResponse);
            }
        } catch (IOException ex) {
            logger.fatal(ex);
        } catch (Exception ex) {
            logger.fatal(ex);
        }
    }

    @Override
    public void beforeInsert(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("beforeInsert");
        }
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
    }

    @Override
    public void afterUpdate(final String string, final User user) {
        if (logger.isDebugEnabled()) {
            logger.debug("afterUpdate");
        }
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode rootNode = objectMapper.readTree(string);

            final String self = rootNode.get("$self").asText();
            final String id = rootNode.get("id").asText();

            final ContextElement contextElement = new ContextElement();
            contextElement.setEntityId(queryBroker.newEntityId(contextName, id, false));

            final ContextAttribute createdAttribute = new ContextAttribute();
            createdAttribute.setName("created");
            createdAttribute.setType("created");
            createdAttribute.setContextValue(this.host + self);

            final ContextAttributeList contextAttributeList = new ContextAttributeList();
            contextAttributeList.getContextAttribute().add(createdAttribute);
            contextElement.setContextAttributeList(contextAttributeList);

            logger.info("publish update to context '" + contextName + "': " + createdAttribute.getContextValue());

            final UpdateContextResponse updateContextResponse = queryBroker.updateContext(
                    contextElement,
                    UpdateActionType.APPEND);
            if (logger.isDebugEnabled()) {
                logger.debug(updateContextResponse);
            }
        } catch (IOException ex) {
            logger.fatal(ex);
        } catch (Exception ex) {
            logger.fatal(ex);
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
}
