/*
 * Copyright (c) 2018-2019 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.polycom.trio;

import com.avispl.symphony.api.common.error.NotImplementedException;
import com.avispl.symphony.api.dal.Version;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.control.call.CallController;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.control.Protocol;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus.CallStatusState;
import com.avispl.symphony.api.dal.dto.control.call.DialDevice;
import com.avispl.symphony.api.dal.dto.control.call.MuteStatus;
import com.avispl.symphony.api.dal.dto.control.call.PopupMessage;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.util.StatisticsUtils;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

/**
 * This class handles all communications to and from a Polycom Trio device.
 * 
 * @author Igor Shpyrko / Symphony Dev Team<br>
 *         Created on Sep 10, 2018
 * @since 4.5
 */
public class PolycomTrio extends RestCommunicator implements CallController, Monitorable, Controller {

	/**
	 * Wraps message exchanged in REST API call to Polycom Trio device. <br>
	 * This message contains list of data where each element in the list is a map (where key is a string and value can be string, list, or another map), and,
	 * optional status (only available in response messages).
	 * 
	 * @since 4.5
	 */
	public static class ListMessage {
		private List<Map<String, ?>> data;
		@JsonProperty("Status")
		private String status;

		/**
		 * ListMessage constructor.
		 */
		public ListMessage() {
			super();
		}

		/**
		 * ListMessage constructor.
		 * 
		 * @param data data to be sent
		 */
		public ListMessage(List<Map<String, ?>> data) {
			super();

			this.data = data;
		}

		/**
		 * Retrieves {@code data} property.
		 *
		 * @return the {@code data} property
		 */
		public List<Map<String, ?>> getData() {
			return data;
		}

		/**
		 * Retrieves {@code status} property.
		 *
		 * @return the {@code status} property
		 */
		public String getStatus() {
			return status;
		}

		/**
		 * Sets {@code data} property.
		 *
		 * @param data the {@code data} to set
		 */
		public void setData(List<Map<String, ?>> data) {
			this.data = data;
		}

		/**
		 * Sets {@code status} property.
		 *
		 * @param status the {@code status} to set
		 */
		public void setStatus(String status) {
			this.status = status;
		}
	}

	/**
	 * Wraps message exchanged in REST API call to Polycom Trio device. <br>
	 * This message contains single map of data (where key is a string and value can be string, list, or another map), and, optional status (only available in
	 * response messages).
	 * 
	 * @param <T> type of data wrapped in this message
	 * 
	 * @since 4.5
	 */
	public static class Message<T> {

		private Map<String, T> data;
		@JsonProperty("Status")
		@JsonInclude(Include.NON_NULL)
		private String status;

		/**
		 * Message constructor.
		 */
		public Message() {
			super();
		}

		/**
		 * Message constructor.
		 * 
		 * @param data data to be sent
		 */
		public Message(Map<String, T> data) {
			super();

			this.data = data;
		}

		/**
		 * Retrieves {@code data} property.
		 *
		 * @return the {@code data} property
		 */
		public Map<String, T> getData() {
			return data;
		}

		/**
		 * Retrieves {@code status} property.
		 *
		 * @return the {@code status} property
		 */
		public String getStatus() {
			return status;
		}

		/**
		 * Sets {@code data} property.
		 *
		 * @param data the {@code data} to set
		 */
		public void setData(Map<String, T> data) {
			this.data = data;
		}

		/**
		 * Sets {@code status} property.
		 *
		 * @param status the {@code status} to set
		 */
		public void setStatus(String status) {
			this.status = status;
		}
	}

	/**
	 * Wraps value of config property. <br>
	 * Note that name of the property is not wrapped along with value, typically properties are added to a map where key is a property name and value is the
	 * object represented by this class. <br>
	 * Note also that for config properties exposed in Polycom Trio UI, property names are available in the "Field Help" section.
	 * 
	 * @since 4.7
	 */
	static class ConfigPropertyWrapper {
		@JsonProperty("Value")
		String value;
		@JsonProperty("Source")
		String source;

		/**
		 * Retrieves {@code source} property.
		 *
		 * @return the {@code source} property
		 */
		public String getSource() {
			return source;
		}

		/**
		 * Retrieves {@code value} property.
		 *
		 * @return the {@code value} property
		 */
		public String getValue() {
			return value;
		}

		/**
		 * Sets {@code source} property.
		 *
		 * @param source the {@code source} to set
		 */
		public void setSource(String source) {
			this.source = source;
		}

		/**
		 * Sets {@code value} property.
		 *
		 * @param value the {@code value} to set
		 */
		public void setValue(String value) {
			this.value = value;
		}
	}

	/**
	 * Wraps video channel statistics and requested call rate.
	 */
	static class VideoChannelStatsWrapper {
		final Integer requestedCallRate;
		final VideoChannelStats videoChannelStats;

		VideoChannelStatsWrapper(VideoChannelStats videoChannelStats, Integer requestedCallRate) {
			super();
			this.videoChannelStats = videoChannelStats;
			this.requestedCallRate = requestedCallRate;
		}
	}

	// API URIs
	private static final String VERSION_INFO_URI = "api/v1/mgmt/device/info";
	private static final String CALL_STATUS_URI = "api/v1/webCallControl/callStatus";
	private static final String COMMUNICATION_INFO_URI = "api/v1/mgmt/media/communicationInfo";
	private static final String DIAL_URI = "api/v1/callctrl/dial";
	private static final String END_CALL_URI = "api/v1/callctrl/endCall";
	private static final String GET_CONFIG_URI = "api/v1/mgmt/config/get";
	private static final String LINE_INFO_URI = "api/v1/mgmt/lineInfo";
	private static final String MUTE_URI = "api/v1/callctrl/mute";
	private static final String SESSION_STATS_URI = "api/v1/mgmt/media/sessionStats";

	private static final String DEVICE_INFO_URI = "api/v1/mgmt/device/info";
	private static final String NETWORK_STATS_URI = "api/v1/mgmt/network/stats";
	private static final String RUNNING_CONFIG_URI = "api/v1/mgmt/device/runningConfig";
	private static final String STATUS_URI = "api/v1/mgmt/pollForStatus";
	private static final String RESTART_URI = "api/v1/mgmt/safeRestart";
	private static final String REBOOT_URI = "api/v1/mgmt/safeReboot";
	private static final String TRANSFER_TYPE_URI = "api/v1/mgmt/transferType/get";

