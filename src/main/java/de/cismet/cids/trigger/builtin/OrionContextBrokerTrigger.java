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
package de.cismet.cids.trigger.builtin;

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

import org.w3c.dom.Node;

import java.util.logging.Level;
import java.util.logging.Logger;

import de.cismet.cids.server.rest.domain.types.User;

import de.cismet.cids.trigger.CidsTrigger;
import de.cismet.cids.trigger.CidsTriggerKey;

/**
 * DOCUMENT ME!
 *
 * @author   pascal
 * @version  $Revision$, $Date$
 */
public class OrionContextBrokerTrigger implements CidsTrigger {

    //~ Methods ----------------------------------------------------------------

    @Override
    public void beforeInsert(final String string, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void afterInsert(final String string, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void beforeUpdate(final String string, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void afterUpdate(final String string, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void beforeDelete(final String string, final String string1, final String string2, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void afterDelete(final String string, final String string1, final String string2, final User user) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public CidsTriggerKey getTriggerKey() {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    /**
     * DOCUMENT ME!
     *
     * @param   t  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  UnsupportedOperationException  DOCUMENT ME!
     */
    @Override
    public int compareTo(final CidsTrigger t) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
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
            final QueryBroker queryBroker = QueryFactory.newQuerier("crisma.ait.ac.at", 80, "orion");
            final ObjectFactory objectFactory = new ObjectFactory();
            final String type = "crisma.worldstate";

            final QueryContextResponse response = queryBroker.queryContext(queryBroker.newEntityId(type, ".*", true));

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
            Logger.getLogger(OrionContextBrokerTrigger.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
