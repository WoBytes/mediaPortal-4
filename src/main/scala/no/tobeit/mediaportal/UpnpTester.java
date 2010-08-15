package no.tobeit.mediaportal;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.teleal.cling.*;
import org.teleal.cling.controlpoint.ActionCallback;
import org.teleal.cling.model.action.ActionException;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.message.header.STAllHeader;
import org.teleal.cling.model.meta.*;
import org.teleal.cling.model.types.*;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;


public class UpnpTester implements Runnable {
    public void run() {
        UpnpService upnpService = new UpnpServiceImpl();
        // Add a listener for device registration events
        upnpService.getRegistry().addListener(
                createRegistryListener(upnpService)
        );


        // Broadcast a search message for all devices
        upnpService.getControlPoint().search(new STAllHeader());

    }


    RegistryListener createRegistryListener(final UpnpService upnpService) {
        return new DefaultRegistryListener() {
            ServiceId serviceId = new UDAServiceId("WANIPConn1");

            @Override
            public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
                RemoteService wanipconn;
                if ((wanipconn = device.findService(serviceId)) != null) {
                    System.out.println("Service discovered: " + wanipconn);
                    executeAction(upnpService, wanipconn);
                }

            }

            @Override
            public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                RemoteService wanipconn;
                if ((wanipconn = device.findService(serviceId)) != null) {
                    System.out.println("Service disappeared: " + wanipconn);
                }
            }

        };
    }

    void executeAction(UpnpService upnpService, Service wanip) {
        ActionInvocation addPortMappingInvocation =  new AddPortMappingActionInvocation(wanip);
        // Executes asynchronous in the background
        upnpService.getControlPoint().execute(
                new ActionCallback(addPortMappingInvocation) {
                    public void success(ActionInvocation actionInvocation) {
                        assert actionInvocation.getOutput().getValues().length == 0;
                        System.out.println("Successfully called action!");
                    }

                    public void failure(ActionInvocation actionInvocation, UpnpResponse operation) {
                        System.out.println("Failed action");
                        System.err.println(createDefaultFailureMessage(actionInvocation, operation));
                    }
                }
        );
    }


    class AddPortMappingActionInvocation extends ActionInvocation {
        AddPortMappingActionInvocation(Service service) {
            super(service.getAction("AddPortMapping"));
            try {
                // This might throw an ActionException if the value is of wrong type
                getInput().addValue(null);                              // NewRemoteHost
                getInput().addValue(new UnsignedIntegerTwoBytes(1234)); // NewExternalPort
                getInput().addValue("TCP");                             // NewProtocol
                getInput().addValue(new UnsignedIntegerTwoBytes(1234)); // NewInternalPort
                String localhost = getLocalhost();
                getInput().addValue(localhost);                         // NewInternalClient
                System.out.println("Adding port to : " + localhost);
                getInput().addValue(true);                              // NewEnabled
                getInput().addValue("Description");                     // NewPortMappingDescription
                getInput().addValue(new UnsignedIntegerFourBytes(0));   // NewLeaseDuration
            } catch (ActionException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }

    class DeletePortMappingActionInvocation extends ActionInvocation {
        DeletePortMappingActionInvocation(Service service) {
            super(service.getAction("DeletePortMapping"));
            try {
                // This might throw an ActionException if the value is of wrong type
                getInput().addValue(null);                              // NewRemoteHost
                getInput().addValue(new UnsignedIntegerTwoBytes(1234)); // NewExternalPort
                getInput().addValue("TCP");                             // NewProtocol
            } catch (ActionException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }

    public InetAddress getLocalHost(InetAddress intendedDestination)
            throws SocketException {
            DatagramSocket sock = new DatagramSocket(39485345);
        sock.connect(intendedDestination, 39485345);
        return sock.getLocalAddress();
    }

    public static String getLocalhost() {
        String retVal = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface ni = netInterfaces.nextElement();
                System.out.println(ni.getName());
                InetAddress ip = null;
                for(InterfaceAddress ip4 : ni.getInterfaceAddresses()) {
                    if(ip4.getAddress() instanceof Inet4Address) {
                        ip = ip4.getAddress();
                    }
                }

                if (ip != null && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
                    System.out.println("Interface " + ni.getName() + " seems to be InternetInterface. I'll take it...");
                    retVal = ni.getName();
                    break;
                } 
            }
        } catch (Exception ex) {
            Logger.getLogger(UpnpTester.class.getName()).log(Level.SEVERE, null, ex);
        }

        return retVal;
    }



    public static void main(String[] args) throws Exception {
        Thread client = new Thread(new UpnpTester());
        client.setDaemon(false);
        client.start();
        Thread.sleep(5000);
    }
}