	// API parameters
	private static final String FIRMWARE_RELEASE = "FirmwareRelease";
	private static final String CATEGORY = "Category";
	private static final String CALL_HANDLE = "CallHandle";
	private static final String CALL_STATE = "CallState";
	private static final String DATA = "data";
	private static final String DEST = "Dest";
	private static final String JITTER = "Jitter";
	private static final String LINE = "Line";
	private static final String PACKETS_EXPECTED = "PacketsExpected";
	private static final String PACKETS_LOST = "PacketsLost";
	private static final String PACKETS_RECEIVED = "PacketsReceived";
	private static final String PACKETS_SENT = "PacketsSent";
	private static final String PHONE_MUTE_STATE = "PhoneMuteState";
	private static final String PROTOCOL_STRING = "Protocol";
	private static final String PROXY_ADDRESS = "ProxyAddress";
	private static final String REGISTRATION_STATUS = "RegistrationStatus";
	private static final String REMOTE_PARTY_NUMBER = "RemotePartyNumber";
	private static final String REF = "Ref";
	private static final String SIP_ADDRESS = "SIPAddress";
	private static final String STATE = "state";
	private static final String STREAMS = "Streams";
	private static final String TYPE = "Type";
	private static final String TX_CODEC = "TxCodec";
	private static final String VIDEO_RX_ACT_BITRATE_KBPS = "VideoRxActBitrateKbps";
	private static final String VIDEO_TX_ACT_BITRATE_KBPS = "VideoTxActBitrateKbps";
	private static final String VIDEO_RX_FRAMERATE = "VideoRxFramerate";
	private static final String VIDEO_RX_FRAME_HEIGHT = "VideoRxFrameHeight";
	private static final String VIDEO_RX_FRAME_WIDTH = "VideoRxFrameWidth";
	private static final String VIDEO_TX_FRAMERATE = "VideoTxFramerate";
	private static final String VIDEO_TX_FRAME_HEIGHT = "VideoTxFrameHeight";
	private static final String VIDEO_TX_FRAME_WIDTH = "VideoTxFrameWidth";

	// API values
	private static final String AUTO = "Auto";
	private static final String H323 = "H323";
	private static final String SIP = "SIP";
	private static final String TEL = "TEL";
	private static final String CONNECTED = "Connected";
	private static final String REGISTERED = "Registered";
	private static final String UNREGISTERED = "Unregistered";
	private static final String VOICE = "Voice";
	private static final String VIDEO = "Video";

	// Config property names
	private static final String VIDEO_CALLRATE = "video.callRate";

	// API response statuses
	private static final String STATUS_2000_SUCCESS = "2000";
	private static final String STATUS_4007_NOT_IN_CALL = "4007";

	// max number of attempts to retrieve call id after dialing device
	private static final int MAX_STATUS_POLL_ATTEMPT = 5;

	/**
	 * Adapter metadata, collected from the version.properties
	 */
	private Properties adapterProperties;

	private AggregatedDeviceProcessor devicePropertyProcessor;


	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		String property = controllableProperty.getProperty();

