/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/


package com.comino.mavcom.control.impl;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.mavlink.IMAVLinkMessage;
import org.mavlink.messages.IMAVLinkMessageID;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAV_CMD;
import org.mavlink.messages.MAV_COMPONENT;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.MAV_TYPE;
import org.mavlink.messages.SERIAL_CONTROL_DEV;
import org.mavlink.messages.SERIAL_CONTROL_FLAG;
import org.mavlink.messages.lquac.msg_command_long;
import org.mavlink.messages.lquac.msg_heartbeat;
import org.mavlink.messages.lquac.msg_serial_control;
import org.mavlink.messages.lquac.msg_statustext;

import com.comino.mavcom.comm.IMAVComm;
import com.comino.mavcom.comm.proxy.MAVUdpProxyNIO;
import com.comino.mavcom.comm.serial.MAVSerialComm;
import com.comino.mavcom.comm.udp.MAVUdpCommNIO;
import com.comino.mavcom.control.IMAVCmdAcknowledge;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.control.IMAVMSPController;
import com.comino.mavcom.log.IMAVMessageListener;
import com.comino.mavcom.log.MSPLogger;
import com.comino.mavcom.mavlink.IMAVLinkListener;
import com.comino.mavcom.model.DataModel;
import com.comino.mavcom.model.segment.LogMessage;
import com.comino.mavcom.model.segment.Status;
import com.comino.mavcom.status.StatusManager;
import com.comino.mavcom.status.listener.IMSPStatusChangedListener;
import com.comino.mavutils.legacy.ExecutorService;

public class MAVProxyController implements IMAVMSPController, Runnable {

	protected String peerAddress = null;


	protected static IMAVMSPController controller = null;

	protected IMAVComm comm = null;


	protected   DataModel model = null;
	protected   MAVUdpProxyNIO proxy = null;

	private static final int BAUDRATE_5   = 57600;
	private static final int BAUDRATE_9   = 921600;
	private static final int BAUDRATE_15  = 1500000;
	private static final int BAUDRATE_20  = 2000000;
	private static final int BAUDRATE_30  = 3000000;

	private static final msg_heartbeat beat_gcs = new msg_heartbeat(2,MAV_COMPONENT.MAV_COMP_ID_ONBOARD_COMPUTER);
	private static final msg_heartbeat beat_px4 = new msg_heartbeat(1,MAV_COMPONENT.MAV_COMP_ID_ONBOARD_COMPUTER);
	private static final msg_heartbeat beat_obs = new msg_heartbeat(1,MAV_COMPONENT.MAV_COMP_ID_OBSTACLE_AVOIDANCE);

	private StatusManager 				status_manager 	= null;
	private List<IMAVMessageListener> 	messageListener = null;

	private ScheduledFuture<?> future = null;
	private LogMessage last_log_message = null;

	private int mode;

	public static IMAVController getInstance() {
		return controller;
	}


