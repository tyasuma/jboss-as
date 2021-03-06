/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.webservices.dmr;

import org.jboss.as.server.BootOperationContext;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.webservices.deployers.AspectDeploymentProcessor;
import org.jboss.as.webservices.deployers.WSDependenciesProcessor;
import org.jboss.as.webservices.deployers.WSDescriptorDeploymentProcessor;
import org.jboss.as.webservices.deployers.WSModelDeploymentProcessor;
import org.jboss.as.webservices.deployers.WSTypeDeploymentProcessor;
import org.jboss.as.webservices.deployers.WebServiceContextDeploymentUnitProcessor;
import org.jboss.as.webservices.parser.WSDeploymentAspectParser;
import org.jboss.logging.Logger;
import org.jboss.wsf.common.sort.DeploymentAspectSorter;
import org.jboss.wsf.spi.classloading.ClassLoaderProvider;
import org.jboss.wsf.spi.deployment.DeploymentAspect;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

/**
 * @author alessio.soldano@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class WSDeploymentActivator {

    private static final Logger LOGGER = Logger.getLogger(WSDeploymentActivator.class);

    static void activate(final BootOperationContext updateContext) {
        int priority = Phase.INSTALL_WAR_METADATA + 10;

        updateContext.addDeploymentProcessor(Phase.PARSE, Phase.PARSE_WEBSERVICES_XML, new WSDescriptorDeploymentProcessor());
        //updateContext.addDeploymentProcessor(Phase.PARSE, Phase.PARSE_JMS_WS_XML, new WSJMSDescriptorDeploymentProcessor());
        updateContext.addDeploymentProcessor(Phase.DEPENDENCIES, Phase.DEPENDENCIES_WS, new WSDependenciesProcessor());
        //updateContext.addDeploymentProcessor(Phase.DEPENDENCIES, Phase.DEPENDENCIES_JAXRPC, new WSJAXRPCDependenciesDeploymentProcessor());
        //updateContext.addDeploymentProcessor(Phase.INSTALL, priority++, new WSEJBAdapterDeploymentProcessor());
        updateContext.addDeploymentProcessor(Phase.INSTALL, Phase.INSTALL_WS_LAZY_BINDING_SOURCE_HANDLER, new WebServiceContextDeploymentUnitProcessor());
        updateContext.addDeploymentProcessor(Phase.INSTALL, priority++, new WSTypeDeploymentProcessor());
        updateContext.addDeploymentProcessor(Phase.INSTALL, priority++, new WSModelDeploymentProcessor());

        addDeploymentProcessors(updateContext, priority);
    }

    private static List<DeploymentAspect> getDeploymentAspects(final ClassLoader cl, final String resourcePath)
    {
        try {
            Enumeration<URL> urls = WSDeploymentActivator.class.getClassLoader().getResources(resourcePath);
            if (urls != null) {
                URL url = urls.nextElement();
                InputStream is = null;
                try {
                    is = url.openStream();
                    return WSDeploymentAspectParser.parse(is, cl);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
            else {
                throw new RuntimeException("Could not load WS deployment aspects from " + resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load WS deployment aspects from " + resourcePath, e);
        }
    }

    private static void addDeploymentProcessors(final BootOperationContext updateContext, final int priority) {
        int index = 1;
        for (final DeploymentAspect da : getSortedDeploymentAspects()) {
            LOGGER.tracef("Installing aspect %s", da.getClass().getName());
            updateContext.addDeploymentProcessor(Phase.INSTALL, priority + index++, new AspectDeploymentProcessor(da));
        }
    }

    private static List<DeploymentAspect> getSortedDeploymentAspects() {
        final List<DeploymentAspect> deploymentAspects = new LinkedList<DeploymentAspect>();
        ClassLoader cl = ClassLoaderProvider.getDefaultProvider().getServerIntegrationClassLoader();
        deploymentAspects.addAll(getDeploymentAspects(cl, "/META-INF/stack-agnostic-deployment-aspects.xml"));
        deploymentAspects.addAll(getDeploymentAspects(cl, "/META-INF/stack-specific-deployment-aspects.xml"));
        return DeploymentAspectSorter.getInstance().sort(deploymentAspects);
    }

}
