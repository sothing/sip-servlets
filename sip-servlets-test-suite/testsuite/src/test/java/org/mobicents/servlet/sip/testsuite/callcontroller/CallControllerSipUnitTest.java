/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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
package org.mobicents.servlet.sip.testsuite.callcontroller;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sip.message.Response;
import static junit.framework.Assert.assertTrue;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.mobicents.servlet.sip.NetworkPortAssigner;
import org.mobicents.servlet.sip.SipUnitServletTestCase;
import org.mobicents.servlet.sip.startup.SipStandardContext;

public class CallControllerSipUnitTest extends SipUnitServletTestCase {

    private static transient Logger logger = Logger.getLogger(CallControllerSipUnitTest.class);

    private SipStack sipStackSender;
    private SipPhone sipPhoneSender;

    private SipStack sipStackReceiver;
    private SipPhone sipPhoneReceiver;
    private int senderPort;
    private int receiverPort;

    private static final int TIMEOUT = 30000;
    private static final int TIMEOUT_FORBIDDEN = 40000;
//	private static final int TIMEOUT = 1000000;

    public CallControllerSipUnitTest(String name) {
        super(name);
        autoDeployOnStartup = false;
    }

    @Override
    public void setUp() throws Exception {
        autoDeployOnStartup = false;
        containerPort = NetworkPortAssigner.retrieveNextPort();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        Thread.sleep(1000);
        sipPhoneSender.dispose();
        sipPhoneReceiver.dispose();
        sipStackSender.dispose();
        sipStackReceiver.dispose();
        super.tearDown();
    }

    @Override
    public void deployApplication() {
    }

    private void deployCallBlocking(Map<String, String> params) {
        SipStandardContext ctx = deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-blocking-servlet/src/main/sipapp",
                "call-blocking", 
                params,
                null);
        assertTrue(ctx.getAvailable());
    }

    private void deployCallForwarding(Map<String, String> params) {
        SipStandardContext ctx = deployApplication(projectHome
                + "/sip-servlets-test-suite/applications/call-forwarding-b2bua-servlet/src/main/sipapp",
                "call-forwarding-b2bua",
                params,
                null);
        assertTrue(ctx.getAvailable());        
    }

    @Override
    protected String getDarConfigurationFile() {
        return "file:///"
                + projectHome
                + "/sip-servlets-test-suite/testsuite/src/test/resources/"
                + "org/mobicents/servlet/sip/testsuite/callcontroller/call-controller-servlet-dar.properties";
    }

    public SipStack makeStack(String transport, int port) throws Exception {
        Properties properties = new Properties();
        String peerHostPort1 = "" + System.getProperty("org.mobicents.testsuite.testhostaddr") + ":" + containerPort;
        properties.setProperty("javax.sip.OUTBOUND_PROXY", peerHostPort1 + "/"
                + "udp");
        properties.setProperty("javax.sip.STACK_NAME", "UAC_" + transport + "_"
                + port);
        properties.setProperty("sipunit.BINDADDR", "" + System.getProperty("org.mobicents.testsuite.testhostaddr") + "");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "target/logs/callforwarding_debug_" + port + ".txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "target/logs/callforwarding_server_" + port + ".xml");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");

        return new SipStack(transport, port, properties);
    }

    public void setupPhone(String fromAddress, String toAddress) {
        try {
            senderPort = NetworkPortAssigner.retrieveNextPort();
            sipStackSender = makeStack(SipStack.PROTOCOL_UDP, senderPort);
            sipPhoneSender = sipStackSender.createSipPhone("" + System.getProperty("org.mobicents.testsuite.testhostaddr") + "",
                    SipStack.PROTOCOL_UDP, containerPort, fromAddress);
            receiverPort = NetworkPortAssigner.retrieveNextPort();
            sipStackReceiver = makeStack(SipStack.PROTOCOL_UDP, receiverPort);
            sipPhoneReceiver = sipStackReceiver.createSipPhone("" + System.getProperty("org.mobicents.testsuite.testhostaddr") + "",
                    SipStack.PROTOCOL_UDP, containerPort, toAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Check if we receive a FORBIDDEN response for our invite
    public void testCallBlockingInvite() throws Exception {
        setupPhone("sip:blocked-sender@sip-servlets.com", "sip:receiver@sip-servlets.com");
        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));        
        deployCallBlocking(params);        
        SipCall sender = sipPhoneSender.createSipCall();
        assertTrue(sender.initiateOutgoingCall("sip:receiver@sip-servlets.com", null));
        int trial = 0;
        while (findResponse(sender, Response.FORBIDDEN) == null && trial < 5) {
            assertTrue(sender.waitOutgoingCallResponse(TIMEOUT_FORBIDDEN));
            trial++;
        }
    }

    // 
    public void testCallForwarding() throws Exception {
        setupPhone("sip:forward-sender@sip-servlets.com", "sip:forward-receiver@sip-servlets.com");
        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));        
        deployCallForwarding(params);

        SipCall sender = sipPhoneSender.createSipCall();
        SipCall receiver = sipPhoneReceiver.createSipCall();

        receiver.listenForIncomingCall();
        Thread.sleep(300);
        sender.initiateOutgoingCall("sip:receiver@sip-servlets.com", null);

        assertTrue(receiver.waitForIncomingCall(TIMEOUT));
        assertTrue(receiver.sendIncomingCallResponse(Response.OK, "OK", 0));
        assertTrue(sender.waitOutgoingCallResponse(TIMEOUT));
        assertTrue(receiver.waitForAck(TIMEOUT));