	public MAVProxyController(int mode) {
		
		this.mode = mode;
		controller = this;
		model = new DataModel();
		status_manager = new StatusManager(model);
		messageListener = new ArrayList<IMAVMessageListener>();

		model.sys.setSensor(Status.MSP_MSP_AVAILABILITY, true);
		model.sys.setStatus(Status.MSP_SITL, mode == MAVController.MODE_NORMAL);
		model.sys.setStatus(Status.MSP_PROXY, true);
		
		beat_gcs.type = MAV_TYPE.MAV_TYPE_ONBOARD_CONTROLLER;
		beat_gcs.system_status = MAV_STATE.MAV_STATE_ACTIVE;
		
		beat_px4.type = MAV_TYPE.MAV_TYPE_ONBOARD_CONTROLLER;
		beat_px4.system_status = MAV_STATE.MAV_STATE_ACTIVE;
		
		beat_obs.type = MAV_TYPE.MAV_TYPE_ONBOARD_CONTROLLER;
		beat_obs.system_status = MAV_STATE.MAV_STATE_ACTIVE;

		status_manager.addListener(StatusManager.TYPE_PX4_STATUS, Status.MSP_CONNECTED, StatusManager.EDGE_RISING, (a) -> {
			model.sys.setStatus(Status.MSP_ACTIVE, true);
			System.out.println("Connection to device established...");
		});

		status_manager.addListener(StatusManager.TYPE_PX4_STATUS, Status.MSP_CONNECTED, StatusManager.EDGE_FALLING, (a) -> {
			model.sys.setStatus(Status.MSP_ACTIVE, false);
			System.out.println("Connection to device lost...");
		});


		status_manager.addListener(StatusManager.TYPE_PX4_STATUS, Status.MSP_GCL_CONNECTED, StatusManager.EDGE_RISING, (a) -> {
			System.out.println("Connection to GCS established...");
			proxy.enableProxy(true);
		});

		status_manager.addListener(StatusManager.TYPE_PX4_STATUS, Status.MSP_GCL_CONNECTED, StatusManager.EDGE_FALLING, (a) -> {
			System.out.println("Connection to GCS lost...");
			proxy.enableProxy(false);
		});


		switch(mode) {
		case MAVController.MODE_NORMAL:
			//		comm = MAVSerialComm.getInstance(model, BAUDRATE_15, false);
			comm = MAVSerialComm.getInstance(model, BAUDRATE_20, false);
			//		comm = MAVSerialComm.getInstance(model, BAUDRATE_9, false);
			comm.open();
			sendMAVLinkMessage(beat_px4);
			
			try { Thread.sleep(100); } catch (InterruptedException e) { }

			proxy = new MAVUdpProxyNIO(model,"172.168.178.2",14550,"172.168.178.1",14555,comm);
			peerAddress = "172.168.178.1";
			System.out.println("Proxy Controller loaded: "+peerAddress);
			model.sys.setStatus(Status.MSP_SITL,false);
			break;

		case MAVController.MODE_SITL:
			model.sys.setStatus(Status.MSP_SITL,true);
			comm = MAVUdpCommNIO.getInstance(model, "127.0.0.1",14580, 14540);
			proxy = new MAVUdpProxyNIO(model,"127.0.0.1",14650,"0.0.0.0",14656,comm);
			peerAddress = "127.0.0.1";
			System.out.println("Proxy Controller (SITL mode) loaded");
			break;
		case MAVController.MODE_USB:
			//			comm = MAVSerialComm.getInstance(model, BAUDRATE_15, false);
			comm = MAVSerialComm.getInstance(model, BAUDRATE_5, false);
			//		comm = MAVSerialComm.getInstance(model, BAUDRATE_9, false);
			comm.open();
			try { Thread.sleep(500); } catch (InterruptedException e) { }
			proxy = new MAVUdpProxyNIO(model,"127.0.0.1",14650,"0.0.0.0",14656,comm);
			peerAddress = "127.0.0.1";
			System.out.println("Proxy Controller (serial mode) loaded: "+peerAddress);
			model.sys.setStatus(Status.MSP_SITL,false);
			break;
		case MAVController.MODE_SERVER:

			model.sys.setStatus(Status.MSP_SITL,true);

			//			comm = MAVSerialComm.getInstance(model, BAUDRATE_5, false);
			//			comm.open();
			//			try { Thread.sleep(500); } catch (InterruptedException e) { }

			comm = MAVUdpCommNIO.getInstance(model, "172.168.178.2",14580, 14540);
			//			comm = MAVUdpCommNIO.getInstance(model, "172.168.178.2",14280, 14030);
			proxy = new MAVUdpProxyNIO(model,"172.168.178.2",14650,"172.168.178.22",14656,comm);
			peerAddress = "172.168.178.22";
			System.out.println("Proxy Controller loaded (Server): "+peerAddress);
			break;
		}

//		comm.addMAVLinkListener(proxy);
		// Direct byte based proxy
		comm.setProxyListener(proxy);


		// Register processing of PING sent by GCL
		proxy.registerListener(msg_heartbeat.class, (o) -> {
			model.sys.gcl_tms = model.sys.getSynchronizedPX4Time_us();
			model.sys.setStatus(Status.MSP_GCL_CONNECTED, true);
		});
		
		// FWD PX4 heartbeat messages to GCL when not connected
		comm.addMAVLinkListener((o) -> {
			if(!model.sys.isStatus(Status.MSP_GCL_CONNECTED) && o instanceof msg_heartbeat) {
				proxy.write((MAVLinkMessage)o);
			}
		});

	}

	@Override
	public boolean sendMAVLinkMessage(MAVLinkMessage msg) {

		try {
			if(msg.sysId==2) {
				if(proxy.isConnected() && model.sys.isStatus(Status.MSP_GCL_CONNECTED))
					proxy.write(msg);
			}
			else {
				if(comm.isConnected() && model.sys.isStatus(Status.MSP_ACTIVE)) {
					comm.write(msg);
				}
			}
			return true;
		} catch (Exception e1) {
			return false;
		}

	}

	@Override
	public boolean sendMAVLinkCmd(int command, float...params) {

		msg_command_long cmd = new msg_command_long(255,1);
		cmd.target_system = 1;
		cmd.target_component = 1;
		cmd.command = command;
		cmd.confirmation = 1;

		for(int i=0; i<params.length;i++) {
			switch(i) {
			case 0: cmd.param1 = params[0]; break;
			case 1: cmd.param2 = params[1]; break;
			case 2: cmd.param3 = params[2]; break;
			case 3: cmd.param4 = params[3]; break;
			case 4: cmd.param5 = params[4]; break;
			case 5: cmd.param6 = params[5]; break;
			case 6: cmd.param7 = params[6]; break;

			}
		}

		return sendMAVLinkMessage(cmd);
	}

	@Override
	public boolean sendMAVLinkCmd(int command, IMAVCmdAcknowledge ack, float...params) {
		comm.setCmdAcknowledgeListener(ack);
		return sendMAVLinkCmd(command, params);
	}