		switch (property) {
			case "RestartDevice":
				restartDevice();
				break;
			case "RebootDevice":
				rebootDevice();
				break;
			default:
				break;
		}
	}

	@Override
	public void controlProperties(List<ControllableProperty> list) throws Exception {
		if (CollectionUtils.isEmpty(list)) {
			throw new IllegalArgumentException("Controllable properties cannot be null or empty");
		}
		for (ControllableProperty controllableProperty : list) {
			controlProperty(controllableProperty);
		}
	}

	/**
	 * Strips out prefix from the codec string.
	 *
	 * @param protocol codec string to normalize
	 * @return normalized codec string
	 */
	private static String cleanProtocol(String protocol) {
		// e.g. "3:G.722.1" or "24:H.264"
		int i = protocol.indexOf(':');
		if (i > 0) {
			protocol = protocol.substring(i + 1);
		}
		return protocol;
	}

	// from Polycom REST API doc: "Parallel processing is not allowed. If one API is being processed and another API is received by the phone, the second
	// request will receive an HTTP 403 error or will be queued for later processing."
	/** Lock to prevent simultaneous calls to REST APIs (parallel processing of API calls is not supported according to Polycom documentation). */
	private final Lock apiLock = new ReentrantLock();

	/**
	 * PolycomTrio constructor
	 */
	public PolycomTrio() throws IOException {
		super();

		Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("mapping/model-mapping.yml", getClass());
		devicePropertyProcessor = new AggregatedDeviceProcessor(mapping);
		adapterProperties = new Properties();
		adapterProperties.load(getClass().getResourceAsStream("/version.properties"));

		// typically devices do not have trusted certificates, instruct to trust all
		setTrustAllCertificates(true);
	}

	/**
	 * {@inheritDoc} <br>
	 * Note that corresponding {@code PolycomnTrio} API does not return call id when dialing. This class will make best effort to retrieve call id after the
	 * call is placed, but, if if such attempts fail after max allowed wait time (5 seconds by default), {@code null} call id will be returned.
	 */
	@Override
	public String dial(DialDevice device) throws Exception {
		/* 
		Description:
		This API enables a user to initiate a call to a given number. Moreover, this API initiates the call and returns a response as an acknowledgment of request received.  
		
		Method: POST 
		Path: /api/v1/callctrl/dial 
		Input JSON:
		{
		  "data": {
		    "Dest": "<NUMBER/SIP_URI>", // Mandatory 
		    "Line": "<LINE_NUMBER>", // Mandatory, default is Line 1
		    "Type": "<SIP/TEL/H323>" // Optional, default is TEL 
		  }
		}
		
		Success Response:
		{
		  "Status": 2000
		}
		
		Failure Response:
		{
			"Status": <4xxx/5xxx>
		}
		
		Applicable return codes 2000, 4000, 4002, 5000, 4002 
		*/

		String dialString = device.getDialString().trim();

		Map<String, String> data = new HashMap<>(3, 1);
		data.put(DEST, dialString);
		data.put(LINE, "1"); // Always line 1? we are not handling multiline calls

		// check for Protocol
		Protocol protocol = device.getProtocol();
		if (protocol != null) {
			String protocolString;
			if (protocol == Protocol.ISDN) {
				protocolString = TEL;
			} else {
				// since SIP and H323 are spelled the same as what device expects doing toString
				protocolString = protocol.toString();
			}
			data.put(TYPE, protocolString); // possible protocols for Trio: SIP/TEL/H323
		}
		// if there is no protocol, let device to decide (it has Auto protocol option)

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doPost(DIAL_URI, new Message<>(data), Message.class);
		} finally {
			apiLock.unlock();
		}
		checkResponseStatus(response.getStatus(), DIAL_URI);

		// Unfortunately, response does not contain the call id and we need to send another command to retrieve it
		// Need to wait for device to connect before getting the call id
		for (int i = 0; i < MAX_STATUS_POLL_ATTEMPT; i++) {
			Thread.sleep(1000);
			Map<String, ?> callStatusData = retrieveRawCallStatusData();
			if (callStatusData != null) {
				String callHandle = (String) callStatusData.get(CALL_HANDLE);
				if (!StringUtils.isNullOrEmpty(callHandle)) {
					// determine if device is in call with expected remote address
					String remoteAddress = ((String) callStatusData.get(REMOTE_PARTY_NUMBER));
					if (!StringUtils.isNullOrEmpty(remoteAddress)) {
						remoteAddress = remoteAddress.trim();
						// remote party number might include protocol: as a prefix
						int ind = remoteAddress.indexOf(':');
						if (ind > 0) {
							remoteAddress = remoteAddress.substring(ind + 1);
						}

						if (remoteAddress.equalsIgnoreCase(dialString)) {
							return callHandle;
						}
					}
				}
			}
		}

		// cannot retrieve call id
		return null;
	}

	/**
	 * {@inheritDoc} <br>
	 * <br>
	 * Note that {@code PolycomTrio} APIs only support retrieving media channel statistics starting with version {@code 5.8}, therefore, result of this call to
	 * older versions of {@code PolycomTrio} will not contain {@link AudioChannelStats} and {@link VideoChannelStats}.
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		Map<String, String> extendedStatisticsMap = new HashMap<>();
		List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();
		extendedStatistics.setControllableProperties(advancedControllableProperties);
		extendedStatistics.setStatistics(extendedStatisticsMap);

		populateStatistics(extendedStatisticsMap);
		populateControllableProperties(extendedStatisticsMap, advancedControllableProperties);

		// get registration status (from line info)
		// all API calls must be synchronized (see comments to apiLock)
		ListMessage listResponse;
		apiLock.lock();
		try {
			listResponse = doGet(LINE_INFO_URI, ListMessage.class);
		} finally {
			apiLock.unlock();
		}
		checkResponseStatus(listResponse.getStatus(), LINE_INFO_URI);

		return Arrays.asList(parseEndpointStats(listResponse.getData()), extendedStatistics);
	}

	/**
	 * Add controllable properties, that are not covered by the YML mapping
	 * @param statistics to add statistics properties to
	 */
	private void populateStatistics(Map<String, String> statistics) throws Exception {
		devicePropertyProcessor.applyProperties(statistics, retrieveDeviceInfo(), "DeviceInfo");
		devicePropertyProcessor.applyProperties(statistics, retrieveNetworkStats(), "NetworkInfo");
		devicePropertyProcessor.applyProperties(statistics, retrieveRunningConfig(), "RunningConfig");
		devicePropertyProcessor.applyProperties(statistics, retrieveStatus(), "DeviceStatus");
		devicePropertyProcessor.applyProperties(statistics, retrieveTransferType(), "TransferType");

		statistics.put("DeviceInfo#Uptime", normalizeDeviceUptime(statistics.get("DeviceInfo#Uptime")));
		statistics.put("NetworkInfo#Uptime", normalizeDeviceUptime(statistics.get("NetworkInfo#Uptime")));
	}

	/**
	 * Add controllable properties, that are not covered by the YML mapping
	 * @param statistics to add controls statistics properties to
	 * @param controls to add advanced controllable properties to
	 */
	private void populateControllableProperties(Map<String, String> statistics, List<AdvancedControllableProperty> controls) {
		statistics.put("RestartDevice", "");
		controls.add(createButton("RestartDevice", "Restart", "Restarting...", 30000L));

		statistics.put("RebootDevice", "");
		controls.add(createButton("RebootDevice", "Reboot", "Rebooting...", 30000L));
	}

	/**
	 * Request basic device info
	 * @return {@link JsonNode} containing the response payload
	 *
	 * @throws Exception if any communication error occurs
	 */
	private JsonNode retrieveDeviceInfo () throws Exception {
		return doGet(DEVICE_INFO_URI, JsonNode.class);
	}

	/**
	 * Request network stats of the device
	 * @return {@link JsonNode} containing the response payload
	 *
	 * @throws Exception if any communication error occurs
	 */
	private JsonNode retrieveNetworkStats() throws Exception {
		return doGet(NETWORK_STATS_URI, JsonNode.class);
	}

	/**
	 * Request running config state of the device
	 * @return {@link JsonNode} containing the response payload
	 *
	 * @throws Exception if any communication error occurs
	 */
	private JsonNode retrieveRunningConfig() throws Exception {
		return doGet(RUNNING_CONFIG_URI, JsonNode.class);
	}

	/**
	 * Request transfer type status of the device
	 * @return {@link JsonNode} containing the response payload
	 *
	 * @throws Exception if any communication error occurs
	 */
	private JsonNode retrieveStatus() throws Exception {
		return doGet(STATUS_URI, JsonNode.class);
	}

	/**
	 * Request transfer type status of the device
	 * @return {@link JsonNode} containing the response payload
	 *
	 * @throws Exception if any communication error occurs
	 */
	private JsonNode retrieveTransferType() throws Exception {
		return doGet(TRANSFER_TYPE_URI, JsonNode.class);
	}

	/**
	 * Request to reboot the device
	 *
	 * @throws Exception if any communication error occurs
	 */
	private void restartDevice() throws Exception {
		doPost(RESTART_URI, null);
	}

	/**
	 * Request to reboot the device
	 *
	 * @throws Exception if any communication error occurs
	 */
	private void rebootDevice() throws Exception {
		doPost(REBOOT_URI, null);
	}

	/**
	 * Normalize uptime format from
	 * 0 day 0:34:33
	 * to
	 * 0 day(s) 0 hour(s) 34 minute(s) 33 second(s)
	 *
	 * @param rawUptime in a format of '0 day 0:34:33'
	 * @return normalized uptime in a format 0 day(s) 0 hour(s) 34 minute(s) 33 second(s)
	 */
	private String normalizeDeviceUptime (String rawUptime) {
		StringBuilder uptime = new StringBuilder();
		Pattern pattern = Pattern.compile("(\\d+)\\sday\\s(\\d+):(\\d+):(\\d+)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(rawUptime);
		if (matcher.find()) {
			uptime.append(matcher.group(1)).append(" day(s) ").append(matcher.group(2)).append(" hour(s) ")
					.append(matcher.group(3)).append(" minute(s) ").append(matcher.group(4)).append(" second(s)");
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("No valid date format found in a raw uptime string: " + rawUptime);
			}
			return rawUptime;
		}
		return uptime.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void hangup(String callId) throws Exception {
		/*
		 Description:
		 This API ends an active call. 
		 
		 Method: POST
		 Path: /api/v1/callctrl/endCall 
		 Input JSON:
		 {
		   "data": {
		     "Ref": "<CALL_REFERENCE>" // Mandatory
		   }
		 }
		 
		Success Response:
		{
		  "Status": 2000
		}
		 
		Failure Response:
		{
			"Status": <4xxx/5xxx>
		}
		
		Applicable return codes 2000, 4000, 4003, 4007, 5000
		*/

		if (StringUtils.isNullOrEmpty(callId)) {
			// retrieve call id from the device
			Map<String, ?> callStatusData = retrieveRawCallStatusData();
			if (callStatusData != null) {
				callId = (String) callStatusData.get(CALL_HANDLE);
			}

			if (StringUtils.isNullOrEmpty(callId)) {
				// no call reported from the device - nothing to disconnect
				return;
			}
		}

		Map<String, String> data = new HashMap<>(1, 1);
		data.put(REF, callId);

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doPost(END_CALL_URI, new Message<>(data), Message.class);
		} finally {
			apiLock.unlock();
		}

		// Ignoring 4007 status that indicates that device is already in a call
		checkResponseStatus(response.getStatus(), END_CALL_URI, STATUS_4007_NOT_IN_CALL);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mute() throws Exception {
		/*
		 Description:
		 This API enables a user to mute the phone
		 
		 Method: POST
		 Path: /api/v1/callctrl/mute 
		 Input JSON:
		 {
		   "data": {
		     "state": "1" // Mandatory
		   }
		 }
		
		 Success Response:
		 {
		   "Status": 2000
		 }
		
		 Failure Response:
		 {
		 	"Status": <4xxx/5xxx>
		 }
		 
		 Applicable return codes 2000, 4000, 4003, 4007, 5000
		 */

		Map<String, String> data = new HashMap<>(1, 1);
		data.put(STATE, "1");

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doPost(MUTE_URI, new Message<>(data), Message.class);
		} finally {
			apiLock.unlock();
		}

		checkResponseStatus(response.getStatus(), MUTE_URI);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CallStatus retrieveCallStatus(String callId) throws Exception {
		/*
		Method: GET
		Path: /api/v1/webCallControl/callStatus
		Success Response:
		{
		  "data": {
		    "CallHandle": "0xb53e57c0",
		    "Type": "Incoming",
		    "Protocol": "Auto",
		    "CallState": "Connected",
		    "LineId": "1",
		    "RemotePartyName": "nh-sx80@nh.vnoc1.com",
		    "RemotePartyNumber": "nh-sx80",
		    "DurationInSeconds": "308"
		  },
		  "Status": "2000"
		}
				
		Failure Response:
		{
			“Status": <4xxx/5xxx>
		}
		
		Applicable return codes: 2000, 4007, 5000
		*/

		CallStatus callStatus = new CallStatus();
		Map<String, ?> callStatusData = retrieveRawCallStatusData();
		if (callStatusData != null) {
			String callState = (String) callStatusData.get(CALL_STATE);
			if (callState != null && callState.equalsIgnoreCase(CONNECTED)) {
				String callIdFromCallStats = (String) callStatusData.get(CALL_HANDLE);
				if (callId != null) {
					// asked for specific call id, need to compare it with the one from call status
					if (!StringUtils.isNullOrEmpty(callIdFromCallStats) && callIdFromCallStats.equalsIgnoreCase(callId)) {
						callStatus.setCallId(callIdFromCallStats);
						callStatus.setCallStatusState(CallStatusState.Connected);
					} else {
						// not our call
						callStatus.setCallStatusState(CallStatusState.Disconnected);
					}
				} else {
					// specific call id is not asked, return status of currently connected call
					callStatus.setCallId(callIdFromCallStats);
					callStatus.setCallStatusState(CallStatusState.Connected);
				}
			} else {
				callStatus.setCallStatusState(CallStatusState.Disconnected);
			}
		} else {
			callStatus.setCallStatusState(CallStatusState.Disconnected);
		}

		return callStatus;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MuteStatus retrieveMuteStatus() throws Exception {
		/*
		 Description:
		 This API retrieves mute status by extracting value of PhoneMuteState from response
		  
		 Method: GET
		 Path: /api/v1/mgmt/media/communicationInfo
		 Success Response:
		 {
		   "data": {
		     "CommunicationType": [
		       "RxTx",
		       "RxTx",
		       "RxTx"
		     ],
		     "FarEndMuteState": [],
		     "PhoneMuteState": "False"
		   },
		   "Status": "2000"
		 }
		 		
		 Failure Response:
		 {
		 	“Status": <4xxx/5xxx>
		 }
		
		 Applicable return codes: 2000, 4007, 5000
		*/

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doGet(COMMUNICATION_INFO_URI, Message.class);
		} finally {
			apiLock.unlock();
		}

		checkResponseStatus(response.getStatus(), COMMUNICATION_INFO_URI);

		Map<String, ?> data = response.getData();
		String phoneMuteState = (String) data.get(PHONE_MUTE_STATE);
		if (!StringUtils.isNullOrEmpty(phoneMuteState, true)) {
			if (phoneMuteState.equalsIgnoreCase("True")) {
				return MuteStatus.Muted;
			} else if (phoneMuteState.equalsIgnoreCase("False")) {
				return MuteStatus.Unmuted;
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}<br>
	 * for PolycomTrio implementation we make a call to retreive the firmware version on the device.
	 * 
	 * @since 4.7
	 */
	@Override
	public Version retrieveSoftwareVersion() throws Exception {
		/*
		Method: GET
		Path: api/v1/mgmt/device/info
		Success Response:
		{
			"data": {
				"ModelNumber": "Trio 8800",
				"AttachedHardware": {},
				"FirmwareRelease": "5.7.1.4145",
				"IPV6Address": "::",
				"DeviceVendor": "Polycom",
				"DeviceType": "hardwareEndpoint",
				"UpTimeSinceLastReboot": "0 Day 22:02:09",
				"IPV4Address": "172.31.254.120",
				"MACAddress": "0004f2fe3ab0"
			},
			"Status": "2000"
		}
				
		Failure Response:
		{
			“Status": <4xxx/5xxx>
		}
		
		Applicable return codes: 2000, 4007, 5000
		*/

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doGet(VERSION_INFO_URI, Message.class);
		} finally {
			apiLock.unlock();
		}

		String status = response.getStatus();
		checkResponseStatus(status, VERSION_INFO_URI, STATUS_4007_NOT_IN_CALL);

		return parseVersionData(response.getData());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendMessage(PopupMessage message) throws Exception {
		// Not supported
		throw new NotImplementedException("Polycom Trio does not support on-screen messaging");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void unmute() throws Exception {
		/*
		 Description:
		 This API enables a user to unmute the phone
		 
		 Method: POST
		 Path: /api/v1/callctrl/mute 
		 Input JSON:
		 {
		   "data": {
		     "state": "0" // Mandatory
		   }
		 }
		
		 Success Response:
		 {
		   "Status": 2000
		 }
		
		 Failure Response:
		 {
		 	"Status": <4xxx/5xxx>
		 }
		 
		 Applicable return codes 2000, 4000, 4003, 4007, 5000
		 */

		Map<String, String> data = new HashMap<>(1, 1);
		data.put(STATE, "0");

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doPost(MUTE_URI, new Message<>(data), Message.class);
		} finally {
			apiLock.unlock();
		}

		checkResponseStatus(response.getStatus(), MUTE_URI);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() throws Exception {
		// we send authentication header with each request, so, there is nothing here to do
	}

	/**
	 * Reports whether device supports retrieving in-call statistics. <br>
	 * PolycomTrio supports retrieving in-call statistics starting with firmware version 5.8. If such call is made to older versions, device will freeze and
	 * require reboot.
	 *
	 * @return {@code true} if device supports retrieving in-call statistics, {@code false} otherwise
	 * @throws Exception if any error occurs
	 * @since 4.7
	 */
	protected boolean canRetrieveInCallStats() throws Exception {
		Version version = retrieveSoftwareVersion();
		if (null != version) {
			Integer major = version.getMajor();
			if (null != major) {
				int value = major.intValue();
				if (value > 5) {
					return true;
				}

				if (value == 5) {
					Integer minor = version.getMinor();
					if (null != minor && minor.intValue() >= 8) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Retrieves requested video bit rate from video stream. <br>
	 * There is a bug in Polycom Trio 5.8 API which reports crazy value for requested video rate. To fix it, return {@code null} for {@code PolycomTrio} and
	 * allow to override it for {@code PolycomVVX} with actual value from video stream.
	 * 
	 * @param stream video stream data
	 * @return requested video bit rate for {@code PolycomVVX}, or {@code null} for {@code PolycomTrio}
	 */
	protected Integer retrieveRequestedVideoCallRate(Map<String, ?> stream) {
		return null;
	}

	/**
	 * Determines video call rate from device configuration. <br>
	 * This call is needed to mitigate a bug in Polycom Trio 5.8 API which reports crazy value for requested video rate.
	 *
	 * @return video call rate in Kbps, of {@code null} if it cannot be determined
	 * @throws Exception if any error occurs
	 */
	Integer determineVideoCallRateFromConfig() throws Exception {
		ConfigPropertyWrapper propertyWrapper = retrieveConfigData(singletonList(VIDEO_CALLRATE)).get(VIDEO_CALLRATE);
		return null != propertyWrapper ? StringUtils.convertToInteger(propertyWrapper.getValue()) : null;
	}

	/**
	 * Parses audio channel statistics for give stream data.
	 *
	 * @param stream stream data to get statistics for
	 * @return parsed statistics
	 * @throws Exception if any error occurs
	 */
	AudioChannelStats parseAudioChannelStats(Map<String, ?> stream) {
		/*
			{
				"Ref": "0xb4e5dfa0",
				"RxPayloadSize": "80",
				"Jitter": "0",
				"Category": "0:Voice",
				"PacketsSent": "243",
				"PacketsExpected": "245",
				"TxPayloadSize": "20",
				"TxMOSCQ": "127.0",
				"OctetsSent": "19440",
				"MaxJitter": "0",
				"PacketsReceived": "244",
				"RxCodec": "3:G.722.1",
				"OctetsReceived": "19520",
				"PacketsLost": "1",
				"Latency": "0",
				"TxCodec": "3:G.722.1",
				"RxMOSCQ": "127.0",
				"RxMOSLQ": "127.0",
				"TxMOSLQ": "127.0"
			}
		 */
		AudioChannelStats audioChannelStats = new AudioChannelStats();

		// parse audio stream statistics
		String codec = (String) stream.get(TX_CODEC);
		if (!StringUtils.isNullOrEmpty(codec)) {
			// the codec is the same in both directions
			audioChannelStats.setCodec(cleanProtocol(codec)); // e.g. "3:G.722.1"
		}

		String jitter = (String) stream.get(JITTER);
		if (!StringUtils.isNullOrEmpty(jitter)) {
			audioChannelStats.setJitterRx(StringUtils.convertToFloat(jitter));
		}

		String packetsLost = (String) stream.get(PACKETS_LOST);
		if (!StringUtils.isNullOrEmpty(packetsLost)) {
			audioChannelStats.setPacketLossRx(StringUtils.convertToInteger(packetsLost));
		}

		return audioChannelStats;
	}

	/**
	 * After we make API call to get call status from device we parse the results here.
	 * 
	 * @param data map of strings (keys) to object values and is the response data to parse
	 * @return call status gathered or {@code null} if not in a call
	 * @since 4.7
	 */
	CallStats parseCallStatsData(Map<String, ?> data) {
		CallStats callStats = new CallStats();
		String callState = (String) data.get(CALL_STATE);

		if (!StringUtils.isNullOrEmpty(callState, true) && (callState.equalsIgnoreCase(CONNECTED))) {
			// ONLY if CONNECTED (not connecting, dialing... proceeding)
			callStats.setCallId((String) data.get(CALL_HANDLE));
			String remoteAddress = (String) data.get(REMOTE_PARTY_NUMBER);
			callStats.setRemoteAddress(remoteAddress);
			String protocol = (String) data.get(PROTOCOL_STRING);
			if (null == protocol || protocol.equals(AUTO)) {
				// try to get protocol from remote address
				if (remoteAddress != null) {
					int ind = remoteAddress.indexOf(':');
					if (ind > 0) {
						String prefix = remoteAddress.substring(0, ind);
						if (prefix.equalsIgnoreCase(SIP) || prefix.equalsIgnoreCase(H323) || prefix.equalsIgnoreCase(TEL)) {
							protocol = prefix;
						}

					}
				}
			}
			callStats.setProtocol(protocol);
			return callStats;
		}

		return null;
	}

	/**
	 * Response from device for statistics is returned as a map (data param). We parse it here
	 *
	 * @return all required statistics wraped in EndpointStatistics object
	 * @throws Exception if any errors
	 * @since 4.7
	 */
	EndpointStatistics parseEndpointStats(List<Map<String, ?>> stream) throws Exception {
		EndpointStatistics endpointStatistics = new EndpointStatistics();
		if (!stream.isEmpty()) {
			RegistrationStatus registrationStatus = new RegistrationStatus();
			boolean sipRegistrarGathered = false;
			boolean sipRegisteredGathered = false;
			boolean sipDetailsGathered = false;
			for (Map<String, ?> map : stream) {

				String sipRegistrar = (String) map.get(PROXY_ADDRESS);
				if (!StringUtils.isNullOrEmpty(sipRegistrar)) {
					registrationStatus.setSipRegistrar(sipRegistrar);
					sipRegistrarGathered = true;
				}

				String registered = (String) map.get(REGISTRATION_STATUS);
				if (!StringUtils.isNullOrEmpty(sipRegistrar)) {
					sipRegisteredGathered = true;
					if (registered.equalsIgnoreCase(REGISTERED)) {
						registrationStatus.setSipRegistered(Boolean.TRUE);
					} else if (registered.equalsIgnoreCase(UNREGISTERED)) {
						registrationStatus.setSipRegistered(Boolean.FALSE);
					}
				}

				String sipAddress = (String) map.get(SIP_ADDRESS);
				if (!StringUtils.isNullOrEmpty(sipAddress)) {
					sipDetailsGathered = true;
					registrationStatus.setSipDetails(SIP_ADDRESS + ": " + sipAddress);
				}

				if (sipRegistrarGathered && sipRegisteredGathered && sipDetailsGathered) {
					break;
				}
			}
			endpointStatistics.setRegistrationStatus(registrationStatus);
		}

		// get call statistics
		CallStats callStats = retrieveCallStats();
		if (callStats != null) {
			endpointStatistics.setCallStats(callStats);
			endpointStatistics.setInCall(true);
			// note that older versions of Trio (before 5.8) will freeze if this call is made, therefore, for Trio, it should be made conditionally on version
			if (canRetrieveInCallStats()) {
				populateInCallStats(endpointStatistics);
			}
		} else {
			endpointStatistics.setInCall(false);
		}
		return endpointStatistics;
	}

	/**
	 * Takes the response from the device for call stats and puts them into the endpointStatistics param
	 * 
	 * @param endpointStatistics stats to be populated from the in call data response
	 * @param mediaSessions in call data response
	 * @throws Exception if any errors
	 */
	void parseInCallStats(EndpointStatistics endpointStatistics, List<Map<String, ?>> mediaSessions) throws Exception {
		if (!mediaSessions.isEmpty()) {
			final CallStats callStats = endpointStatistics.getCallStats();
			final String callId = callStats.getCallId();
			AudioChannelStats audioChannelStats = null;
			VideoChannelStats videoChannelStats = null;
			Integer requestedCallRate = null;
			for (Map<String, ?> mediaSession : mediaSessions) {
				// in case if there are multiple media sessions returned, we need to match media session reference to call id
				// example: one call is connected, another one is on hold - two media sessions returned
				if (callId.equals(mediaSession.get(REF))) {
					Integer expectedAudioPackets = null;
					Integer expectedVideoPackets = null;
					@SuppressWarnings("unchecked")
					List<Map<String, ?>> streams = (List<Map<String, ?>>) mediaSession.get(STREAMS);
					if (streams != null && !streams.isEmpty()) {
						for (Map<String, ?> stream : streams) {
							String category = (String) stream.get(CATEGORY);
							// it has some number in front, not clear what it is, index or identifier
							// "Category": "0:Voice",
							// to be safe, compare only suffix
							if (category.endsWith(VOICE)) {
								audioChannelStats = parseAudioChannelStats(stream);

								// we will need this for calculating percent packet loss below
								String packetsExpected = (String) stream.get(PACKETS_EXPECTED);
								if (!StringUtils.isNullOrEmpty(packetsExpected)) {
									expectedAudioPackets = StringUtils.convertToInteger(packetsExpected);
								}

								// add mute status
								MuteStatus muteStatus = retrieveMuteStatus();
								if (null != muteStatus) {
									audioChannelStats.setMuteTx(muteStatus == MuteStatus.Muted ? Boolean.TRUE : Boolean.FALSE);
								}

								if (videoChannelStats != null) {
									// both audio and video statistics are collected, ignore rest of the streams (if any)
									break;
								}
							} else if (category.endsWith(VIDEO)) {
								VideoChannelStatsWrapper videoChannelStatsWrapper = parseVideoChannelStats(stream);
								videoChannelStats = videoChannelStatsWrapper.videoChannelStats;

								if (null != videoChannelStats) {
									requestedCallRate = videoChannelStatsWrapper.requestedCallRate;

									// there is a bug in Polycom Trio 5.8 API which reports crazy value for requested video rate
									// to fix it, override the value with one retrieved from device config
									// TODO if future versions of Polycom Trio API returns correct values, this call can be removed
									if (null == requestedCallRate) {
										requestedCallRate = determineVideoCallRateFromConfig();
									}

									// we will need this for calculating percent packet loss below
									String packetsExpected = (String) stream.get(PACKETS_EXPECTED);
									if (!StringUtils.isNullOrEmpty(packetsExpected)) {
										expectedVideoPackets = StringUtils.convertToInteger(packetsExpected);
									}
								}

								if (audioChannelStats != null) {
									// both audio and video statistics are collected, ignore rest of the streams (if any)
									break;
								}
							}
						}

						// calculate call rates and packet loss percentage and set them into the callStats

						// Polycom VVX (as some other endpoints) only report bit rates for video
						if (null != videoChannelStats) {
							callStats.setCallRateRx(videoChannelStats.getBitRateRx());
							callStats.setCallRateTx(videoChannelStats.getBitRateTx());
							callStats.setRequestedCallRate(requestedCallRate);
						}

						callStats.setPercentPacketLossRx(
								StatisticsUtils.calculatePacketLossPercentage(null != audioChannelStats ? audioChannelStats.getPacketLossRx() : null,
										null != videoChannelStats ? videoChannelStats.getPacketLossRx() : null, expectedAudioPackets, expectedVideoPackets));

						endpointStatistics.setCallStats(callStats);
						endpointStatistics.setAudioChannelStats(audioChannelStats);
						endpointStatistics.setVideoChannelStats(videoChannelStats);
					}

					// we matched this media session to call id, no need to continue
					break;
				}
			}
		} else {
			// not in call anymore
			endpointStatistics.setInCall(false);
			endpointStatistics.setCallStats(null);
		}
	}

	/**
	 * Response from device for version is returned as a map (data param). We query it for the specific firmware relase key and set value of that entry to the
	 * version return object
	 * 
	 * @param data usually the response from the Trio including the firmware version
	 * @return version object with the version field populated, or null if the version could not be gathered
	 * @since 4.7
	 */
	Version parseVersionData(Map<String, ?> data) {
		if (null != data) {
			Object firmwareVersion = data.get(FIRMWARE_RELEASE);
			if (null != firmwareVersion) {
				String versionValue = firmwareVersion.toString();
				if (!StringUtils.isNullOrEmpty(versionValue)) {
					return new Version(versionValue);
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate Text controllable property
	 *
	 * @param name         name of the property
	 * @param label        default button label
	 * @param labelPressed button label when is pressed
	 * @param gracePeriod  period to pause monitoring statistics for
	 * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.Button as type
	 */
	private AdvancedControllableProperty createButton(String name, String label, String labelPressed, long gracePeriod) {
		AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
		button.setLabel(label);
		button.setLabelPressed(labelPressed);
		button.setGracePeriod(gracePeriod);

		return new AdvancedControllableProperty(name, new Date(), button, "");
	}

	/**
	 * Parses video channel statistics for give stream data.
	 *
	 * @param stream stream data to get statistics for
	 * @return parsed statistics
	 * @throws Exception if any error occurs
	 */
	VideoChannelStatsWrapper parseVideoChannelStats(Map<String, ?> stream) throws Exception {
		/*
			{
				"Ref": "0xb4e5e9d8",
				"RxPayloadSize": "v",
				"VideoRxFrameWidth": "320",
				"Jitter": "0",
				"Category": "1:Video",
				"PacketsSent": "0",
				"PacketsExpected": "136",
				"TxPayloadSize": "v",
				"VideoTxFramerate": "0",
				"OctetsSent": "0",
				"MaxJitter": "0",
				"VideoRxFramerate": "16",
				"PacketsReceived": "136",
				"VideoRxFastUpdateReqCnt": "0",
				"VideoTxActBitrateKbps": "0",
				"RxCodec": "24:H.264",
				"OctetsReceived": "40910",
				"PacketsLost": "0",
				"Latency": "0",
				"TxCodec": "24:H.264",
				"VideoTxFastUpdateReqCnt": "1",
				"VideoTxFrameWidth": "1280",
				"VideoTxFrameHeight": "720",
				"VideoTxConfigBitrateKbps": "448",
				"VideoRxFrameHeight": "180",
				"VideoRxActBitrateKbps": "319"
			}
		 */

		VideoChannelStats videoChannelStats = null;
		Integer requestedCallRate = null;

		// both Trio (and VVX) return weird data for audio only calls. They do report empty video stream which has 0 for most of values
		// also, VVX refers there to audio (and not video) codec!
		// therefore, do additional checks and only create video channel statistics if there are any packets reported
		boolean hasVideo = false;
		String sValue = (String) stream.get(PACKETS_EXPECTED);
		if (!StringUtils.isNullOrEmpty(sValue)) {
			Integer iValue = StringUtils.convertToInteger(sValue);
			if (null != iValue && iValue.intValue() > 0) {
				hasVideo = true;
			} else {
				sValue = (String) stream.get(PACKETS_SENT);
				if (!StringUtils.isNullOrEmpty(sValue)) {
					iValue = StringUtils.convertToInteger(sValue);
					if (null != iValue && iValue.intValue() > 0) {
						hasVideo = true;
					} else {
						sValue = (String) stream.get(PACKETS_RECEIVED);
						if (!StringUtils.isNullOrEmpty(sValue)) {
							iValue = StringUtils.convertToInteger(sValue);
							if (null != iValue && iValue.intValue() > 0) {
								hasVideo = true;
							}
						}
					}
				}
			}
		}

		// last check for config bit rate (if there, it is a video call)
		requestedCallRate = retrieveRequestedVideoCallRate(stream);
		if (null != requestedCallRate) {
			if (requestedCallRate.intValue() > 0) {
				if (!hasVideo) {
					hasVideo = true;
				}
			} else {
				// reset it back to null so it can be retrieved from the config
				requestedCallRate = null;
			}
		}

		if (hasVideo) {
			videoChannelStats = new VideoChannelStats();

			// parse video stream statistics
			String bitRateRx = (String) stream.get(VIDEO_RX_ACT_BITRATE_KBPS);
			if (!StringUtils.isNullOrEmpty(bitRateRx)) {
				Integer integerBitRateRx = StringUtils.convertToInteger(bitRateRx);
				videoChannelStats.setBitRateRx(integerBitRateRx);
			}

			String bitRateTx = (String) stream.get(VIDEO_TX_ACT_BITRATE_KBPS);
			if (!StringUtils.isNullOrEmpty(bitRateTx)) {
				Integer integerBitRateTx = StringUtils.convertToInteger(bitRateTx);
				videoChannelStats.setBitRateTx(integerBitRateTx);
			}

			String codec = (String) stream.get(TX_CODEC); // e.g. "24:H.264"
			if (!StringUtils.isNullOrEmpty(codec)) {
				// the codec is the same in both directions
				videoChannelStats.setCodec(cleanProtocol(codec));
			}

			String frameRateRx = (String) stream.get(VIDEO_RX_FRAMERATE);
			if (!StringUtils.isNullOrEmpty(frameRateRx)) {
				videoChannelStats.setFrameRateRx(StringUtils.convertToFloat(frameRateRx));
			}

			String frameRateTx = (String) stream.get(VIDEO_TX_FRAMERATE);
			if (!StringUtils.isNullOrEmpty(frameRateTx)) {
				videoChannelStats.setFrameRateTx(StringUtils.convertToFloat(frameRateTx));
			}

			String videoTxWidth = (String) stream.get(VIDEO_TX_FRAME_WIDTH);
			String videoTxHeight = (String) stream.get(VIDEO_TX_FRAME_HEIGHT);
			if (!StringUtils.isNullOrEmpty(videoTxWidth) && !StringUtils.isNullOrEmpty(videoTxHeight)) {
				videoChannelStats.setFrameSizeTx(videoTxWidth + "x" + videoTxHeight);
			}

			String videoRxWidth = (String) stream.get(VIDEO_RX_FRAME_WIDTH);
			String videoRxHeight = (String) stream.get(VIDEO_RX_FRAME_HEIGHT);
			if (!StringUtils.isNullOrEmpty(videoRxWidth) && !StringUtils.isNullOrEmpty(videoRxHeight)) {
				videoChannelStats.setFrameSizeRx(videoRxWidth + "x" + videoRxHeight);
			}

			String jitter = (String) stream.get(JITTER);
			if (!StringUtils.isNullOrEmpty(jitter)) {
				videoChannelStats.setJitterRx(StringUtils.convertToFloat(jitter));
			}

			String packetsLost = (String) stream.get(PACKETS_LOST);
			if (!StringUtils.isNullOrEmpty(packetsLost)) {
				videoChannelStats.setPacketLossRx(StringUtils.convertToInteger(packetsLost));
			}
		}

		return new VideoChannelStatsWrapper(videoChannelStats, requestedCallRate);
	}

	/**
	 * Verifies that given status is a success.
	 *
	 * @param status status to check
	 * @param request request which caused error
	 * @param ignoreStatuses optional list of statuses which are not success, but should be ignored
	 * @throws CommandFailureException if given status is not a success and cannot be ignored
	 */
	private void checkResponseStatus(String status, String request, String... ignoreStatuses) throws CommandFailureException {
		if (status.equals(STATUS_2000_SUCCESS)) {
			// success
			return;
		}

		if (ignoreStatuses != null) {
			for (String ignoreStatus : ignoreStatuses) {
				if (status.equals(ignoreStatus)) {
					// ignore, not error in given context
					return;
				}
			}
		}

		// if we got here, it is an error
		if (logger.isTraceEnabled()) {
			logger.trace("Response " + status + " was received which is not 2000 nor any of the expected ignore statuses were received for request: " + request
					+ " and device " + host);
		}
		throw new CommandFailureException(host, request, status);
	}

	/**
	 * Retrieves in call statistics from the device and populate corresponding properties in {@code endpointStatistics} object. <br>
	 * Note that older versions of Trio (before 5.8) will freeze if this call is made, therefore, for Trio, it should be made conditionally on version.
	 *
	 * @param endpointStatistics endpoint statistics to populate
	 * @throws Exception if any error occurs
	 */
	private void populateInCallStats(EndpointStatistics endpointStatistics) throws Exception {

		/*
		Method: GET
		Path: /api/v1/mgmt/media/sessionStats
		Success Response:
		{
		  "data": [
		{
		  "Streams": [
		    {
		      "Ref": "0xb4e5dfa0",
		      "RxPayloadSize": "80",
		      "Jitter": "0",
		      "Category": "0:Voice",
		      "PacketsSent": "243",
		      "PacketsExpected": "245",
		      "TxPayloadSize": "20",
		      "TxMOSCQ": "127.0",
		      "OctetsSent": "19440",
		      "MaxJitter": "0",
		      "PacketsReceived": "244",
		      "RxCodec": "3:G.722.1",
		      "OctetsReceived": "19520",
		      "PacketsLost": "1",
		      "Latency": "0",
		      "TxCodec": "3:G.722.1",
		      "RxMOSCQ": "127.0",
		      "RxMOSLQ": "127.0",
		      "TxMOSLQ": "127.0"
		    },
		    {
		      "Ref": "0xb4e5e9d8",
		      "RxPayloadSize": "v",
		      "VideoRxFrameWidth": "320",
		      "Jitter": "0",
		      "Category": "1:Video",
		      "PacketsSent": "0",
		      "PacketsExpected": "136",
		      "TxPayloadSize": "v",
		      "VideoTxFramerate": "0",
		      "OctetsSent": "0",
		      "MaxJitter": "0",
		      "VideoRxFramerate": "16",
		      "PacketsReceived": "136",
		      "VideoRxFastUpdateReqCnt": "0",
		      "VideoTxActBitrateKbps": "0",
		      "RxCodec": "24:H.264",
		      "OctetsReceived": "40910",
		      "PacketsLost": "0",
		      "Latency": "0",
		      "TxCodec": "24:H.264",
		      "VideoTxFastUpdateReqCnt": "1",
		      "VideoTxFrameWidth": "1280",
		      "VideoTxFrameHeight": "720",
		      "VideoTxConfigBitrateKbps": "448",
		      "VideoRxFrameHeight": "180",
		      "VideoRxActBitrateKbps": "319"
		    }
		  ],
		  "Ref": "0xb53e57c0",
		  "SRTPCall": "False",
		  "H235Call": "False"
		}
		  ],
		  "Status": "2000"
		}
				
		Failure Response:
		{
			"Status": 5000
		}
		
		Applicable return codes: 2000, 5000
		*/

		// all API calls must be synchronized (see comments to apiLock)
		ListMessage listResponse;
		apiLock.lock();
		try {
			listResponse = doGet(SESSION_STATS_URI, ListMessage.class);
		} finally {
			apiLock.unlock();
		}

		checkResponseStatus(listResponse.getStatus(), SESSION_STATS_URI);

		parseInCallStats(endpointStatistics, listResponse.getData());
	}

	/**
	 * This method will make "api/v1/webCallControl/callStatus" API call to determine if device is connected. If the device is connected, it will create and
	 * populate {@link CallStats} object and populate its {@code callId}, {@code remoteAddress}, and {@code protocol} properties.<br>
	 * <br>
	 * if device returns "4007 Call Does Not Exist" response, or a "CallState" other than "Connected" or "OnHold", this method will return {@code null}
	 * 
	 * @return instance of {@link CallStats} object with only the {@code callId} and {@code remoteAddress} properties, or {@code null} if device not in call
	 * @throws Exception if any error occurs
	 */
	private CallStats retrieveCallStats() throws Exception {
		/*
		Method: GET
		Path: /api/v1/webCallControl/callStatus
		Success Response:
		{
		  "data": {
		    "CallHandle": "0xb53e57c0",
		    "Type": "Incoming",
		    "Protocol": "Auto",
		    "CallState": "Connected",
		    "LineId": "1",
		    "RemotePartyName": "nh-sx80@nh.vnoc1.com",
		    "RemotePartyNumber": "nh-sx80",
		    "DurationInSeconds": "308"
		  },
		  "Status": "2000"
		}
				
		Failure Response:
		{
			“Status": <4xxx/5xxx>
		}
		
		Applicable return codes: 2000, 4007, 5000
		*/

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doGet(CALL_STATUS_URI, Message.class);
		} finally {
			apiLock.unlock();
		}

		String status = response.getStatus();
		checkResponseStatus(status, CALL_STATUS_URI, STATUS_4007_NOT_IN_CALL);

		if (status.equals(STATUS_4007_NOT_IN_CALL)) {
			return null;
		} else {
			return parseCallStatsData(response.getData());
		}
	}

	/**
	 * Retrieves one or more config properties from the device. <br>
	 * Note that for config properties exposed in Polycom Trio UI, property names are available in the "Field Help" section.
	 * 
	 * @param propertyNames list of property names to retrieve config data for
	 * @return map of config properties where key is config property name and values is config property value wrapper
	 * @throws Exception if any error occurs
	 * @since 4.7
	 */
	private Map<String, ConfigPropertyWrapper> retrieveConfigData(List<String> propertyNames) throws Exception {
		/*
		Method: POST
		Path: api/v1/mgmt/config/get
		Post data:
		{
			"data": [
				"video.callRate"
			]
		}
		
		Success Response:
		{
			"data": {
				"video.callRate": {
					"Value": "2048",
					"Source": "default"
				}
			},
			"Status": "2000"
		}
		
		Failure Response:
		{
			“Status": <4xxx/5xxx>
		}
		
		Applicable return codes: 2000, 4000, 4009, 5000
		(4009 -> Parameter count exceeded limit of 20 parameters)
		 */

		// prepare request data
		Map<String, List<String>> data = singletonMap(DATA, propertyNames);

		// all API calls must be synchronized (see comments to apiLock)
		Message<ConfigPropertyWrapper> response;
		apiLock.lock();
		try {
			response = doPost(GET_CONFIG_URI, data, new ParameterizedTypeReference<Message<ConfigPropertyWrapper>>() {
			});
		} finally {
			apiLock.unlock();
		}

		checkResponseStatus(response.getStatus(), GET_CONFIG_URI);
		return response.getData();
	}

	/**
	 * This method will make "api/v1/webCallControl/callStatus" API call to determine if device is connected. If the device is connected, it will create and
	 * populate {@link CallStats} object and populate its {@code callId}, {@code remoteAddress}, and {@code protocol} properties.<br>
	 * <br>
	 * if device returns "4007 Call Does Not Exist" response, or a "CallState" other than "Connected" or "OnHold", this method will return {@code null}
	 * 
	 * @return instance of {@link CallStats} object with only the {@code callId} and {@code remoteAddress} properties, or {@code null} if device not in call
	 * @throws Exception if any error occurs
	 */
	private Map<String, ?> retrieveRawCallStatusData() throws Exception {
		/*
		Method: GET
		Path: /api/v1/webCallControl/callStatus
		Success Response:
		{
		  "data": {
		    "CallHandle": "0xb53e57c0",
		    "Type": "Incoming",
		    "Protocol": "Auto",
		    "CallState": "Connected",
		    "LineId": "1",
		    "RemotePartyName": "nh-sx80@nh.vnoc1.com",
		    "RemotePartyNumber": "nh-sx80",
		    "DurationInSeconds": "308"
		  },
		  "Status": "2000"
		}
				
		Failure Response:
		{
			“Status": <4xxx/5xxx>
		}
		
		Applicable return codes: 2000, 4007, 5000
		*/

		// all API calls must be synchronized (see comments to apiLock)
		Message<?> response;
		apiLock.lock();
		try {
			response = doGet(CALL_STATUS_URI, Message.class);
		} finally {
			apiLock.unlock();
		}

		String status = response.getStatus();
		checkResponseStatus(status, CALL_STATUS_URI, STATUS_4007_NOT_IN_CALL);
		return response.getData();
	}
}

// On a device go to settings->applications->rest api->ENABLE
// For User set the password (use Polycom/1234)
/*
 * ------------------------------------------------------------
GET https://172.31.254.120:443/api/v1/webCallControl/callStatus

{
    "data": {
        "RemotePartyName": "nh-sx80@nh.vnoc1.com",
        "CallState": "Connected",
        "CallHandle": "0x49d9ed8",
        "LineId": "1",
        "Type": "Outgoing",
        "Protocol": "Sip",
        "RemotePartyNumber": "sip:NH-SX80@nh.vnoc1.com",
        "DurationInSeconds": "409"
    },
    "Status": "2000"
}

-------------------------------------------------------------------
GET https://172.31.254.120:443/api/v1/mgmt/media/communicationInfo
{
    "data": {
        "CommunicationType": [
            "RxTx",
            "RxTx",
            "RxTx"
        ],
        "FarEndMuteState": [],
        "PhoneMuteState": "False"
    },
    "Status": "2000"
}
--------------------------------------------------------------------
GET https://172.31.254.120:443/api/v1/mgmt/lineInfo
{
    "data": [
        {
            "UserID": "7771991022",
            "SIPAddress": "7771991022@nh.vnoc1.com",
            "RegistrationStatus": "unregistered",
            "LineNumber": "1",
            "LineType": "private",
            "Label": "7771991022"
        }
    ],
    "Status": "2000"
}
-----------------------------------------------------------------------


The list of some possible returned codes and their meanings:
Success 
 2000 API executed successfully. 
   
Failed
 4000 Invalid input parameters. 
 4001 Device busy. 
 4002 Line not registered. 
 4003 Operation not allowed. 
 4004 Operation Not Supported 
 4005 Line does not exist. 
 4006 URLs not configured. 
 4007 Call Does Not Exist 
 4008 Configuration Export Failed 
 4009 Input Size Limit Exceeded 
 4010 Default Password Not Allowed 
   
 5000 Failed to process request. 

*/
