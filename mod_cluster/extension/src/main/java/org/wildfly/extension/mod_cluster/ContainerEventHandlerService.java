/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.wildfly.extension.mod_cluster;

import static org.wildfly.extension.mod_cluster.ModClusterLogger.ROOT_LOGGER;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.modcluster.ModClusterService;
import org.jboss.modcluster.config.ProxyConfiguration;
import org.jboss.modcluster.config.impl.ModClusterConfig;
import org.jboss.modcluster.load.LoadBalanceFactorProvider;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.inject.MapInjector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.msc.value.Value;

/**
 * Service configuring and starting mod_cluster.
 *
 * @author Jean-Frederic Clere
 * @author Radoslav Husar
 */
public class ContainerEventHandlerService implements Service<ModClusterService> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append(ModClusterExtension.SUBSYSTEM_NAME);
    public static final ServiceName CONFIG_SERVICE_NAME = SERVICE_NAME.append("config");

    private LoadBalanceFactorProvider load;
    private ModClusterConfig config;

    private final Value<SocketBindingManager> bindingManager;
    private final InjectedValue<SocketBinding> binding = new InjectedValue<>();
    private final Map<String, OutboundSocketBinding> outboundSocketBindings = new HashMap<>();

    private volatile ModClusterService eventHandler;

    ContainerEventHandlerService(ModClusterConfig config, LoadBalanceFactorProvider load, Value<SocketBindingManager> bindingManager) {
        this.config = config;
        this.load = load;
        this.bindingManager = bindingManager;
    }

    @Override
    public ModClusterService getValue() throws IllegalStateException, IllegalArgumentException {
        return this.eventHandler;
    }

    @Override
    public void start(StartContext context) {
        ROOT_LOGGER.debugf("Starting mod_cluster extension");

        boolean isMulticast = isMulticastEnabled(bindingManager.getValue().getDefaultInterfaceBinding().getNetworkInterfaces());

        // Resolve and configure proxies
        if (outboundSocketBindings.size() > 0) {
            List<ProxyConfiguration> proxies = new LinkedList<>();
            for (final OutboundSocketBinding binding : outboundSocketBindings.values()) {
                proxies.add(new ProxyConfiguration() {

                    @Override
                    public InetSocketAddress getRemoteAddress() {
                        // Both host and port may not be null in the model, no need to validate here
                        // Don't do resolving here, let mod_cluster deal with it
                        return new InetSocketAddress(binding.getUnresolvedDestinationAddress(), binding.getDestinationPort());
                    }

                    @Override
                    public InetSocketAddress getLocalAddress() {
                        if (binding.getOptionalSourceAddress() != null) {
                            return new InetSocketAddress(binding.getOptionalSourceAddress(), binding.getAbsoluteSourcePort() == null ? 0 : binding.getAbsoluteSourcePort());
                        } else if (binding.getAbsoluteSourcePort() != null) {
                            // Bind to port only if source address is not configured
                            return new InetSocketAddress(binding.getAbsoluteSourcePort());
                        }
                        // No binding configured so don't bind
                        return null;
                    }

                });
            }
            config.setProxyConfigurations(proxies);
        }

        // Set advertise if no proxies are configured
        if (config.getProxyConfigurations().isEmpty()) {
            config.setAdvertise(isMulticast);
        }

        // Read node to set configuration.
        if (config.getAdvertise()) {
            // There should be a socket-binding.... Well no it needs an advertise socket :-(
            final SocketBinding binding = this.binding.getOptionalValue();
            if (binding != null) {
                config.setAdvertiseSocketAddress(binding.getMulticastSocketAddress());
                config.setAdvertiseInterface(binding.getSocketAddress().getAddress());
                if (!isMulticast) {
                    ROOT_LOGGER.multicastInterfaceNotAvailable();
                }
            }
        }

        this.eventHandler = new ModClusterService(config, load);
    }

    private boolean isMulticastEnabled(Collection<NetworkInterface> ifaces) {
        for (NetworkInterface iface : ifaces) {
            try {
                if (iface.isUp() && (iface.supportsMulticast() || iface.isLoopback())) {
                    return true;
                }
            } catch (SocketException e) {
                // Ignore
            }
        }
        return false;
    }

    @Override
    public void stop(StopContext context) {
        this.eventHandler.shutdown();
        this.eventHandler = null;
    }

    Injector<SocketBinding> getSocketBindingInjector() {
        return this.binding;
    }

    Injector<OutboundSocketBinding> getOutboundSocketBindingInjector(String name) {
        return new MapInjector<>(outboundSocketBindings, name);
    }

}