	@Override
	public boolean sendShellCommand(String s) {
		String command = s+"\n";
		msg_serial_control msg = new msg_serial_control(1,1);
		try {
			byte[] bytes = command.getBytes("US-ASCII");
			for(int i =0;i<bytes.length && i<70;i++)
				msg.data[i] = bytes[i];
			msg.count = bytes.length;
			msg.device = SERIAL_CONTROL_DEV.SERIAL_CONTROL_DEV_SHELL;
			msg.flags  = SERIAL_CONTROL_FLAG.SERIAL_CONTROL_FLAG_RESPOND;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		sendMAVLinkMessage(msg);
		System.out.println("ShellCommand executed: "+s);
		return true;
	}


	@Override
	public boolean sendMSPLinkCmd(int command, float...params) {
		MSPLogger.getInstance().writeLocalMsg("Command rejected: Proxy cannot send command to itself...");
		return false;
	}

	public void registerListener(Class<?> clazz, IMAVLinkListener listener) {
		proxy.registerListener(clazz, listener);
	}



	public boolean isConnected() {
		//		model.sys.setStatus(Status.MSP_ACTIVE, comm.isConnected());
		if(mode == MAVController.MODE_NORMAL)
			return proxy.isConnected() && comm.isConnected();
		return proxy.isConnected();
	}

	@Override
	public boolean connect() {
		try { Thread.sleep(200); } catch (InterruptedException e) { }
		comm.open(); proxy.open();
		if(comm.isConnected()) {
			sendMAVLinkCmd(MAV_CMD.MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES, 1);
		}
		future = ExecutorService.get().scheduleAtFixedRate(this, 1, 1000, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public boolean close() {
		proxy.close(); comm.close();
		return true;
	}


	@Override
	public DataModel getCurrentModel() {
		return comm.getModel();
	}


	@Override
	public Map<Class<?>,MAVLinkMessage> getMavLinkMessageMap() {
		return comm.getMavLinkMessageMap();
	}


	@Override
	public boolean isSimulation() {
		return mode != MAVController.MODE_NORMAL;
	}

	@Override
	public void addStatusChangeListener(IMSPStatusChangedListener listener) {
		status_manager.addListener(listener);

	}

	@Override
	public void addMAVLinkListener(IMAVLinkListener listener) {
		comm.addMAVLinkListener(listener);
	}

	@Override
	public void addMAVMessageListener(IMAVMessageListener listener) {
		messageListener.add(listener);
	}

	@Override
	public void writeLogMessage(LogMessage m) {
		
		if(!m.isNew(last_log_message))
			return;
		
		last_log_message = m;

		this.model.msg = m;
		this.model.msg.tms = model.sys.getSynchronizedPX4Time_us();

		msg_statustext msg = new msg_statustext();
		msg.setText(m.text);
		msg.componentId = 1;
		msg.severity =m.severity;
		proxy.write(msg);
		if (messageListener != null) {
			for (IMAVMessageListener msglistener : messageListener)
				msglistener.messageReceived(m);
		}
		if(isSimulation()) {
			ExecutorService.get().submit(() -> {
				System.out.println(m);
			});
		}
	}


	@Override
	public String getConnectedAddress() {
		return peerAddress;
	}


	@Override
	public int getErrorCount() {
		return comm.getErrorCount();
	}


	@Override
	public String enableFileLogging(boolean enable, String directory) {
		return null;
	}

	@Override
	public StatusManager getStatusManager() {
		return status_manager;
	}


	@Override
	public boolean start() {

		status_manager.start();

		if(isSimulation()) {
			System.out.println("Setup MAVLink streams for simulation mode");
			sendMAVLinkCmd(MAV_CMD.MAV_CMD_SET_MESSAGE_INTERVAL, IMAVLinkMessageID.MAVLINK_MSG_ID_HIGHRES_IMU,50000);
			sendMAVLinkCmd(MAV_CMD.MAV_CMD_SET_MESSAGE_INTERVAL, IMAVLinkMessageID.MAVLINK_MSG_ID_VISION_POSITION_ESTIMATE,50000);
			sendMAVLinkCmd(MAV_CMD.MAV_CMD_SET_MESSAGE_INTERVAL, IMAVLinkMessageID.MAVLINK_MSG_ID_ATTITUDE_TARGET,20000);
		}

		return true;
	}


	@Override
	public void run() {


		if(!proxy.isConnected())  {
			proxy.close(); proxy.open();
		}
		if(!comm.isConnected()) {
			model.sys.setStatus(Status.MSP_ACTIVE, false);
			comm.open();
		} else
			model.sys.setStatus(Status.MSP_ACTIVE, true);

		sendMAVLinkMessage(beat_px4);
		
		sendMAVLinkMessage(beat_obs);
		
		
	}

	@Override
	public long getTransferRate() {
		return proxy.getTransferRate();
	}

}
