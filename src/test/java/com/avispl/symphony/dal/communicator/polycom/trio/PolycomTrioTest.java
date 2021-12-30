/*
 * Copyright (c) 2018-2019 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.polycom.trio;

import com.avispl.symphony.api.common.error.NotImplementedException;
import com.avispl.symphony.api.dal.Version;
import com.avispl.symphony.api.dal.dto.control.Protocol;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus.CallStatusState;
import com.avispl.symphony.api.dal.dto.control.call.DialDevice;
import com.avispl.symphony.api.dal.dto.control.call.MuteStatus;
import com.avispl.symphony.api.dal.dto.control.call.PopupMessage;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.dal.util.JsonUtils;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import org.junit.AfterClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * PolycomTrioTest.java
 * 
 * @author Igor Shpyrko / Symphony Dev Team<br>
 *         Created on Sep 10, 2018
 * @since 4.5
 */
@Tag("Integration")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PolycomTrioTest {
	static final private ObjectMapper objectMapper = createObjectMapper(JsonInclude.Include.NON_NULL);

	static class MockPolycomTrio extends PolycomTrio {
		private Version testVersion;
		private MuteStatus muteStatus;
		private Integer requestedCallRate;

		/**
		 * PolycomTrio constructor
		 */
		public MockPolycomTrio() throws IOException {
		}

		/**
		 * Sets {@code muteStatus} field
		 *
		 * @param muteStatus the {@code muteStatus} to set
		 */
		public void setMuteStatus(MuteStatus muteStatus) {
			this.muteStatus = muteStatus;
		}

		/**
		 * Sets {@code requestedCallRate} field
		 *
		 * @param requestedCallRate the {@code requestedCallRate} to set
		 */
		public void setRequestedCallRate(Integer requestedCallRate) {
			this.requestedCallRate = requestedCallRate;
		}

		/**
		 * Sets {@code testVersion} field
		 *
		 * @param testVersion the {@code testVersion} to set
		 */
		public void setTestVersion(Version testVersion) {
			this.testVersion = testVersion;
		}

		@Override
		public MuteStatus retrieveMuteStatus() {
			return muteStatus;
		}

		@Override
		public Version retrieveSoftwareVersion() {
			return testVersion;
		}

		@Override
		Integer determineVideoCallRateFromConfig() {
			return requestedCallRate;
		}
	}

	/** Communicator object under the test. */
	protected static PolycomTrio testCommunicator;
	/** Call id created during the test. */
	protected static String callId;
	/** Protocol to use while dialing during the test. */
	protected static final Protocol protocol = Protocol.SIP;
	/** far site number that indicates the entity being called during this unit test */
	protected static String farSiteDialString;
	/**
	 * within {@link PolycomTrio#dial(DialDevice)} unit test, if the device is not disconnected, this field will be set to false, preventing the in-call tests
	 * to run
	 */
	private static boolean shouldExecuteTests;

	/**
	 * Tears down test class after execution.
	 */
	@AfterClass
	public static void tearDownAfterClass() {
		if (callId != null) {
			try {
				testCommunicator.hangup(callId);
			} catch (Exception e) {
				// ignore, nothing more we can do
			}
			callId = null;
		}

		if (testCommunicator != null) {
			testCommunicator.destroy();
			testCommunicator = null;
		}
	}

	/**
	 * PolycomTrioTest constructor.
	 */
	public PolycomTrioTest() {
		super();

		if (testCommunicator == null) {
			try {
				testCommunicator = createTestCommunicator();
			} catch (Exception e) {
				fail("Cannot create test communicator");
			}
		}
	}

	/**
	 * Unit test for {@code PolycomTrio#parseCallStatsData(Map)}.
	 */
	@Test
	public void testParseCallStatsData() {
		// expected values
		String expectedCallId = "0xb53e57c0";
		String expectedProtocol = "Auto";
		String expectedRemoteAddress = "nh-sx80";

		// call statistics stream data
		// @formatter:off
		String stream ="{\n" + 
				"  \"CallHandle\": \"" + expectedCallId + "\",\n" + 
				"  \"Type\": \"Incoming\",\n" + 
				"  \"Protocol\": \"" + expectedProtocol + "\",\n" + 
				"  \"CallState\": \"Connected\",\n" + 
				"  \"LineId\": \"1\",\n" + 
				"  \"RemotePartyName\": \"nh-sx80@nh.vnoc1.com\",\n" + 
				"  \"RemotePartyNumber\": \"" + expectedRemoteAddress + "\",\n" + 
				"  \"DurationInSeconds\": \"308\"\n" + 
				"}";
		// @formatter:on

		try {
			CallStats callStats = testCommunicator.parseCallStatsData(jsonStringToMap(stream));
			assertNotNull("Call statistics are null", callStats);
			assertEquals("Call statistics CallId is wrong", expectedCallId, callStats.getCallId());
			assertEquals("Call statistics Protocol is wrong", expectedProtocol, callStats.getProtocol());
			assertEquals("Call statistics RemoteAddress is wrong", expectedRemoteAddress, callStats.getRemoteAddress());
			// the rest are not currently being parsed/available from APIs and should be null
			assertNull("Call statistics CallRateRx is wrong", callStats.getCallRateRx());
			assertNull("Call statistics CallRateTx is wrong", callStats.getCallRateTx());
			assertNull("Call statistics PercentPacketLossRx is wrong", callStats.getPercentPacketLossRx());
			assertNull("Call statistics PercentPacketLossTx is wrong", callStats.getPercentPacketLossTx());
			assertNull("Call statistics  is wrong", callStats.getRequestedCallRate());
			assertNull("Call statistics TotalPacketLossRx is wrong", callStats.getTotalPacketLossRx());
			assertNull("Call statistics TotalPacketLossTx is wrong", callStats.getTotalPacketLossTx());

		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@code PolycomTrio#parseInCallStats(EndpointStatistics endpointStatistics, List<Map<String, ?>> mediaSessions)}.
	 */
	@Test
	public void testParseInCallStats() throws IOException {
		// use mock communicator for this test as we do not want to connect to actual device for mute status
		// it is still a valid test as it inherits all the parsing code from real communicator
		MockPolycomTrio mockTrio = new MockPolycomTrio();
		boolean expectedAudioMuteTx = Math.random() < 0.5;
		mockTrio.setMuteStatus(expectedAudioMuteTx ? MuteStatus.Muted : MuteStatus.Unmuted);
		// also, currently there is a code in Polycom Trio which overrides requested call rate with value from device config
		// that code is there due to a bug in Trio 5.8 APIs which returns crazy numbers for call rate in statistics
		// if that bug is fixed in future versions of Trio APIs, check for requested call rate should be done based on version
		// for now just call to retrieve device config in mock communicator
		int expectedRequestedCallRate = 448; // this value is from statistics below
		mockTrio.setRequestedCallRate(Integer.valueOf(expectedRequestedCallRate));

		String expectedCallId = "0xb4e5dfa0";
		// @formatter:off
		String inCallVoiceStream ="{\n"+
				"\"Ref\": \"" + expectedCallId + "\",\n" + 
				"\"RxPayloadSize\": \"80\",\n" + 
				"\"Jitter\": \"1\",\n" + 
				"\"Category\": \"0:Voice\",\n" + 
				"\"PacketsSent\": \"243\",\n" + 
				"\"PacketsExpected\": \"245\",\n" + 
				"\"TxPayloadSize\": \"20\",\n" + 
				"\"TxMOSCQ\": \"127.0\",\n" + 
				"\"OctetsSent\": \"19440\",\n" + 
				"\"MaxJitter\": \"0\",\n" + 
				"\"PacketsReceived\": \"244\",\n" + 
				"\"RxCodec\": \"3:G.722.1\",\n" + 
				"\"OctetsReceived\": \"19520\",\n" + 
				"\"PacketsLost\": \"1\",\n" + 
				"\"Latency\": \"0\",\n" + 
				"\"TxCodec\": \"3:G.722.1\",\n" + 
				"\"RxMOSCQ\": \"127.0\",\n" + 
				"\"RxMOSLQ\": \"127.0\",\n" + 
				"\"TxMOSLQ\": \"127.0\"\n" + 
				"}";
		String inCallVideoStream ="{\n"+
				"\"Ref\": \"" + expectedCallId + "\",\n" + 
				"\"RxPayloadSize\": \"v\",\n" + 
				"\"VideoRxFrameWidth\": \"320\",\n" + 
				"\"Jitter\": \"1\",\n" + 
				"\"Category\": \"1:Video\",\n" + 
				"\"PacketsSent\": \"0\",\n" + 
				"\"PacketsExpected\": \"136\",\n" + 
				"\"TxPayloadSize\": \"v\",\n" + 
				"\"VideoTxFramerate\": \"0\",\n" + 
				"\"OctetsSent\": \"0\",\n" + 
				"\"MaxJitter\": \"2\",\n" + 
				"\"VideoRxFramerate\": \"16\",\n" + 
				"\"PacketsReceived\": \"136\",\n" + 
				"\"VideoRxFastUpdateReqCnt\": \"0\",\n" + 
				"\"VideoTxActBitrateKbps\": \"0\",\n" + 
				"\"RxCodec\": \"24:H.264\",\n" + 
				"\"OctetsReceived\": \"40910\",\n" + 
				"\"PacketsLost\": \"1\",\n" + 
				"\"Latency\": \"0\",\n" + 
				"\"TxCodec\": \"24:H.264\",\n" + 
				"\"VideoTxFastUpdateReqCnt\": \"1\",\n" + 
				"\"VideoTxFrameWidth\": \"1280\",\n" + 
				"\"VideoTxFrameHeight\": \"720\",\n" + 
				"\"VideoTxConfigBitrateKbps\": \"448\",\n" + 
				"\"VideoRxFrameHeight\": \"180\",\n" + 
				"\"VideoRxActBitrateKbps\": \"319\"\n" + 
				"}";
		
		// @formatter:on
		try {
			// test parseInCallStatsData
			EndpointStatistics endpointStatistics = new EndpointStatistics();
			// some values need to be populated
			CallStats callStats = new CallStats();
			callStats.setCallId(expectedCallId);
			endpointStatistics.setCallStats(callStats);

			List<Map<String, ?>> endpointStatsMap = new ArrayList<>();

			Map<String, Object> refMap = new HashMap<>();
			refMap.put("Ref", expectedCallId);
			// endpointStatsMap.add(refMap);

			// Map<String, List<Map<String, ?>>> streamsMap = new HashMap<>();
			List<Map<String, ?>> streamsList = new ArrayList<>();
			streamsList.add(jsonStringToMap(inCallVoiceStream));
			streamsList.add(jsonStringToMap(inCallVideoStream));
			refMap.put("Streams", streamsList);
			endpointStatsMap.add(refMap);

			mockTrio.parseInCallStats(endpointStatistics, endpointStatsMap);

			// only checking properties populated via the parser
			AudioChannelStats audioChannelStats = endpointStatistics.getAudioChannelStats();
			VideoChannelStats videoChannelStats = endpointStatistics.getVideoChannelStats();
			callStats = endpointStatistics.getCallStats();
			// verify audioChannelStats
			assertEquals("audio channel codec is wrong", "G.722.1", audioChannelStats.getCodec());
			assertEquals("audio channel jitter is wrong", Float.valueOf("1.0"), audioChannelStats.getJitterRx());
			assertEquals("audio channel packet loss is wrong", 1, audioChannelStats.getPacketLossRx().intValue());
			assertEquals("audio channel mute is wrong", Boolean.valueOf(expectedAudioMuteTx), audioChannelStats.getMuteTx());
			// verify videoChannelStats
			assertEquals("video channel codec is wrong", "H.264", videoChannelStats.getCodec());
			assertEquals("video channel bit rate rx is wrong", 319, videoChannelStats.getBitRateRx().intValue());
			assertEquals("video channel bit rate tx is wrong", 0, videoChannelStats.getBitRateTx().intValue());
			assertEquals("video channel frame rate rx is wrong", Float.valueOf("16.0"), videoChannelStats.getFrameRateRx());
			assertEquals("video channel frame rate tx is wrong", Float.valueOf("0.0"), videoChannelStats.getFrameRateTx());
			assertEquals("video channel frame rate rx is wrong", "320x180", videoChannelStats.getFrameSizeRx());
			assertEquals("video channel frame rate tx is wrong", "1280x720", videoChannelStats.getFrameSizeTx());
			assertEquals("video channel jitter is wrong", Float.valueOf("1.0"), videoChannelStats.getJitterRx());
			assertEquals("video channel packet loss is wrong", 1, videoChannelStats.getPacketLossRx().intValue());
			// verify call stats
			assertEquals("call stats call rate rx is wrong", 319, callStats.getCallRateRx().intValue());
			assertEquals("call stats call rate tx is wrong", 0, callStats.getCallRateTx().intValue());
			assertEquals("call stats requested rate is wrong", expectedRequestedCallRate, callStats.getRequestedCallRate().intValue());
			assertEquals("call stats packet loss rx is wrong", Float.valueOf("0.5249344"), callStats.getPercentPacketLossRx());
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@code PolycomTrio#parseVersionData(Map)}.
	 */
	@Test
	public void testParseVersionData() {
		// null data map case
		try {
			Version version = testCommunicator.parseVersionData(null);
			assertNull("Version is not null for 'null' data map", version);
		} catch (Exception e) {
			fail(e.toString());
		}

		// empty data map case
		try {
			Version version = testCommunicator.parseVersionData(new HashMap<String, Object>());
			assertNull("Version is not null for 'empty' data map", version);
		} catch (Exception e) {
			fail(e.toString());
		}

		// valid case
		// expected values
		Integer expectedBuild = Integer.valueOf(4145);
		Integer expectedMajor = Integer.valueOf(5);
		Integer expectedMinor = Integer.valueOf(7);
		Integer expectedPatch = Integer.valueOf(1);
		String expectedVersion = expectedMajor + "." + expectedMinor + "." + expectedPatch + "." + expectedBuild;

		// version stream data
		// @formatter:off
		String stream ="{\n" + 
				"  \"ModelNumber\": \"Trio 8800\",\n" + 
				"  \"AttachedHardware\": {},\n" + 
				"  \"FirmwareRelease\": \"" + expectedVersion + "\",\n" + 
				"  \"IPV6Address\": \"::\",\n" + 
				"  \"DeviceVendor\": \"Polycom\",\n" + 
				"  \"DeviceType\": \"hardwareEndpoint\",\n" + 
				"  \"UpTimeSinceLastReboot\": \"0 Day 22:02:09\",\n" + 
				"  \"IPV4Address\": \"***REMOVED***\",\n" + 
				"  \"MACAddress\": \"0004f2fe3ab0\"\n" + 
				"}";
		// @formatter:on

		try {
			// TODO uncomment
//			Version version = testCommunicator.parseVersionData(JsonUtils.jsonStringToMap(stream));
//			assertNotNull("Version is null", version);
//			assertEquals("Version is wrong", expectedVersion, version.getVersion());
//			assertEquals("Version Build is wrong", expectedBuild, version.getBuild());
//			assertEquals("Version Major is wrong", expectedMajor, version.getMajor());
//			assertEquals("Version Minor is wrong", expectedMinor, version.getMinor());
//			assertEquals("Version Patch is wrong", expectedPatch, version.getPatch());

		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#retrieveSoftwareVersion()}.
	 */
	@Test
	public void test00_RetrieveSoftwareVersion() {
		try {
			// test full command call flow
			Version softwareVersion = testCommunicator.retrieveSoftwareVersion();
			assertNotNull("softwareVersion is null", softwareVersion);

		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#dial(DialDevice)}.
	 */
	@Test
	public void test01_DialAnotherDeviceIfNotAlreadyInCall() {
		try {
			CallStatusState callStatus = testCommunicator.retrieveCallStatus(null).getCallStatusState();
			assertNotNull("Invalid call status retreived from device", callStatus);

			shouldExecuteTests = callStatus == CallStatusState.Disconnected;
			if (shouldExecuteTests) {
				DialDevice device2call = new DialDevice();
				device2call.setDialString(farSiteDialString);
				device2call.setProtocol(protocol);

				String resultCallId = testCommunicator.dial(device2call);
				Thread.sleep(5000);
				assertFalse("CallId should not be null or empty", StringUtils.isNullOrEmpty(resultCallId));
				callId = resultCallId;
			}
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#mute()} and {@link PolycomTrio#unmute()}.
	 */
	@Test
	public void test02_MuteFunctionality() {
		if (shouldExecuteTests) {
			try {
				MuteStatus initMuteState = testCommunicator.retrieveMuteStatus();
				assertNotNull("Mute Status is null", initMuteState);
				if (initMuteState.equals(MuteStatus.Muted)) {
					testCommunicator.unmute();
					Thread.sleep(300);
					assertFalse("Mute Status did not change on first unmute command ", testCommunicator.retrieveMuteStatus().equals(initMuteState));

					testCommunicator.mute();
					Thread.sleep(300);
					assertTrue("Mute Status did not change back to initial state on mute command", testCommunicator.retrieveMuteStatus().equals(initMuteState));
				} else {
					testCommunicator.mute();
					Thread.sleep(300);
					assertFalse("Mute Status did not change on first mute command", testCommunicator.retrieveMuteStatus().equals(initMuteState));

					testCommunicator.unmute();
					Thread.sleep(300);
					assertTrue("Mute Status did not change back to initial state on unmute command",
							testCommunicator.retrieveMuteStatus().equals(initMuteState));
				}
			} catch (Exception e) {
				fail(e.toString());
			}
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#retrieveCallStatus(String)}.
	 */
	@Test
	public void test03_RetrieveCallStatus() {
		try {
			CallStatus callStatus = testCommunicator.retrieveCallStatus(callId);
			assertNotNull("Call status is null", callStatus);
			CallStatusState callStatusState = callStatus.getCallStatusState();
			assertNotNull("Call status state is null", callStatusState);
			if (callId != null) {
				// our call, wait until it is connected, but, just in case, limit number of attempts
				int count = 10;
				while (callStatusState != CallStatusState.Connected) {
					Thread.sleep(500);
					callStatus = testCommunicator.retrieveCallStatus(callId);
					callStatusState = callStatus.getCallStatusState();
					if (--count < 0) {
						break;
					}
				}
				assertEquals("Call is not connected", CallStatusState.Connected, callStatusState);
				assertEquals("Call is is wrong", callId, callStatus.getCallId());
				// notify derived class (if any) that call is connected
				onCallConnected();
			} else if (callStatusState == CallStatusState.Connected) {
				// if connected, must report call id
				assertNotNull("Call id is null", callStatus.getCallId());
			}
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#getMultipleStatistics()}.
	 */
	@Test
	public void test04_GetStatistics() {
		try {
			verifyStatistics(testCommunicator.getMultipleStatistics().get(0));
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#sendMessage(PopupMessage)}.
	 */
	@Test
	public void test05_SendMessage() {
		if (shouldExecuteTests) {
			try {
				testCommunicator.sendMessage(new PopupMessage());
				fail("No error for not implemented sendMessage method call");
			} catch (NotImplementedException e) {
				// expected
			} catch (Exception e) {
				fail(e.toString());
			}
		}
	}

	/**
	 * Unit test for {@link PolycomTrio#hangup(String)}.
	 */
	@Test
	public void test06_HangUpCall() {
		if (shouldExecuteTests) {
			try {
				testCommunicator.hangup(callId);
				callId = null;
			} catch (Exception e) {
				fail(e.toString());
			}
		}
	}

	/**
	 * Test if Trio can retrieve call statistics based on version.
	 */
	@Test
	public void test07_CanRetrieveInCallStats() throws IOException {
		// use mock communicator for this test since we need to simulate different versions
		MockPolycomTrio mockTrio = new MockPolycomTrio();

		// no version set
		mockTrio.setTestVersion(null);
		try {
			assertFalse("Trio can retrieve call statistics without version set", mockTrio.canRetrieveInCallStats());
		} catch (Exception e) {
			fail(e.toString());
		}

		// version <= 5.8, can't retrieve call status
		mockTrio.setTestVersion(new Version("5.7.0"));
		try {
			assertFalse("Trio can retrieve call statistics with version earlier than 5.8", mockTrio.canRetrieveInCallStats());
		} catch (Exception e) {
			fail(e.toString());
		}

		// set trio version to 5.8, result should be "trio can retrieve call status".
		mockTrio.setTestVersion(new Version("5.8.0"));
		try {
			assertTrue("Trio can't retrieve call statistics with version set", mockTrio.canRetrieveInCallStats());
		} catch (Exception e) {
			fail(e.toString());
		}

		// set Trio version to be later than 5.8, should be able to retrieve call status
		mockTrio.setTestVersion(new Version("6.0.0"));
		try {
			assertTrue("Trio can't retrieve call statistics with version set", mockTrio.canRetrieveInCallStats());
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Creates test communicator object.
	 *
	 * @return communicator to test
	 * @throws Exception if any error occurs
	 */
	protected PolycomTrio createTestCommunicator() throws Exception {
		PolycomTrio communicator = new PolycomTrio();
		communicator.setHost("***REMOVED***"); // Polycom Trio 8800 in NH lab
		communicator.setLogin("Polycom");
		communicator.setPassword("1234");
		communicator.setProtocol("https");
		communicator.init();

		farSiteDialString = "6256981951@vnoc1.com";

		return communicator;
	}

	/**
	 * Called upon call is connected. <br>
	 * This method gives derived class (if any) time to prepare for testing call statistics.
	 *
	 * @throws Exception if any error occurs
	 */
	protected void onCallConnected() throws Exception {
		// to be overridden by derived classes if needed
	}

	/**
	 * Verifies statistics received during the test.
	 *
	 * @param statistics statistics to verify
	 */
	protected void verifyStatistics(Statistics statistics) {
		assertNotNull("Statistics is null", statistics);
		assertTrue("Statistics is not EndpointStatistics", statistics instanceof EndpointStatistics);

		EndpointStatistics endpointStatistics = (EndpointStatistics) statistics;
		RegistrationStatus registrationStatus = endpointStatistics.getRegistrationStatus();
		assertNotNull("RegistrationStatus is null", registrationStatus);
		assertNotNull("SIP registrar is null", registrationStatus.getSipRegistrar());
		assertEquals("SIP registered is not true", Boolean.TRUE, registrationStatus.getSipRegistered());
		assertNotNull("SIP details are null", registrationStatus.getSipDetails());

		CallStats callStats = endpointStatistics.getCallStats();
		if (null != callId) {
			// our call must have call statistics
			assertNotNull("Call statistic is null", callStats);
			assertEquals("Call id is wrong", callId, callStats.getCallId());
			String protocolString = protocol.name().toLowerCase();
			assertEquals("Protocol is wrong", protocolString, callStats.getProtocol().toLowerCase());
			assertEquals("Remote address is wrong", protocolString + ":" + farSiteDialString, callStats.getRemoteAddress().toLowerCase());
			assertNotNull("Requested call rate is null", callStats.getRequestedCallRate());
			// NOTE do not verify audio/video call statistics here, they might not be available yet. the code verifying packet loss percentage needs to be added
			// to test parsing methods, not to test of live statistics
		} else {
			// not our call might have call statistics
			if (null != callStats) {
				assertNotNull("Call id is null", callStats.getCallId());
				assertNotNull("Protocol is null", callStats.getProtocol());
				assertNotNull("Remote address is null", callStats.getRemoteAddress());
				assertNotNull("Requested call rate is null", callStats.getRequestedCallRate());
			}
		}
	}


	private final static ObjectMapper createObjectMapper(JsonInclude.Include include) {
		ObjectMapper jsonObjectMapper = new ObjectMapper();
		jsonObjectMapper.setSerializationInclusion(include);
		jsonObjectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		jsonObjectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		jsonObjectMapper.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
		return jsonObjectMapper;
	}


	/**
	 * Convert Json String into Map
	 *
	 * @param jsonString the json formatted string to convert
	 * @return a Map object
	 * @throws Exception if any errors occur
	 */
	public final static Map<String, Object> jsonStringToMap(String jsonString) throws Exception {
		Map<String, Object> map = new HashMap<String, Object>();

		// convert JSON string to Map
		map = objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {
		});
		return map;
	}
}