//		assertTrue(sender.sendInviteOkAck());
        sender.sendInviteOkAck();
        assertTrue(sender.disconnect());
        assertTrue(receiver.waitForDisconnect(TIMEOUT));
        assertTrue(receiver.respondToDisconnect());
    }

    // 
    public void testCallForwardingBothDeployed() throws Exception {
        setupPhone("sip:forward-sender@sip-servlets.com", "sip:forward-receiver@sip-servlets.com");
        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));        
        deployCallBlocking(params);
        deployCallForwarding(params);

        SipCall sender = sipPhoneSender.createSipCall();
        SipCall receiver = sipPhoneReceiver.createSipCall();

        receiver.listenForIncomingCall();
        Thread.sleep(300);
        sender.initiateOutgoingCall("sip:receiver@sip-servlets.com", null);

        assertTrue(receiver.waitForIncomingCall(TIMEOUT));
        assertTrue(receiver.sendIncomingCallResponse(Response.OK, "OK", 0));
        assertTrue(receiver.waitForAck(TIMEOUT));
        assertTrue(sender.waitOutgoingCallResponse(TIMEOUT));
        //sipunit doesn't succeed to send the ACK since it tries to do it with 
        //Dialog.createRequest(Request.ACK)
//		assertTrue(sender.sendInviteOkAck());		
        sender.sendInviteOkAck();
        assertTrue(sender.disconnect());
        assertTrue(receiver.waitForDisconnect(TIMEOUT));
        assertTrue(receiver.respondToDisconnect());
    }

    // Check if we receive a FORBIDDEN response for our invite
    public void testCallBlockingBothDeployed() throws Exception {
        setupPhone("sip:blocked-sender@sip-servlets.com", "sip:receiver@sip-servlets.com");
        Map<String, String> params = new HashMap();
        params.put("servletContainerPort", String.valueOf(containerPort));
        params.put("testPort", String.valueOf(receiverPort));
        params.put("senderPort", String.valueOf(senderPort));        
        deployCallBlocking(params);
        deployCallForwarding(params);
        SipCall sender = sipPhoneSender.createSipCall();
        assertTrue(sender.initiateOutgoingCall("sip:receiver@sip-servlets.com", null));
        int trial = 0;
        while (findResponse(sender, Response.FORBIDDEN) == null && trial < 5) {
            assertTrue(sender.waitOutgoingCallResponse(TIMEOUT_FORBIDDEN));
            trial++;
        }
    }
}
